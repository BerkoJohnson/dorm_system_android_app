package com.example.data.model

import java.security.MessageDigest

object RollCallIdempotencyKey {
    fun create(
        ownerId: String,
        tenantUrl: String,
        houseId: String,
        roomId: String,
        sessionType: String,
        inspectionDate: String
    ): String {
        val source = listOf(
            ownerId,
            tenantUrl,
            houseId,
            roomId,
            sessionType,
            inspectionDate
        ).joinToString("\u001F")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "rollcall-$digest"
    }
}
