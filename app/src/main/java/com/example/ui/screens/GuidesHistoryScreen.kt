package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.catalog.RecoveryGuidesCatalog
import com.example.data.local.CommandLogEntity
import com.example.ui.RecoveryUiState
import com.example.ui.RecoveryViewModel
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.RecoveryAmber
import com.example.ui.theme.TerminalEmerald
import com.example.ui.theme.TerminalMonoStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GuidesHistoryScreen(
    state: RecoveryUiState,
    logs: List<CommandLogEntity>,
    viewModel: RecoveryViewModel,
    modifier: Modifier = Modifier,
) {
    val isHe = state.isHebrew
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            SegmentedButton(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp)) },
            ) {
                Text(if (isHe) "מדריכי Recovery" else "Recovery Guides")
            }
            SegmentedButton(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
            ) {
                Text(if (isHe) "היסטוריה (${logs.size})" else "History (${logs.size})")
            }
        }

        if (selectedTab == 0) {
            OfflineGuidesPane(isHebrew = isHe, modifier = Modifier.weight(1f))
        } else {
            CommandHistoryPane(
                isHebrew = isHe,
                logs = logs,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OfflineGuidesPane(
    isHebrew: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = if (isHebrew) "קיצורי מקשים לכניסה ל-Recovery ו-Bootloader" else "Hardware Key Combos (100% Offline)",
                style = MaterialTheme.typography.titleMedium,
                color = PhosphorCyan,
            )
        }

        items(RecoveryGuidesCatalog.oemGuides, key = { it.id }) { oem ->
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = PhosphorCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(oem.brand, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = if (isHebrew) "Recovery: ${oem.recoveryComboHe}" else "Recovery: ${oem.recoveryComboEn}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = if (isHebrew) "Bootloader: ${oem.bootloaderComboHe}" else "Bootloader: ${oem.bootloaderComboEn}",
                        style = MaterialTheme.typography.bodySmall,
                        color = RecoveryAmber,
                    )
                    Text(
                        text = if (isHebrew) oem.notesHe else oem.notesEn,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isHebrew) "פתרון תקלות ADB ו-Recovery" else "Offline Troubleshooting Solver",
                style = MaterialTheme.typography.titleMedium,
                color = RecoveryAmber,
            )
        }

        items(RecoveryGuidesCatalog.troubleshooting, key = { it.id }) { item ->
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BuildCircle, contentDescription = null, tint = RecoveryAmber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(item.errorTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = if (isHebrew) "סיבה: ${item.causeHe}" else "Cause: ${item.causeEn}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (isHebrew) "פתרון: ${item.fixHe}" else "Fix: ${item.fixEn}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TerminalEmerald,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandHistoryPane(
    isHebrew: Boolean,
    logs: List<CommandLogEntity>,
    viewModel: RecoveryViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dateFmt = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.US) }
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(if (isHebrew) "מחיקת היסטוריה" else "Clear History") },
            text = { Text(if (isHebrew) "למחוק את כל לוגי ההרצה השמורים מקומית?" else "Delete all locally saved execution logs?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistoryLogs()
                        showClearDialog = false
                    }
                ) {
                    Text(if (isHebrew) "מחק הכל" else "Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(if (isHebrew) "ביטול" else "Cancel")
                }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val text = viewModel.buildExportLogsText()
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Recovery ADB Offline Logs")
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, if (isHebrew) "ייצוא לוגים" else "Export Logs"))
                },
                enabled = logs.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("export_logs_button"),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isHebrew) "ייצוא לוגים" else "Export")
            }
            OutlinedButton(
                onClick = { showClearDialog = true },
                enabled = logs.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("clear_history_button"),
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isHebrew) "נקה היסטוריה" else "Clear")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (logs.isEmpty()) {
                item {
                    Text(
                        text = if (isHebrew) "עדיין לא הורצו פקודות בהיסטוריה המקומית." else "No executed commands recorded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(logs, key = { it.id }) { entry ->
                ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (entry.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (entry.isSuccess) TerminalEmerald else CriticalCrimson,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "${entry.deviceLabel} • ${entry.durationMs}ms",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            Text(
                                text = dateFmt.format(Date(entry.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("$ ${entry.command}", style = TerminalMonoStyle, color = PhosphorCyan)
                                    if (entry.output.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = entry.output.take(450),
                                            style = TerminalMonoStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
