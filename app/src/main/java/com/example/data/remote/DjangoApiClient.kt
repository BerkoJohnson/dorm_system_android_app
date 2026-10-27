package com.example.data.remote

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore
import java.util.UUID
import android.net.Uri
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
import java.util.concurrent.TimeUnit

class DjangoApiException(
    val statusCode: Int,
    val errorCode: String,
    override val message: String
) : IOException(message)

enum class SessionValidationFailure {
    TRANSIENT,
    INVALID_CREDENTIALS,
    ACCESS_RESTRICTED,
    UNEXPECTED
}

class DjangoApiClient(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("django_api", Context.MODE_PRIVATE)
    private val tokenStore = EncryptedTokenStore(appContext)
    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()
    private val http = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    var baseUrl: String
        get() = preferences.getString(KEY_BASE_URL, DEFAULT_API_BASE_URL) ?: DEFAULT_API_BASE_URL
        private set(value) {
            preferences.edit().putString(KEY_BASE_URL, normalizeBaseUrl(value)).apply()
        }

    val hasSession: Boolean
        get() = tokenStore.contains() && try {
            tokenStore.read() != null
        } catch (_: IllegalStateException) {
            false
        }

    val tenantConfigured: Boolean
        get() = preferences.getBoolean(KEY_TENANT_CONFIGURED, false)

    val configuredSchoolName: String
        get() = preferences.getString(KEY_SCHOOL_NAME, "").orEmpty()

    val configuredSchoolAddress: String
        get() = preferences.getString(KEY_SCHOOL_ADDRESS, "").orEmpty()

    val configuredSchoolPhone: String
        get() = preferences.getString(KEY_SCHOOL_PHONE, "").orEmpty()

    val configuredSchoolEmail: String
        get() = preferences.getString(KEY_SCHOOL_EMAIL, "").orEmpty()

    val configuredSchoolLogoUrl: String
        get() = preferences.getString(KEY_SCHOOL_LOGO_URL, "").orEmpty()

    val lastSuccessfulAuthenticationAt: Long?
        get() = preferences.getLong(KEY_LAST_AUTHENTICATION, 0L).takeIf { it > 0L }

    val lastSuccessfulSyncAt: Long?
        get() = preferences.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0L }

    val offlineGracePeriodMillis: Long
        get() = preferences.getInt(KEY_OFFLINE_GRACE_DAYS, DEFAULT_OFFLINE_GRACE_DAYS)
            .coerceAtLeast(0)
            .toLong() * MILLIS_PER_DAY

    val accessRestrictionCode: String?
        get() = preferences.getString(KEY_ACCESS_RESTRICTION, null)

    fun configureOfflineGracePeriodDays(days: Int) {
        require(days in 0..MAX_OFFLINE_GRACE_DAYS) {
            "Offline grace period must be between 0 and $MAX_OFFLINE_GRACE_DAYS days."
        }
        preferences.edit().putInt(KEY_OFFLINE_GRACE_DAYS, days).apply()
    }

    fun hasUsableOfflineGrace(now: Long = System.currentTimeMillis()): Boolean {
        val validatedAt = lastSuccessfulAuthenticationAt ?: return false
        val syncedAt = lastSuccessfulSyncAt ?: return false
        return now >= validatedAt &&
            now - validatedAt <= offlineGracePeriodMillis &&
            now >= syncedAt &&
            now - syncedAt <= offlineGracePeriodMillis
    }

    fun cachedSessionForCurrentTenant(): JSONObject? {
        val cached = tokenStore.readMetadata() ?: return null
        val metadata = try {
            JSONObject(cached)
        } catch (_: JSONException) {
            tokenStore.clearMetadata()
            return null
        }
        if (metadata.optString(KEY_SESSION_TENANT) != baseUrl) return null
        return metadata.optJSONObject(KEY_CACHED_SESSION)
    }

    fun recordSuccessfulValidation(session: JSONObject, now: Long = System.currentTimeMillis()) {
        tokenStore.writeMetadata(
            JSONObject()
                .put(KEY_SESSION_TENANT, baseUrl)
                .put(KEY_CACHED_SESSION, session)
                .toString()
        )
        preferences.edit()
            .putLong(KEY_LAST_AUTHENTICATION, now)
            .remove(KEY_ACCESS_RESTRICTION)
            .apply()
    }

    fun recordSuccessfulSync(now: Long = System.currentTimeMillis()) {
        preferences.edit().putLong(KEY_LAST_SYNC, now).apply()
    }

    fun clearLastSuccessfulSync() {
        preferences.edit().remove(KEY_LAST_SYNC).apply()
    }

    fun restrictOfflineStartup(reasonCode: String) {
        preferences.edit().putString(KEY_ACCESS_RESTRICTION, reasonCode).apply()
    }

    fun clearAccessRestriction() {
        preferences.edit().remove(KEY_ACCESS_RESTRICTION).apply()
    }

    fun classifySessionValidationFailure(error: Throwable): SessionValidationFailure =
        when (error) {
            is DjangoApiException -> when (error.statusCode) {
                401 -> SessionValidationFailure.INVALID_CREDENTIALS
                403 -> SessionValidationFailure.ACCESS_RESTRICTED
                in 500..599, 408, 429 -> SessionValidationFailure.TRANSIENT
                else -> SessionValidationFailure.UNEXPECTED
            }
            is IOException -> SessionValidationFailure.TRANSIENT
            else -> SessionValidationFailure.UNEXPECTED
        }

    fun accessRestrictionMessage(errorCode: String): String = when {
        errorCode.contains("suspend", ignoreCase = true) ||
            errorCode.contains("inactive", ignoreCase = true) ->
            "The school account is suspended or inactive. Cached records are preserved; contact the administrator."
        errorCode.contains("tenant", ignoreCase = true) ||
            errorCode.contains("school", ignoreCase = true) ->
            "This account no longer has access to this school. Cached records are preserved."
        errorCode.contains("permission", ignoreCase = true) ||
            errorCode.contains("forbidden", ignoreCase = true) ->
            "School permissions were denied or revoked. Revalidate access with the school server; cached records are preserved."
        else ->
            "The school server denied access. Revalidate the account or contact the administrator; cached records are preserved."
    }

    fun rememberSchoolSelection(baseUrl: String, schoolName: String) {
        rememberSchoolSelection(
            baseUrl,
            JSONObject().put("name", schoolName)
        )
    }

    fun rememberSchoolSelection(baseUrl: String, school: JSONObject) {
        check(!tenantConfigured) {
            "This device already has a school configured. Contact the administrator to change it."
        }
        val schoolName = school.optString("name")
        require(schoolName.isNotBlank()) { "Verify the school before saving its server URL." }
        this.baseUrl = baseUrl
        persistSchoolProfile(school)
    }

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
            val previousSession = try {
                cachedSessionForCurrentTenant()
            } catch (_: IllegalStateException) {
                null
            }
            val previousUser = previousSession?.optJSONObject("user")
            val previousUserId = previousUser?.optString("id").orEmpty()
            val newUserId = user?.optString("id").orEmpty()
            val cachedAccessMatches = SessionAccessPolicy.matches(
                previousUser,
                user,
                previousSession?.optJSONObject("features"),
                response.optJSONObject("features")
            )
            if (
                previousUserId == newUserId &&
                previousUserId.isNotBlank() &&
                cachedAccessMatches
            ) {
                recordSuccessfulValidation(response)
            } else {
                tokenStore.clearMetadata()
                preferences.edit()
                    .remove(KEY_LAST_AUTHENTICATION)
                    .remove(KEY_LAST_SYNC)
                    .remove(KEY_ACCESS_RESTRICTION)
                    .apply()
            }
            if (!tenantConfigured) {
                preferences.edit()
                    .putBoolean(KEY_TENANT_CONFIGURED, true)
                    .putString(KEY_SCHOOL_NAME, schoolName)
                    .apply()
            } else if (configuredSchoolName != schoolName) {
                preferences.edit().putString(KEY_SCHOOL_NAME, schoolName).apply()
            }
            response.optJSONObject("school")?.let { persistSchoolProfile(it) }
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

    suspend fun download(path: String, query: Map<String, String> = emptyMap()): ByteArray =
        withContext(Dispatchers.IO) {
            val queryString = query.entries.joinToString("&") {
                "${java.net.URLEncoder.encode(it.key, Charsets.UTF_8.name())}=" +
                    java.net.URLEncoder.encode(it.value, Charsets.UTF_8.name())
            }
            val suffix = if (queryString.isEmpty()) "" else "?$queryString"
            val token = tokenStore.read()
                ?: throw DjangoApiException(401, "authentication_required", "Sign in to continue.")
            val request = Request.Builder()
                .url(baseUrl + "/" + path.trimStart('/') + suffix)
                .header("Accept", "*/*")
                .header("X-App-Version", BuildConfig.VERSION_NAME)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val responseText = response.body?.string().orEmpty()
                    val error = runCatching { JSONObject(responseText).optJSONObject("error") }
                        .getOrNull()
                    if (response.code == 401) expireSession()
                    throw DjangoApiException(
                        response.code,
                        error?.optString("code").orEmpty().ifBlank { "http_${response.code}" },
                        error?.optString("message").orEmpty().ifBlank {
                            "The school API request failed (HTTP ${response.code})."
                        }
                    )
                }
                response.body?.bytes()
                    ?: throw IOException("The school API returned an empty export.")
            }
        }

    suspend fun post(
        path: String,
        payload: JSONObject = JSONObject(),
        idempotencyKey: String? = null
    ): JSONObject =
        withContext(Dispatchers.IO) {
            execute(
                baseUrl + "/" + path.trimStart('/'),
                "POST",
                payload,
                authenticated = true,
                idempotencyKey = idempotencyKey
            )
        }

    suspend fun uploadImage(path: String, uri: Uri): JSONObject =
        withContext(Dispatchers.IO) {
            val resolver = appContext.contentResolver
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("The selected photo could not be opened.")
            if (bytes.isEmpty()) throw IOException("The selected photo is empty.")
            val mediaType = resolver.getType(uri)?.toMediaType()
                ?: "application/octet-stream".toMediaType()
            require(mediaType.type == "image") { "Select an image file for the student photo." }
            require(bytes.size <= MAX_STUDENT_PHOTO_BYTES) {
                "The student photo must be 10 MB or smaller."
            }
            val token = tokenStore.read()
                ?: throw DjangoApiException(401, "authentication_required", "Sign in to continue.")
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "photo",
                    "student-photo",
                    bytes.toRequestBody(mediaType)
                )
                .build()
            val request = Request.Builder()
                .url(baseUrl + "/" + path.trimStart('/'))
                .header("Accept", "application/json")
                .header("X-App-Version", BuildConfig.VERSION_NAME)
                .header("Authorization", "******")
                .post(requestBody)
                .build()
            http.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (response.code == 204 && responseText.isEmpty()) return@use JSONObject()
                val envelope = try {
                    JSONObject(responseText)
                } catch (_: JSONException) {
                    if (response.isSuccessful) {
                        throw IOException("The school API returned an unreadable photo response.")
                    }
                    throw DjangoApiException(
                        response.code,
                        "http_${response.code}",
                        "The school API photo upload failed (HTTP ${response.code})."
                    )
                }
                if (!response.isSuccessful) {
                    val error = envelope.optJSONObject("error")
                    if (response.code == 401) expireSession()
                    throw DjangoApiException(
                        response.code,
                        error?.optString("code").orEmpty().ifBlank { "http_${response.code}" },
                        error?.optString("message").orEmpty().ifBlank {
                            "The school API photo upload failed (HTTP ${response.code})."
                        }
                    )
                }
                envelope.optJSONObject("data") ?: JSONObject()
            }
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
        tokenStore.clearMetadata()
        preferences.edit()
            .remove(KEY_LAST_AUTHENTICATION)
            .remove(KEY_LAST_SYNC)
            .remove(KEY_ACCESS_RESTRICTION)
            .apply()
        _sessionExpired.value = false
    }

    fun expireSession() {
        tokenStore.clear()
        _sessionExpired.value = true
    }

    private fun persistSchoolProfile(school: JSONObject) {
        val name = school.optString("name").takeIf(String::isNotBlank)
        val address = listOf(
            "address",
            "postal_address",
            "location"
        ).firstNotNullOfOrNull { key ->
            school.optString(key).takeIf { it.isNotBlank() && it != "null" }
        }
        val phone = listOf("phone_number", "phone", "telephone").firstNotNullOfOrNull { key ->
            school.optString(key).takeIf { it.isNotBlank() && it != "null" }
        }
        val email = listOf("email", "email_address").firstNotNullOfOrNull { key ->
            school.optString(key).takeIf { it.isNotBlank() && it != "null" }
        }
        val logoValue = school.optString("logo_url")
            .takeIf { it.isNotBlank() && it != "null" }
            ?: school.optJSONObject("logo")?.optString("url")
                ?.takeIf { it.isNotBlank() && it != "null" }
            ?: school.optString("logo").takeIf {
                it.isNotBlank() && it != "null" && !it.startsWith("{")
            }
        preferences.edit().apply {
            name?.let { putString(KEY_SCHOOL_NAME, it) }
            address?.let { putString(KEY_SCHOOL_ADDRESS, it) }
            phone?.let { putString(KEY_SCHOOL_PHONE, it) }
            email?.let { putString(KEY_SCHOOL_EMAIL, it) }
            logoValue?.let { putString(KEY_SCHOOL_LOGO_URL, resolveSchoolAssetUrl(it)) }
        }.apply()
    }

    private fun resolveSchoolAssetUrl(value: String): String {
        if (value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
        ) {
            return value
        }
        val origin = baseUrl.removeSuffix("/api/v1")
        return "$origin/${value.trimStart('/')}"
    }

    private fun execute(
        url: String,
        method: String,
        payload: JSONObject?,
        authenticated: Boolean,
        idempotencyKey: String? = null
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
            builder.header("Idempotency-Key", idempotencyKey ?: UUID.randomUUID().toString())
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
                if (
                    response.code == 403 &&
                    authenticated &&
                    url.substringBefore('?').endsWith("/auth/me/")
                ) {
                    preferences.edit()
                        .putString(
                            KEY_ACCESS_RESTRICTION,
                            error?.optString("code").orEmpty().ifBlank { "access_restricted" }
                        )
                        .apply()
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
        private const val KEY_SCHOOL_ADDRESS = "school_address"
        private const val KEY_SCHOOL_PHONE = "school_phone"
        private const val KEY_SCHOOL_EMAIL = "school_email"
        private const val KEY_SCHOOL_LOGO_URL = "school_logo_url"
        private const val KEY_LAST_AUTHENTICATION = "last_successful_authentication"
        private const val KEY_LAST_SYNC = "last_successful_sync"
        private const val KEY_OFFLINE_GRACE_DAYS = "offline_grace_days"
        private const val KEY_ACCESS_RESTRICTION = "access_restriction"
        private const val KEY_SESSION_TENANT = "tenant_url"
        private const val KEY_CACHED_SESSION = "session"
        private const val DEFAULT_OFFLINE_GRACE_DAYS = 30
        private const val MAX_OFFLINE_GRACE_DAYS = 365
        private const val MAX_STUDENT_PHOTO_BYTES = 10 * 1024 * 1024
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
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

    fun readMetadata(): String? {
        val encoded = preferences.getString(SESSION_METADATA_KEY, null) ?: return null
        return try {
            decrypt(encoded)
        } catch (error: Exception) {
            clearMetadata()
            throw IllegalStateException(
                "Stored offline session data could not be decrypted. Sign in again.",
                error
            )
        }
    }

    fun writeMetadata(metadata: String) {
        preferences.edit().putString(SESSION_METADATA_KEY, encrypt(metadata)).apply()
    }

    fun clearMetadata() {
        preferences.edit().remove(SESSION_METADATA_KEY).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, IV_LENGTH)
        val ciphertext = raw.copyOfRange(IV_LENGTH, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
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
        private const val SESSION_METADATA_KEY = "encrypted_session_metadata"
        private const val KEY_ALIAS = "eden_django_api_token"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
    }
}
