package com.example

import com.example.data.repository.NotificationPollingWorker
import com.example.data.repository.NotificationQuietHours
import com.example.data.repository.SchoolNotificationCategory
import com.example.ui.viewmodel.AppScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalTime

class NotificationRoutingTest {
    @Test
    fun notificationKindsUseRelevantChannelsAndDestinations() {
        assertEquals(
            AppScreen.EXEATS,
            SchoolNotificationCategory.fromKind("pending_exeat").destination
        )
        assertEquals(
            AppScreen.NOTIFICATIONS,
            SchoolNotificationCategory.fromKind("incident_reported").destination
        )
        assertEquals(
            AppScreen.ROLL_CALL_HISTORY,
            SchoolNotificationCategory.fromKind("roll_call_unmarked").destination
        )
        assertEquals(
            AppScreen.OUTBOX,
            SchoolNotificationCategory.fromKind("sync_rejected").destination
        )
    }

    @Test
    fun notificationDeliveryStateIsScopedToTenantAndAccount() {
        val sameScope = NotificationPollingWorker.stateKey("https://school.example/api", "staff-1")
        assertEquals(
            sameScope,
            NotificationPollingWorker.stateKey("https://school.example/api", "staff-1")
        )
        assertNotEquals(
            sameScope,
            NotificationPollingWorker.stateKey("https://other.example/api", "staff-1")
        )
        assertNotEquals(
            sameScope,
            NotificationPollingWorker.stateKey("https://school.example/api", "staff-2")
        )
    }

    @Test
    fun notificationQuietHoursDeferNighttimeAlertsIntoMorningDigest() {
        assertTrue(NotificationQuietHours.isQuiet(LocalTime.of(22, 0)))
        assertTrue(NotificationQuietHours.isQuiet(LocalTime.of(23, 45)))
        assertTrue(NotificationQuietHours.isQuiet(LocalTime.of(6, 59)))
        assertFalse(NotificationQuietHours.isQuiet(LocalTime.of(7, 0)))
        assertFalse(NotificationQuietHours.isQuiet(LocalTime.of(12, 0)))
    }
}
