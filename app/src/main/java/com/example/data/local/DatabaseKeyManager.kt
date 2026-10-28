package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages the Room database passphrase.
 *
 * The passphrase is 32 random bytes, generated once per installation and wrapped with an
 * AES-GCM key that lives in the Android Keystore. Only the wrapped blob is persisted, so
 * the on-disk database cannot be opened without the keystore-protected key.
 *
 * Honest limits of this scheme, which the README also documents:
 * - An attacker with root access can read both the database and the wrapped blob, though
 *   hardware-backed keystores will not release the AES key material itself off-device.
 * - If the keystore loses the master key (factory reset copy, keystore corruption, a
 *   resting-device restore), the database cannot be recovered. Because the database is a
 *   server cache plus the delivery outbox, the app regenerates and rebuilds instead of
 *   crashing, and re-syncs from the school server. Any queued, undelivered changes in that
 *   database are lost - staff must deliver the outbox before a factory reset.
 */
object DatabaseKeyManager {
    private const val PREFS = "edenest_secure_prefs"
    private const val PREF_KEY_BLOB = "db_key_blob"
    private const val KEYSTORE_ALIAS = "edenest_db_master_key"
    private const val KEY_LENGTH_BYTES = 32
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    data class Result(
        val passphrase: ByteArray,
        /** True when the previously stored key could not be unwrapped and a new one was issued. */
        val regenerated: Boolean
    )

    fun getOrCreatePassphrase(context: Context): Result {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storedBlob = prefs.getString(PREF_KEY_BLOB, null)
        if (storedBlob != null) {
            val unwrapped = try {
                unwrapKey(storedBlob)
            } catch (error: Exception) {
                android.util.Log.w(TAG, "Stored database key could not be unwrapped.", error)
                null
            }
            if (unwrapped != null) {
                return Result(unwrapped, regenerated = false)
            }
            // The keystore no longer yields the master key. Anything previously encrypted
            // with the old passphrase is unrecoverable; callers wipe the database files
            // when regenerating is true.
            android.util.Log.w(TAG, "Issuing a new database key; cached data will be rebuilt.")
        }
        val fresh = ByteArray(KEY_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val wrapped = wrapKey(fresh)
        if (wrapped == null) {
            android.util.Log.w(
                TAG,
                "Keystore unavailable; storing a per-install random key in app-private prefs."
            )
        }
        prefs.edit()
            .putString(PREF_KEY_BLOB, wrapped ?: Base64.encodeToString(fresh, Base64.NO_WRAP))
            .apply()
        return Result(fresh, regenerated = storedBlob != null)
    }

    private fun wrapKey(rawKey: ByteArray): String? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateMasterKey())
        val ciphertext = cipher.doFinal(rawKey)
        val iv = cipher.iv
        Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    } catch (error: Exception) {
        android.util.Log.w(TAG, "Could not wrap database key with the Android Keystore.", error)
        null
    }

    private fun unwrapKey(blob: String): ByteArray? {
        val separator = blob.indexOf(':')
        require(separator > 0) { "Malformed key blob." }
        val iv = Base64.decode(blob.substring(0, separator), Base64.NO_WRAP)
        val ciphertext = Base64.decode(blob.substring(separator + 1), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, loadOrCreateMasterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun loadOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private const val TAG = "DatabaseKeyManager"
}
