package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.core.adb.UsbInterfaceMode
import com.example.data.local.SavedDeviceEntity
import com.example.ui.ConnectionState
import com.example.ui.RecoveryUiState
import com.example.ui.RecoveryViewModel
import com.example.ui.components.HeroConsoleHeader
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.RecoveryAmber
import com.example.ui.theme.TerminalEmerald
import com.example.ui.theme.TerminalMonoStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectionScreen(
    state: RecoveryUiState,
    savedDevices: List<SavedDeviceEntity>,
    viewModel: RecoveryViewModel,
    modifier: Modifier = Modifier,
) {
    val isHe = state.isHebrew
    var pendingRebootAction by remember { mutableStateOf<Pair<String, String>?>(null) }

    pendingRebootAction?.let { (targetMode, label) ->
        AlertDialog(
            onDismissRequest = { pendingRebootAction = null },
            title = { Text(if (isHe) "אישור פעולת אתחול" else "Confirm Reboot Action") },
            text = {
                Text(
                    if (isHe) "האם לאתחל את מכשיר היעד ($label)?"
                    else "Reboot connected target device ($label)?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.rebootTarget(targetMode)
                        pendingRebootAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RecoveryAmber, contentColor = Color.Black),
                ) {
                    Text(if (isHe) "אשר והפעל" else "Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRebootAction = null }) {
                    Text(if (isHe) "ביטול" else "Cancel")
                }
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HeroConsoleHeader(
                isHebrew = isHe,
                connectionState = state.connectionState,
                connectedLabel = state.connectedLabel,
                transportBadge = state.transportBadge,
                onDisconnect = { viewModel.disconnect() },
            )
        }

        // Connected Target Telemetry & Recovery Power Matrix
        item {
            AnimatedVisibility(
                visible = state.connectionState == ConnectionState.CONNECTED_ADB ||
                    state.connectionState == ConnectionState.CONNECTED_FASTBOOT
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    state.targetTelemetry?.let { tel ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = TerminalEmerald)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isHe) "טלמטריית מכשיר היעד" else "Target Device Telemetry",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    TelemetryChip("Model", "${tel.manufacturer} ${tel.model}".trim())
                                    TelemetryChip("Android", tel.androidVersion.ifBlank { "-" })
                                    TelemetryChip("Security", tel.securityPatch.ifBlank { "-" })
                                    TelemetryChip("Slot", tel.activeSlot)
                                    TelemetryChip("AVB", tel.verifiedBoot)
                                    if (tel.batteryLevel.isNotBlank()) {
                                        TelemetryChip("Battery", tel.batteryLevel)
                                    }
                                }
                            }
                        }
                    }

                    // Power & Recovery Actions Matrix
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = RecoveryAmber)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isHe) "מעבר מצבי אתחול ו-Recovery" else "Recovery & Boot Mode Actions",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PowerActionButton(
                                    label = if (isHe) "אתחול רגיל" else "Reboot OS",
                                    icon = Icons.Default.RestartAlt,
                                    tint = PhosphorCyan,
                                    tag = "btn_reboot_system",
                                    onClick = { pendingRebootAction = "" to (if (isHe) "אתחול רגיל" else "System") },
                                )
                                PowerActionButton(
                                    label = "Recovery",
                                    icon = Icons.Default.SettingsBackupRestore,
                                    tint = TerminalEmerald,
                                    tag = "btn_reboot_recovery",
                                    onClick = { pendingRebootAction = "recovery" to "Recovery" },
                                )
                                PowerActionButton(
                                    label = "Bootloader",
                                    icon = Icons.Default.Build,
                                    tint = RecoveryAmber,
                                    tag = "btn_reboot_bootloader",
                                    onClick = { pendingRebootAction = "bootloader" to "Bootloader" },
                                )
                                PowerActionButton(
                                    label = "Fastbootd",
                                    icon = Icons.Default.Memory,
                                    tint = RecoveryAmber,
                                    tag = "btn_reboot_fastbootd",
                                    onClick = { pendingRebootAction = "fastboot" to "Fastbootd" },
                                )
                                PowerActionButton(
                                    label = "Sideload",
                                    icon = Icons.Default.SystemUpdateAlt,
                                    tint = PhosphorCyan,
                                    tag = "btn_reboot_sideload",
                                    onClick = { pendingRebootAction = "sideload" to "ADB Sideload" },
                                )
                                PowerActionButton(
                                    label = "EDL (9008)",
                                    icon = Icons.Default.DeveloperBoard,
                                    tint = CriticalCrimson,
                                    tag = "btn_reboot_edl",
                                    onClick = { pendingRebootAction = "edl" to "Qualcomm EDL" },
                                )
                            }
                        }
                    }
                }
            }
        }

        // USB OTG Host Card
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Usb, contentDescription = null, tint = PhosphorCyan)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isHe) "חיבור USB OTG ישיר (כבל)" else "Direct USB OTG Host",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        FilledTonalButton(
                            onClick = { viewModel.refreshUsbDevices() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("usb_scan_button"),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isHe) "סרוק USB" else "Scan USB")
                        }
                    }

                    if (state.usbDevices.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Cable,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = if (isHe) {
                                        "לא זוהה התקן ADB או Fastboot ב-USB OTG. חבר כבל OTG עם USB Debugging מופעל ולחץ סרוק."
                                    } else {
                                        "No USB OTG ADB/Fastboot target detected. Plug in an OTG cable with USB Debugging enabled."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        state.usbDevices.forEach { usb ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(usb.displayName, style = MaterialTheme.typography.titleSmall)
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = "${usb.mode.name} • ${usb.vendorIdHex}:${usb.productIdHex}" +
                                                    (usb.serialNumber?.let { " • $it" } ?: ""),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = { viewModel.connectUsbTarget(usb) },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Text(
                                            when {
                                                !usb.hasPermission -> if (isHe) "אשר USB" else "Grant"
                                                usb.mode == UsbInterfaceMode.FASTBOOT -> "Fastboot"
                                                else -> if (isHe) "התחבר" else "Connect"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Local TCP/IP ADB Connection & LAN Scanner
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = TerminalEmerald)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isHe) "חיבור ADB מקומי (Wi-Fi / LAN / Loopback)" else "Local TCP/IP ADB Connection",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = state.tcpHostInput,
                                onValueChange = { viewModel.updateTcpHost(it) },
                                label = { Text(if (isHe) "IP מקומי" else "Local IP") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(2.2f)
                                    .testTag("tcp_host_input"),
                            )
                            OutlinedTextField(
                                value = state.tcpPortInput,
                                onValueChange = { viewModel.updateTcpPort(it) },
                                label = { Text(if (isHe) "פורט" else "Port") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tcp_port_input"),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { viewModel.connectTcp() },
                            enabled = state.connectionState != ConnectionState.CONNECTING,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("tcp_connect_button"),
                        ) {
                            if (state.connectionState == ConnectionState.CONNECTING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (isHe) "התחבר ב-TCP" else "Connect TCP")
                        }

                        OutlinedButton(
                            onClick = { viewModel.scanLocalLanForAdb() },
                            enabled = !state.isScanningLan,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("lan_scan_button"),
                        ) {
                            if (state.isScanningLan) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Lan, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(if (isHe) "סרוק רשת" else "Scan LAN")
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AssistChip(
                            onClick = {
                                viewModel.updateTcpHost("127.0.0.1")
                                viewModel.updateTcpPort("5555")
                            },
                            label = { Text("127.0.0.1:5555", style = MaterialTheme.typography.labelMedium) },
                        )
                        state.lanCandidates.forEach { candidate ->
                            AssistChip(
                                onClick = {
                                    viewModel.connectTcp(candidate.host, candidate.port.toString())
                                },
                                label = {
                                    Text(
                                        "${candidate.host}:${candidate.port} (${candidate.latencyMs}ms)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TerminalEmerald,
                                    )
                                },
                            )
                        }
                    }

                    if (savedDevices.isNotEmpty()) {
                        Text(
                            text = if (isHe) "מכשירים שמורים (אופליין):" else "Saved Offline Targets:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        savedDevices.forEach { dev ->
                            Surface(
                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(dev.label, style = MaterialTheme.typography.titleSmall)
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                "${dev.host}:${dev.port}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    TextButton(onClick = { viewModel.connectTcp(dev.host, dev.port.toString()) }) {
                                        Text(if (isHe) "חבר" else "Connect")
                                    }
                                    IconButton(onClick = { viewModel.deleteSavedDevice(dev.id) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = if (isHe) "מחק מכשיר" else "Delete saved device",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Local Host Hardware & RSA Identity Card
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = RecoveryAmber)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isHe) "זהות RSA מקומית ונתוני מארח" else "Local RSA Key & Host Info",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        TextButton(onClick = { viewModel.regenerateRsaKey() }) {
                            Text(if (isHe) "חדש מפתח" else "New Key")
                        }
                    }

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = "RSA-2048 SHA-256: ${state.rsaFingerprint.ifBlank { "..." }}",
                            style = TerminalMonoStyle,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    state.hostDiagnostics?.let { host ->
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TelemetryChip("Host", "${host.manufacturer} ${host.model}")
                            TelemetryChip("Android", "${host.androidVersion} (API ${host.sdkInt})")
                            TelemetryChip(
                                "USB OTG",
                                if (host.usbHostSupported) "Supported" else "No USB Host",
                            )
                            TelemetryChip(
                                "Free Storage",
                                String.format(Locale.US, "%.1f / %.1f GB", host.freeStorageGb, host.totalStorageGb),
                            )
                            if (host.localIps.isNotEmpty()) {
                                TelemetryChip("Local IP", host.localIps.joinToString(", "))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryChip(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$label:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PowerActionButton(
    label: String,
    icon: ImageVector,
    tint: Color,
    tag: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag(tag),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.5f)),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface)
    }
}
