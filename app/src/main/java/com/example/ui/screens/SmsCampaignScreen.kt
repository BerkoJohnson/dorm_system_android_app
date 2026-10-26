package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.House
import com.example.data.model.SmsCampaign
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SmsCampaignScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val houses by viewModel.houses.collectAsStateWithLifecycle()
    val campaigns by viewModel.smsCampaigns.collectAsStateWithLifecycle()
    val smsEnabled by viewModel.smsEnabled.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingSmsCampaigns.collectAsStateWithLifecycle()
    val messageError by viewModel.operationsMessage.collectAsStateWithLifecycle()
    var campaignMessage by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf("all_boarders") }
    var selectedHouse by remember { mutableStateOf<House?>(null) }
    var showHouseMenu by remember { mutableStateOf(false) }
    var decisionNote by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadSmsCampaigns() }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("GUARDIAN SMS CAMPAIGNS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = if (smsEnabled) {
                            "Campaigns require approval by a different administrator before delivery."
                        } else {
                            "SMS delivery is currently disabled in the school configuration."
                        },
                        color = Color(0xFF475569),
                        fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { scope = "all_boarders" }) {
                            Text(if (scope == "all_boarders") "✓ All boarders" else "All boarders")
                        }
                        TextButton(onClick = { scope = "house" }) {
                            Text(if (scope == "house") "✓ One house" else "One house")
                        }
                    }
                    if (scope == "house") {
                        TextButton(onClick = { showHouseMenu = true }) {
                            Text(selectedHouse?.name ?: "Choose a house")
                        }
                        DropdownMenu(
                            expanded = showHouseMenu,
                            onDismissRequest = { showHouseMenu = false }
                        ) {
                            houses.forEach { house ->
                                DropdownMenuItem(
                                    text = { Text(house.name) },
                                    onClick = {
                                        selectedHouse = house
                                        showHouseMenu = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = campaignMessage,
                        onValueChange = { campaignMessage = it.take(500) },
                        label = { Text("Message to guardians") },
                        supportingText = { Text("${campaignMessage.length}/500 characters") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    Button(
                        onClick = {
                            viewModel.createSmsCampaign(
                                scope,
                                if (scope == "house") selectedHouse?.id else null,
                                campaignMessage
                            )
                            campaignMessage = ""
                        },
                        enabled = smsEnabled && campaignMessage.isNotBlank() &&
                            (scope != "house" || selectedHouse != null),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Submit for second-admin approval") }
                    if (messageError != null) {
                        Text(messageError.orEmpty(), color = Color(0xFFB91C1C), fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Text("CAMPAIGN HISTORY", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        if (isLoading) {
            item { Text("Loading campaigns…", color = Color(0xFF64748B)) }
        }
        if (!isLoading && campaigns.isEmpty()) {
            item { Text("No campaigns found.", color = Color(0xFF64748B)) }
        }
        items(campaigns, key = SmsCampaign::id) { campaign ->
            CampaignCard(
                campaign = campaign,
                decisionNote = decisionNote,
                onDecisionNoteChange = { decisionNote = it.take(240) },
                onApprove = {
                    viewModel.decideSmsCampaign(campaign.id, true, decisionNote)
                    decisionNote = ""
                },
                onReject = {
                    viewModel.decideSmsCampaign(campaign.id, false, decisionNote)
                    decisionNote = ""
                }
            )
        }
    }
}

@Composable
private fun CampaignCard(
    campaign: SmsCampaign,
    decisionNote: String,
    onDecisionNoteChange: (String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(campaign.message, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${campaign.status} · ${campaign.recipientCount} recipients · " +
                    (campaign.houseName ?: "All boarders") + " · by ${campaign.createdBy}",
                color = Color(0xFF64748B),
                fontSize = 11.sp
            )
            if (campaign.decisionNote.isNotBlank()) {
                Text("Decision: ${campaign.decisionNote}", fontSize = 11.sp)
            }
            if (campaign.status.equals("Pending approval", ignoreCase = true)) {
                OutlinedTextField(
                    value = decisionNote,
                    onValueChange = onDecisionNoteChange,
                    label = { Text("Decision note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onApprove) { Text("Approve & queue") }
                    TextButton(onClick = onReject) { Text("Reject") }
                }
            }
        }
    }
}
