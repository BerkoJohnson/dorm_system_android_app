package com.example.data.model

enum class OutboxKind(val value: String) {
    REMOTE_MUTATION("remote"),
    ROLL_CALL("roll_call");

    companion object {
        fun fromValue(value: String?): OutboxKind? =
            entries.firstOrNull { it.value == value }
    }
}
