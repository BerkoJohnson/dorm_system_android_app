package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.remote.DjangoApiClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedOfflineSessionTest {
    @Test
    fun validatedSessionIsEncryptedPersistedAndBoundToItsTenant() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("django_api", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("django_api_secrets", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val client = DjangoApiClient(context)
        client.rememberSchoolSelection("https://school.example", "Example School")
        val session = JSONObject()
            .put(
                "user",
                JSONObject()
                    .put("id", "staff-17")
                    .put("role", "HOUSEMASTER")
                    .put("is_active", true)
                    .put(
                        "capabilities",
                        JSONObject()
                            .put("role", "HOUSEMASTER")
                            .put("house_id", "house-1")
                    )
            )
        client.recordSuccessfulValidation(session, now = 1_000L)

        val persistedSecretPreferences = context.getSharedPreferences(
            "django_api_secrets",
            Context.MODE_PRIVATE
        )
        assertFalse(persistedSecretPreferences.contains("session"))
        val encryptedSession =
            persistedSecretPreferences.getString("encrypted_session_metadata", null)
        assertTrue(encryptedSession != null && !encryptedSession.contains("staff-17"))
        val restoredClient = DjangoApiClient(context)
        assertEquals(
            "staff-17",
            restoredClient.cachedSessionForCurrentTenant()
                ?.optJSONObject("user")
                ?.optString("id")
        )
        assertEquals(1_000L, restoredClient.lastSuccessfulAuthenticationAt)

        restoredClient.rememberSchoolSelection("https://different.example", "Different School")
        assertNull(restoredClient.cachedSessionForCurrentTenant())
    }
}
