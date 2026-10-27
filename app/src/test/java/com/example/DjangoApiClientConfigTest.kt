package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.DjangoApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DjangoApiClientConfigTest {
    @Test
    fun selectedTenantUrlAndNamePersistBeforeFirstSignIn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("django_api", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        DjangoApiClient(context).rememberSchoolSelection(
            "https://school.example",
            "Example School"
        )

        val restoredClient = DjangoApiClient(context)
        assertEquals("https://school.example/api/v1", restoredClient.baseUrl)
        assertEquals("Example School", restoredClient.configuredSchoolName)
        assertFalse(restoredClient.tenantConfigured)
    }

    @Test
    fun selectedTenantProfilePersistsBrandingAndContactDetails() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("django_api", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        DjangoApiClient(context).rememberSchoolSelection(
            "https://school.example",
            JSONObject()
                .put("name", "Example School")
                .put("address", "123 School Road")
                .put("phone_number", "+233200000000")
                .put("email", "office@school.example")
                .put("logo_url", "/media/school-logo.png")
        )

        val restoredClient = DjangoApiClient(context)
        assertEquals("Example School", restoredClient.configuredSchoolName)
        assertEquals("123 School Road", restoredClient.configuredSchoolAddress)
        assertEquals("+233200000000", restoredClient.configuredSchoolPhone)
        assertEquals("office@school.example", restoredClient.configuredSchoolEmail)
        assertEquals(
            "https://school.example/media/school-logo.png",
            restoredClient.configuredSchoolLogoUrl
        )
    }

    @Test
    fun lastSyncTimeAndConfiguredOfflineGracePersist() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("django_api", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val client = DjangoApiClient(context)
        assertEquals(30L * 24 * 60 * 60 * 1000, client.offlineGracePeriodMillis)
        client.configureOfflineGracePeriodDays(7)
        client.recordSuccessfulSync(now = 1000L)

        val restoredClient = DjangoApiClient(context)
        assertEquals(1000L, restoredClient.lastSuccessfulSyncAt)
        assertEquals(7L * 24 * 60 * 60 * 1000, restoredClient.offlineGracePeriodMillis)
        restoredClient.clearLastSuccessfulSync()
        assertNull(restoredClient.lastSuccessfulSyncAt)
    }
}
