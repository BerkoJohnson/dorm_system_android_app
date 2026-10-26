package com.example.data.remote

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class DjangoApiException(
    val statusCode: Int,
    val errorCode: String,
    override val message: String
) : IOException(message)

class DjangoApiClient(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("django_api", Context.MODE_PRIVATE)
    private val tokenStore = EncryptedTokenStore(appContext)
    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()
    private val http = OkHttpClient.Builder()
        .callTimeout(java.time.Duration.ofSeconds(30))
        .build()

    var baseUrl: String
        get() = preferences.getString(KEY_BASE_URL, DEFAULT_API_BASE_URL) ?: DEFAULT_API_BASE_URL
        private set(value) {
            preferences.edit().putString(KEY_BASE_URL, normalizeBaseUrl(value)).apply()
        }

    val hasSession: Boolean
        get() = tokenStore.contains()

    val tenantConfigured: Boolean
        get() = preferences.getBoolean(KEY_TENANT_CONFIGURED, false)

    val configuredSchoolName: String
        get() = preferences.getString(KEY_SCHOOL_NAME, "").orEmpty()

    suspend fun login(
        baseUrl: String,
        username: String,
        password: String,
        expectedSchoolName: String? = null
    ): JSONObject =
        withContext(Dispatchers.IO) {
            val normalizedUrl = normalizeBaseUrl(baseUrl)
            if (tenantConfigured && normalizedUrl != this@DjangoApiClient.baseUrl) {
                throw IllegalStateException(
                    "This device is configured for $configuredSchoolName. Only an administrator can change school setup."
                )
            }
            require(normalizedUrl.startsWith("https://") || BuildConfig.DEBUG) {
                "Use HTTPS for the school API. HTTP is allowed only in debug builds."
            }
            val deviceId = Settings.Secure.getString(
                appContext.contentResolver,
                Settings.Secure.ANDROID_ID
            ).orEmpty()
            val payload = JSONObject()
                .put("username", username.trim())
                .put("password", password)
                .put(
                    "device",
                    JSONObject()
                        .put("device_id", deviceId)
                        .put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}".take(150))
                        .put("platform", "android")
                        .put("app_version", BuildConfig.VERSION_NAME)
                )
            val response = execute(
                url = normalizedUrl + "/auth/login/",
                method = "POST",
                payload = payload,
                authenticated = false
            )
            val token = response.optString("token")
            if (token.isBlank()) throw IOException("The server response did not include an access token.")
            tokenStore.write(token)
            this@DjangoApiClient.baseUrl = normalizedUrl
            _sessionExpired.value = false
            val user = response.optJSONObject("user")
            val capabilities = user?.optJSONObject("capabilities")
            val role = user?.optString("role").orEmpty()
            val schoolName = response.optJSONObject("school")?.optString("name").orEmpty()
            val schoolMatchesPreview =
                expectedSchoolName.isNullOrBlank() || schoolName == expectedSchoolName
            val validSession = user?.optBoolean("is_active") == true &&
                role in SUPPORTED_ROLES &&
                capabilities != null &&
                role.equals(capabilities.optString("role"), ignoreCase = true) &&
                schoolName.isNotBlank() &&
                schoolMatchesPreview &&
                (role != "ADMIN" || capabilities.optBoolean("is_admin")) &&
                (
                    role !in HOUSE_ASSIGNED_ROLES ||
                        !capabilities.optString("house_id").isNullOrBlank() &&
                        capabilities.optString("house_id") != "null"
                    )
            val firstSetupAllowed = tenantConfigured || capabilities?.optBoolean("is_admin") == true
            if (!validSession || !firstSetupAllowed) {
                try {
                    logout()
                } finally {
                    clearSession()
                }
                throw IOException(
                    when {
                        !schoolMatchesPreview ->
                            "The sign-in server identifies a different school than the one you verified."
                        !validSession ->
                            "The school server returned an incomplete or unsupported staff account."
                        else ->
                            "Initial school setup must be completed by a school administrator. Ask the administrator to configure this device."
                    }
                )
            }
            if (!tenantConfigured) {
                preferences.edit()
                    .putBoolean(KEY_TENANT_CONFIGURED, true)
                    .putString(KEY_SCHOOL_NAME, schoolName)
                    .apply()
            } else if (configuredSchoolName != schoolName) {
                preferences.edit().putString(KEY_SCHOOL_NAME, schoolName).apply()
            }
            response
        }

    suspend fun previewSchool(baseUrl: String): JSONObject = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        require(normalizedUrl.startsWith("https://") || BuildConfig.DEBUG) {
            "Use HTTPS for the school API. HTTP is allowed only in debug builds."
        }
        val preview = execute(
            url = normalizedUrl + "/public/school/",
            method = "GET",
            payload = null,
            authenticated = false
        )
        val school = preview.optJSONObject("school")
            ?: throw IOException("The server did not identify a school for this tenant URL.")
        if (school.optString("name").isBlank()) {
            throw IOException("The server returned an empty school name.")
        }
        preview
    }

    suspend fun get(path: String, query: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val queryString = query.entries.joinToString("&") {
                "${java.net.URLEncoder.encode(it.key, Charsets.UTF_8.name())}=" +
                    java.net.URLEncoder.encode(it.value, Charsets.UTF_8.name())
            }
            val suffix = if (queryString.isEmpty()) "" else "?$queryString"
            execute(baseUrl + "/" + path.trimStart('/') + suffix, "GET", null, true)
        }

    suspend fun post(path: String, payload: JSONObject = JSONObject()): JSONObject =
        withContext(Dispatchers.IO) {
            execute(
                baseUrl + "/" + path.trimStart('/'),
                "POST",
                payload,
                authenticated = true
            )
        }

    suspend fun logout() = withContext(Dispatchers.IO) {
        try {
            execute(baseUrl + "/auth/logout/", "POST", JSONObject(), authenticated = true)
        } finally {
            clearSession()
        }
    }

    fun clearSession() {
        tokenStore.clear()
        _sessionExpired.value = false
    }

    fun expireSession() {
        tokenStore.clear()
        _sessionExpired.value = true
    }

    private fun execute(
        url: String,
        method: String,
        payload: JSONObject?,
        authenticated: Boolean
    ): JSONObject {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("X-App-Version", BuildConfig.VERSION_NAME)
        if (authenticated) {
            val token = tokenStore.read()
                ?: throw DjangoApiException(401, "authentication_required", "Sign in to continue.")
            builder.header("Authorization", "Bearer $token")
        }
        val body = payload?.toString()
            ?.toRequestBody("application/json; charset=utf-8".toMediaType())
        if (method == "POST") {
            builder.header("Idempotency-Key", UUID.randomUUID().toString())
        }
        builder.method(method, body)

        http.newCall(builder.build()).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (response.code == 204 && responseText.isEmpty()) return JSONObject()
            val envelope = try {
                JSONObject(responseText)
            } catch (_: Exception) {
                if (response.isSuccessful) {
                    throw IOException("The school API returned an unreadable response (HTTP ${response.code}).")
                }
                throw DjangoApiException(
                    response.code,
                    "http_${response.code}",
                    "The school API request failed (HTTP ${response.code})."
                )
            }
            if (!response.isSuccessful) {
                val error = envelope.optJSONObject("error")
                if (response.code == 401 && authenticated) {
                    expireSession()
                }
                throw DjangoApiException(
                    response.code,
                    error?.optString("code").orEmpty().ifBlank { "http_${response.code}" },
                    error?.optString("message").orEmpty().ifBlank {
                        "The school API request failed (HTTP ${response.code})."
                    }
                )
            }
            val data = envelope.optJSONObject("data")
                ?: when (val data = envelope.opt("data")) {
                    is JSONObject -> data
                    is JSONArray -> JSONObject().put("items", data)
                    else -> JSONObject()
                }
            envelope.optJSONObject("pagination")?.let { data.put("pagination", it) }
            return data
        }
    }

    private fun normalizeBaseUrl(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        require(trimmed.isNotBlank()) { "Enter the school's API base URL." }
        require(trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            "The API URL must start with https://."
        }
        return if (trimmed.endsWith("/api/v1")) trimmed else "$trimmed/api/v1"
    }

    companion object {
        private const val DEFAULT_API_BASE_URL = "https://atebubushs.ecreativesys.com/api/v1"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TENANT_CONFIGURED = "tenant_configured"
        private const val KEY_SCHOOL_NAME = "school_name"
        private val SUPPORTED_ROLES = setOf(
            "ADMIN",
            "SENIOR_HOUSEMASTER",
            "HOUSEMASTER",
            "HOUSE_TUTOR",
            "GATE_OFFICER",
            "MEDICAL"
        )
        private val HOUSE_ASSIGNED_ROLES = setOf("HOUSEMASTER", "HOUSE_TUTOR")
    }
}

private class EncryptedTokenStore(context: Context) {
    private val preferences = context.getSharedPreferences("django_api_secrets", Context.MODE_PRIVATE)

    fun read(): String? {
        val encoded = preferences.getString(TOKEN_KEY, null) ?: return null
        return try {
            val raw = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, IV_LENGTH)
            val ciphertext = raw.copyOfRange(IV_LENGTH, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (error: Exception) {
            clear()
            throw IllegalStateException("Stored sign-in credentials could not be decrypted. Please sign in again.", error)
        }
    }

    fun contains(): Boolean = preferences.contains(TOKEN_KEY)

    fun write(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        preferences.edit().putString(TOKEN_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    fun clear() {
        preferences.edit().remove(TOKEN_KEY).apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val TOKEN_KEY = "access_token"
        private const val KEY_ALIAS = "eden_django_api_token"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
    }
}
