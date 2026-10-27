package com.example

import com.example.data.model.OutboxKind
import com.example.data.repository.OutboxWorkScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OutboxWorkSchedulerTest {
    @Test
    fun uniqueWorkIdentityIsStableButScopedToAccountAndTenant() {
        val first = OutboxWorkScheduler.workName(
            OutboxKind.REMOTE_MUTATION,
            "action-1",
            "staff-1",
            "https://school-a.example/api/v1"
        )
        assertEquals(
            first,
            OutboxWorkScheduler.workName(
                OutboxKind.REMOTE_MUTATION,
                "action-1",
                "staff-1",
                "https://school-a.example/api/v1"
            )
        )
        assertNotEquals(
            first,
            OutboxWorkScheduler.workName(
                OutboxKind.REMOTE_MUTATION,
                "action-1",
                "staff-2",
                "https://school-a.example/api/v1"
            )
        )
        assertNotEquals(
            first,
            OutboxWorkScheduler.workName(
                OutboxKind.REMOTE_MUTATION,
                "action-1",
                "staff-1",
                "https://school-b.example/api/v1"
            )
        )
        assertNotEquals(
            first,
            OutboxWorkScheduler.workName(
                OutboxKind.ROLL_CALL,
                "action-1",
                "staff-1",
                "https://school-a.example/api/v1"
            )
        )
        assertFalse(first.contains("staff-1"))
        assertFalse(first.contains("school-a"))
    }
}
