package com.example.data.model

enum class UserRole(val displayName: String, val badgeTitle: String) {
    HOUSEMASTER("Housemaster / Housemistress", "Duty Housemaster"),
    HOUSE_TUTOR("House Tutor", "House Tutor"),
    SENIOR_HOUSEMASTER("Senior Housemaster", "Senior Housemaster"),
    MEDICAL("Medical / Infirmary Lead", "Medical Staff"),
    GATE_OFFICER("Gate Security Officer", "Campus Security"),
    ADMINISTRATOR("System Administrator", "Administration")
}

fun userRoleFromServerRole(role: String): UserRole? = when (role.trim().uppercase()) {
    "ADMIN" -> UserRole.ADMINISTRATOR
    "SENIOR_HOUSEMASTER" -> UserRole.SENIOR_HOUSEMASTER
    "HOUSEMASTER" -> UserRole.HOUSEMASTER
    "HOUSE_TUTOR" -> UserRole.HOUSE_TUTOR
    "GATE_OFFICER" -> UserRole.GATE_OFFICER
    "MEDICAL" -> UserRole.MEDICAL
    else -> null
}
