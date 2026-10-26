package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.DjangoApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
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
}
