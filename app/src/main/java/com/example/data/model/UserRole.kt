package com.example.data.model

enum class UserRole(val displayName: String, val badgeTitle: String) {
    HOUSEMASTER("Housemaster / Housemistress", "Duty Housemaster"),
    GATE_OFFICER("Gate Security Officer", "Campus Security"),
    ADMINISTRATOR("Senior Housemaster / Admin", "Administration")
}
