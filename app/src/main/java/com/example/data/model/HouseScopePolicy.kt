package com.example.data.model

object HouseScopePolicy {
    fun <T> filter(
        rows: List<T>,
        role: UserRole,
        assignedHouseId: String?,
        rowHouseId: (T) -> String
    ): List<T> {
        if (!role.isHouseScoped) return rows
        val houseId = assignedHouseId?.takeIf(String::isNotBlank) ?: return emptyList()
        return rows.filter { rowHouseId(it) == houseId }
    }
}
