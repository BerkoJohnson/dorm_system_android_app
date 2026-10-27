package com.example.data.model

enum class UserRole(val displayName: String, val badgeTitle: String) {
    HOUSEMASTER("Housemaster / Housemistress", "Housemaster"),
    ASSISTANT_HOUSEMASTER("Assistant Housemaster", "Assistant Housemaster"),
    ACTING_HOUSEMASTER("Acting Housemaster", "Acting Housemaster"),
    HOUSE_TUTOR("House Tutor", "House Tutor"),
    SENIOR_HOUSEMASTER("Senior Housemaster", "Senior Housemaster"),
    HEADMASTER("Headmaster", "Headmaster · Read only"),
    MATRON("Matron", "Matron"),
    MEDICAL("Medical / Infirmary Lead", "Medical / Infirmary"),
    GATE_OFFICER("Gate Security Officer", "Campus Security"),
    ADMINISTRATOR("System Administrator", "Administration");

    val isHouseScoped: Boolean
        get() = this in setOf(
            HOUSEMASTER,
            ASSISTANT_HOUSEMASTER,
            ACTING_HOUSEMASTER,
            HOUSE_TUTOR,
            MATRON
        )

    val isReadOnly: Boolean
        get() = this == HEADMASTER
}

fun userRoleFromServerRole(role: String): UserRole? = when (role.trim().uppercase()) {
    "ADMIN" -> UserRole.ADMINISTRATOR
    "HEADMASTER", "HEAD_MASTER", "PRINCIPAL", "HEAD_TEACHER" -> UserRole.HEADMASTER
    "SENIOR_HOUSEMASTER" -> UserRole.SENIOR_HOUSEMASTER
    "HOUSEMASTER" -> UserRole.HOUSEMASTER
    "ASSISTANT_HOUSEMASTER", "ASSISTANT_HOUSEMISTRESS" -> UserRole.ASSISTANT_HOUSEMASTER
    "ACTING_HOUSEMASTER", "DELEGATED_HOUSEMASTER", "DUTY_HOUSEMASTER" ->
        UserRole.ACTING_HOUSEMASTER
    "HOUSE_TUTOR" -> UserRole.HOUSE_TUTOR
    "MATRON" -> UserRole.MATRON
    "GATE_OFFICER" -> UserRole.GATE_OFFICER
    "MEDICAL" -> UserRole.MEDICAL
    else -> null
}
