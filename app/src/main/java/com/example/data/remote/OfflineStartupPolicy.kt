package com.example.data.remote

import org.json.JSONArray
import org.json.JSONObject

object OfflineStartupPolicy {
    fun canRestoreSession(
        hasCredentials: Boolean,
        tenantConfigured: Boolean,
        cachedSessionMatchesTenant: Boolean,
        hasUsableCache: Boolean,
        accessRestricted: Boolean,
        lastSuccessfulAuthenticationAt: Long?,
        lastSuccessfulSyncAt: Long?,
        now: Long,
        gracePeriodMillis: Long
    ): Boolean {
        val validatedAt = lastSuccessfulAuthenticationAt ?: return false
        val syncedAt = lastSuccessfulSyncAt ?: return false
        return hasCredentials &&
            tenantConfigured &&
            cachedSessionMatchesTenant &&
            hasUsableCache &&
            !accessRestricted &&
            gracePeriodMillis >= 0L &&
            now >= validatedAt &&
            now - validatedAt <= gracePeriodMillis &&
            now >= syncedAt &&
            now - syncedAt <= gracePeriodMillis
    }
}

object SessionAccessPolicy {
    fun matches(
        cachedUser: JSONObject?,
        currentUser: JSONObject?,
        cachedFeatures: JSONObject?,
        currentFeatures: JSONObject?
    ): Boolean {
        if (cachedUser == null || currentUser == null) return false
        return cachedUser.optString("role") == currentUser.optString("role") &&
            jsonObjectsEqual(
                cachedUser.optJSONObject("capabilities"),
                currentUser.optJSONObject("capabilities")
            ) &&
            jsonObjectsEqual(cachedFeatures, currentFeatures)
    }

    private fun jsonObjectsEqual(left: JSONObject?, right: JSONObject?): Boolean {
        if (left == null || right == null) return left == null && right == null
        return canonicalJson(left) == canonicalJson(right)
    }

    private fun canonicalJson(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence()
            .toList()
            .sorted()
            .joinToString(prefix = "{", postfix = "}") { key ->
                "${JSONObject.quote(key)}:${canonicalJson(value.opt(key))}"
            }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") {
            canonicalJson(value.get(it))
        }
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }
}

object CacheOwnershipPolicy {
    fun belongsToOwner(cachedOwnerId: String?, activeOwnerId: String): Boolean =
        !cachedOwnerId.isNullOrBlank() && cachedOwnerId == activeOwnerId
}
