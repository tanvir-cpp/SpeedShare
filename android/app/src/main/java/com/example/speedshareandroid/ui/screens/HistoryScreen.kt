package com.example.speedshareandroid.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speedshareandroid.models.HistoryFilter
import com.example.speedshareandroid.theme.*
import com.example.speedshareandroid.ui.SpeedShareViewModel
import com.example.speedshareandroid.ui.components.HistoryItemCard

@Composable
fun HistoryScreen(
    viewModel: SpeedShareViewModel
) {
    val context = LocalContext.current
    val historyRecords by viewModel.filteredHistoryRecords.collectAsState()
    val allRecords by viewModel.allHistoryRecords.collectAsState()
    val searchQuery by viewModel.historySearchQuery.collectAsState()
    val currentFilter by viewModel.historyFilter.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgMidnight)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Search Input Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setHistorySearchQuery(it) },
            placeholder = { Text("Search by file name or device...", color = TextMuted, fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setHistorySearchQuery("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = BgCard,
                unfocusedContainerColor = BgCard,
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = BorderGlass,
                focusedTextColor = TextPureWhite,
                unfocusedTextColor = TextPureWhite
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Pills Row & Clear Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                val filters = listOf(
                    Pair(HistoryFilter.ALL, "All (${allRecords.size})"),
                    Pair(HistoryFilter.RECEIVED, "Received"),
                    Pair(HistoryFilter.SENT, "Sent"),
                    Pair(HistoryFilter.FAILED, "Failed")
                )

                items(filters) { (filter, label) ->
                    val isSelected = currentFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setHistoryFilter(filter) },
                        label = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = BgCard,
                            selectedContainerColor = NeonIndigo.copy(alpha = 0.25f),
                            labelColor = TextSecondary,
                            selectedLabelColor = NeonCyan
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = BorderGlass,
                            selectedBorderColor = NeonCyan
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            if (allRecords.isNotEmpty()) {
                IconButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear All History",
                        tint = NeonRose,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Content List
        if (historyRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 90.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(BgCard)
                            .border(1.dp, BorderGlass, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No matching transfers found" else "No Transfer History",
                        color = TextPureWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "Try a different keyword or filter category." else "Transferred files will be logged here with instant 1-tap open & share actions.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(
                    items = historyRecords,
                    key = { it.id }
                ) { record ->
                    HistoryItemCard(
                        record = record,
                        onOpen = { viewModel.openFile(context, record) },
                        onShare = { viewModel.shareFile(context, record) },
                        onDelete = { viewModel.deleteHistoryRecord(record.id) }
                    )
                }
            }
        }
    }

    // Clear Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = BgCard,
            shape = RoundedCornerShape(18.dp),
            title = {
                Text(
                    text = "Clear Transfer History?",
                    color = TextPureWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "This will remove all transfer history records. Saved files on disk remain safe in Downloads/SpeedShare.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRose),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Clear All", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
