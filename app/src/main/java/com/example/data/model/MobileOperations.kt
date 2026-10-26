package com.example.data.model

data class MobileReport(
    val title: String,
    val recordCount: Int,
    val generatedAt: String,
    val metrics: List<Pair<String, String>>,
    val headers: List<String>,
    val rows: List<List<String>>,
    val sections: List<MobileReportSection>
)

data class MobileReportSection(
    val title: String,
    val headers: List<String>,
    val rows: List<List<String>>
)

data class MobileAuditEvent(
    val id: String,
    val action: String,
    val actor: String,
    val targetType: String,
    val summary: String,
    val createdAt: String
)

data class SmsCampaign(
    val id: String,
    val scope: String,
    val houseName: String?,
    val message: String,
    val status: String,
    val recipientCount: Int,
    val decisionNote: String,
    val createdBy: String
)
