package com.example

import com.example.data.remote.DjangoApiClient
import com.example.data.remote.DjangoApiException
import com.example.data.remote.CacheOwnershipPolicy
import com.example.data.remote.OfflineStartupPolicy
import com.example.data.remote.SessionAccessPolicy
import com.example.data.remote.SessionValidationFailure
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineStartupPolicyTest {
    @Test
    fun coldStartAfterSevenDaysCanRestoreUsableCachedSessionOffline() {
        val now = 1_800_000_000_000L
        assertTrue(
            OfflineStartupPolicy.canRestoreSession(
                hasCredentials = true,
                tenantConfigured = true,
                cachedSessionMatchesTenant = true,
                hasUsableCache = true,
                accessRestricted = false,
                lastSuccessfulAuthenticationAt = now - SEVEN_DAYS,
                lastSuccessfulSyncAt = now - SEVEN_DAYS,
                now = now,
                gracePeriodMillis = THIRTY_DAYS
            )
        )
    }

    @Test
    fun processRestartUsesPersistableInputsAndGraceExpiryBlocksOfflineAccess() {
        val now = 1_800_000_000_000L
        val persistedAuthenticationTime = now - 31 * SEVEN_DAYS / 7
        assertFalse(
            OfflineStartupPolicy.canRestoreSession(
                hasCredentials = true,
                tenantConfigured = true,
                cachedSessionMatchesTenant = true,
                hasUsableCache = true,
                accessRestricted = false,
                lastSuccessfulAuthenticationAt = persistedAuthenticationTime,
                lastSuccessfulSyncAt = now - SEVEN_DAYS,
                now = now,
                gracePeriodMillis = THIRTY_DAYS
            )
        )
    }

    @Test
    fun missingCacheWrongTenantAndPreviouslyDeniedAccessCannotStartOffline() {
        assertFalse(canRestore(hasUsableCache = false))
        assertFalse(canRestore(cachedSessionMatchesTenant = false))
        assertFalse(canRestore(accessRestricted = true))
    }

    @Test
    fun cachedSchoolRecordsAreNotReusedAcrossAccounts() {
        assertTrue(CacheOwnershipPolicy.belongsToOwner("staff-1", "staff-1"))
        assertFalse(CacheOwnershipPolicy.belongsToOwner("staff-1", "staff-2"))
        assertFalse(CacheOwnershipPolicy.belongsToOwner(null, "staff-1"))
    }

    @Test
    fun sessionAccessComparisonIgnoresJsonKeyOrderButDetectsPermissionChanges() {
        val cachedUser = JSONObject(
            """{"role":"HOUSEMASTER","capabilities":{"house_id":"house-1","students_view":true}}"""
        )
        val sameAccessWithDifferentKeyOrder = JSONObject(
            """{"capabilities":{"students_view":true,"house_id":"house-1"},"role":"HOUSEMASTER"}"""
        )
        assertTrue(
            SessionAccessPolicy.matches(
                cachedUser,
                sameAccessWithDifferentKeyOrder,
                JSONObject("""{"roll_call":true,"exeats":true}"""),
                JSONObject("""{"exeats":true,"roll_call":true}""")
            )
        )

        val restrictedUser = JSONObject(
            """{"role":"HOUSEMASTER","capabilities":{"house_id":"house-1","students_view":false}}"""
        )
        assertFalse(
            SessionAccessPolicy.matches(
                cachedUser,
                restrictedUser,
                JSONObject("""{"roll_call":true}"""),
                JSONObject("""{"roll_call":true}""")
            )
        )
    }

    @Test
    fun recentAuthenticationDoesNotExtendStaleCacheGrace() {
        val now = TEST_NOW
        assertFalse(
            OfflineStartupPolicy.canRestoreSession(
                hasCredentials = true,
                tenantConfigured = true,
                cachedSessionMatchesTenant = true,
                hasUsableCache = true,
                accessRestricted = false,
                lastSuccessfulAuthenticationAt = now - SEVEN_DAYS,
                lastSuccessfulSyncAt = now - 31 * SEVEN_DAYS / 7,
                now = now,
                gracePeriodMillis = THIRTY_DAYS
            )
        )
    }

    @Test
    fun networkAndServerFailuresAreTransientBut401And403AreNot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = DjangoApiClient(context)

        assertEquals(
            SessionValidationFailure.TRANSIENT,
            client.classifySessionValidationFailure(IOException("offline"))
        )
        assertEquals(
            SessionValidationFailure.TRANSIENT,
            client.classifySessionValidationFailure(SocketTimeoutException("timed out"))
        )
        assertEquals(
            SessionValidationFailure.TRANSIENT,
            client.classifySessionValidationFailure(UnknownHostException("dns"))
        )
        assertEquals(
            SessionValidationFailure.TRANSIENT,
            client.classifySessionValidationFailure(
                DjangoApiException(500, "server_error", "server error")
            )
        )
        assertEquals(
            SessionValidationFailure.INVALID_CREDENTIALS,
            client.classifySessionValidationFailure(
                DjangoApiException(401, "token_expired", "expired")
            )
        )
        assertEquals(
            SessionValidationFailure.ACCESS_RESTRICTED,
            client.classifySessionValidationFailure(
                DjangoApiException(403, "permission_revoked", "denied")
            )
        )
        assertTrue(client.accessRestrictionMessage("account_suspended").contains("suspended"))
        assertTrue(client.accessRestrictionMessage("tenant_access_revoked").contains("school"))
        assertTrue(client.accessRestrictionMessage("permission_revoked").contains("revoked"))
    }

    private fun canRestore(
        cachedSessionMatchesTenant: Boolean = true,
        hasUsableCache: Boolean = true,
        accessRestricted: Boolean = false
    ): Boolean =
        OfflineStartupPolicy.canRestoreSession(
            hasCredentials = true,
            tenantConfigured = true,
            cachedSessionMatchesTenant = cachedSessionMatchesTenant,
            hasUsableCache = hasUsableCache,
            accessRestricted = accessRestricted,
            lastSuccessfulAuthenticationAt = TEST_NOW,
            lastSuccessfulSyncAt = TEST_NOW,
            now = TEST_NOW,
            gracePeriodMillis = THIRTY_DAYS
        )

    companion object {
        private const val TEST_NOW = 1_800_000_000_000L
        private const val SEVEN_DAYS = 7 * 24 * 60 * 60 * 1000L
        private const val THIRTY_DAYS = 30 * SEVEN_DAYS / 7
    }
}
