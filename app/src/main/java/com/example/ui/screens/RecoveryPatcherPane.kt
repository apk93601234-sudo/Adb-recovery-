package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.core.patcher.RecoveryLayoutKind
import com.example.ui.RecoveryUiState
import com.example.ui.RecoveryViewModel
import com.example.ui.components.MonoCodeBlock
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.RecoveryAmber
import com.example.ui.theme.TerminalEmerald

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecoveryPatcherPane(
    state: RecoveryUiState,
    viewModel: RecoveryViewModel,
    modifier: Modifier = Modifier,
) {
    val isHe = state.isHebrew

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.analyzeImageForPatch(uri)
    }

    val savePatchedLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { destUri ->
        if (destUri != null) {
            val base = state.patchTargetInfo?.fileName?.substringBeforeLast('.') ?: "recovery"
            viewModel.applyPatchAndSave(destUri, "${base}_adb_patched.img")
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Live Device Probe (MediaTek + Recovery-in-Boot detection)
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.DeveloperBoard, contentDescription = null, tint = RecoveryAmber)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isHe) "זיהוי מחיצות Recovery ו-MediaTek" else "Live MediaTek & Recovery Layout Probe",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        FilledTonalButton(
                            onClick = { viewModel.probeConnectedRecoveryLayout() },
                            enabled = !state.isProbingDevice,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("probe_recovery_layout_button"),
                        ) {
                            if (state.isProbingDevice) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(if (isHe) "סרוק מכשיר" else "Probe")
                        }
                    }

                    state.deviceRecoveryProbe?.let { probe ->
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ProbeChip("HW", probe.hardware, highlight = probe.isMediaTek)
                            ProbeChip("Platform", probe.platform, highlight = probe.isMediaTek)
                            ProbeChip(
                                "SoC",
                                if (probe.isMediaTek) "MediaTek (MTK)" else "Non-MTK",
                                highlight = probe.isMediaTek,
                            )
                            ProbeChip("A/B", if (probe.isAbDevice) "Yes (${probe.activeSlot})" else "No", highlight = false)
                            ProbeChip("Root", if (probe.hasRootSu) "su Ready" else "No su", highlight = probe.hasRootSu)
                        }
                        Surface(
                            color = PhosphorCyan.copy(alpha = 0.1f),
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, PhosphorCyan.copy(alpha = 0.35f)),
                        ) {
                            Text(
                                text = if (isHe) probe.recommendationHe else probe.recommendationEn,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    } ?: Text(
                        text = if (isHe) {
                            "חבר מכשיר ב-ADB כדי לזהות אוטומטית מעבד MediaTek והאם ה-Recovery נמצא במחיצה נפרדת או בתוך boot.img — או בחר ישירות קובץ .img למטה (אופליין מלא)."
                        } else {
                            "Probe a connected ADB device to detect MediaTek & Recovery-in-Boot layout, or pick an .img file directly below (100% offline)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 2. Pick boot.img / recovery.img / vendor_boot.img
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = PhosphorCyan)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isHe) "Recovery / Boot Image Patcher (אופליין מלא)" else "Offline Recovery / Boot Image Patcher",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        text = if (isHe) {
                            "מנתח ומפאטץ' קבצי recovery.img, boot.img (Recovery-in-Boot) ו-vendor_boot.img כולל תמיכה בכותרת MediaTek (MTK) ודחיסת GZIP / LZ4."
                        } else {
                            "Unpacks and repacks recovery.img, boot.img (Recovery-in-Boot), and vendor_boot.img with MediaTek MTK headers, GZIP, and LZ4 support."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { pickImageLauncher.launch(arrayOf("*/*")) },
                        enabled = !state.isAnalyzingPatchImage && !state.isPatchingImage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("patcher_pick_image_button"),
                    ) {
                        if (state.isAnalyzingPatchImage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isHe) "בחר קובץ boot.img / recovery.img / vendor_boot.img"
                            else "Select boot.img / recovery.img / vendor_boot.img"
                        )
                    }
                }
            }
        }

        // 3. Parsed Image Analysis + Patch Options
        state.patchTargetInfo?.let { info ->
            item {
                val (layoutColor, layoutTitle) = when (info.recoveryLayout) {
                    RecoveryLayoutKind.DEDICATED_RECOVERY ->
                        TerminalEmerald to (if (isHe) "זוהה: מחיצת Recovery ייעודית" else "Detected: Dedicated Recovery Image")
                    RecoveryLayoutKind.RECOVERY_IN_BOOT ->
                        PhosphorCyan to (if (isHe) "זוהה: Recovery בתוך מחיצת Boot!" else "Detected: Recovery-in-Boot (A/B Ramdisk)!")
                    RecoveryLayoutKind.VENDOR_BOOT_RECOVERY ->
                        RecoveryAmber to (if (isHe) "זוהה: Vendor Boot Ramdisk" else "Detected: Vendor Boot Ramdisk")
                    RecoveryLayoutKind.BOOT_WITHOUT_RECOVERY ->
                        CriticalCrimson to (if (isHe) "אזהרה: boot.img ללא רכיבי Recovery" else "Warning: Boot Image without Recovery Ramdisk")
                    RecoveryLayoutKind.UNKNOWN ->
                        RecoveryAmber to (if (isHe) "מבנה לא מזוהה" else "Unknown Layout")
                }

                ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(info.fileName, style = MaterialTheme.typography.titleMedium, color = PhosphorCyan)

                        Surface(
                            color = layoutColor.copy(alpha = 0.14f),
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, layoutColor.copy(alpha = 0.5f)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (info.recoveryLayout == RecoveryLayoutKind.BOOT_WITHOUT_RECOVERY) {
                                        Icons.Default.Warning
                                    } else {
                                        Icons.Default.CheckCircle
                                    },
                                    contentDescription = null,
                                    tint = layoutColor,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(layoutTitle, style = MaterialTheme.typography.titleSmall, color = layoutColor)
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ProbeChip("Header", "${info.headerMagic} v${info.headerVersion}", highlight = false)
                            ProbeChip("Ramdisk", info.ramdiskCompression.name, highlight = true)
                            ProbeChip("Entries", "${info.cpioEntryCount} CPIO", highlight = false)
                            ProbeChip(
                                "MTK",
                                if (info.isMediaTekImage) "MediaTek Detected" else "Standard",
                                highlight = info.isMediaTekImage,
                            )
                            if (info.hasAvbFooter) {
                                ProbeChip("AVB", "AVBf Signed", highlight = true)
                            }
                        }

                        if (info.isMediaTekImage) {
                            Text(
                                text = "MTK: ${info.mediaTekDetails}",
                                style = MaterialTheme.typography.labelSmall,
                                color = RecoveryAmber,
                            )
                        }

                        if (info.recoveryIndicators.isNotEmpty()) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = info.recoveryIndicators.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Patch Option Toggles
                        PatchSwitchRow(
                            title = if (isHe) "הוסף כפתור/טריגר 'ADB Debug' לתפריט Recovery" else "Add 'ADB Debug' Recovery Menu Trigger",
                            subtitle = if (isHe) {
                                "מזריק את /sbin/enable_adb_debug.sh, /init.recovery.adb_debug.rc ו-/res/recovery_adb_menu.conf"
                            } else {
                                "Injects /sbin/enable_adb_debug.sh, /init.recovery.adb_debug.rc & menu descriptor"
                            },
                            checked = state.patchOptions.addRecoveryAdbMenuTrigger,
                            onCheckedChange = { v -> viewModel.updatePatchOptions { it.copy(addRecoveryAdbMenuTrigger = v) } },
                        )

                        PatchSwitchRow(
                            title = if (isHe) "אישור ADB אוטומטי ללא חלונית RSA" else "Auto-Authorize All ADB Hosts (No RSA Prompt)",
                            subtitle = if (isHe) {
                                "מגדיר ro.adb.secure=0, ro.debuggable=1, sys.usb.config=adb ב-default.prop"
                            } else {
                                "Sets ro.adb.secure=0, ro.debuggable=1, sys.usb.config=adb in ramdisk props"
                            },
                            checked = state.patchOptions.autoAuthorizeAdb,
                            onCheckedChange = { v -> viewModel.updatePatchOptions { it.copy(autoAuthorizeAdb = v) } },
                        )

                        PatchSwitchRow(
                            title = if (isHe) "הרשאת Root מלאה ל-ADB Shell ב-Recovery" else "Enable Root ADB Shell (service.adb.root=1)",
                            subtitle = if (isHe) "מאפשר הרצת פקודות Root עמוקות ישירות דרך ADB ב-Recovery" else "Enables uid=0 root shell in recovery adbd",
                            checked = state.patchOptions.enableRootShellInRecovery,
                            onCheckedChange = { v -> viewModel.updatePatchOptions { it.copy(enableRootShellInRecovery = v) } },
                        )

                        PatchSwitchRow(
                            title = if (isHe) "תמיכת USB Gadget למכשירי MediaTek (MTK)" else "MediaTek (MTK) USB UDC Gadget Fix",
                            subtitle = if (isHe) "מאתר אוטומטית בקר /sys/class/udc/* (musb-hdrc) ומגדיר sys.usb.ffs.ready=1" else "Auto-configures /sys/class/udc/* (musb-hdrc) and sys.usb.ffs.ready=1",
                            checked = state.patchOptions.enableMediaTekUsbGadgetFix,
                            onCheckedChange = { v -> viewModel.updatePatchOptions { it.copy(enableMediaTekUsbGadgetFix = v) } },
                        )

                        Button(
                            onClick = {
                                val base = info.fileName.substringBeforeLast('.')
                                savePatchedLauncher.launch("${base}_adb_patched.img")
                            },
                            enabled = !state.isPatchingImage && info.cpioEntryCount > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("apply_patch_save_button"),
                        ) {
                            if (state.isPatchingImage) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (isHe) "בנה ושמור קובץ .img מופאטץ'" else "Build & Save Patched .img")
                        }
                    }
                }
            }
        }

        // 4. Patch Result Report
        state.patchResult?.let { res ->
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = TerminalEmerald)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isHe) "הפאטצ' הושלם בהצלחה!" else "Image Patched Successfully!",
                                style = MaterialTheme.typography.titleMedium,
                                color = TerminalEmerald,
                            )
                        }
                        MonoCodeBlock(code = "SHA-256: ${res.sha256After}", isHebrew = isHe)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            res.appliedChanges.forEach { change ->
                                Text("✓ $change", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                            res.warnings.forEach { warn ->
                                Text("⚠ $warn", style = MaterialTheme.typography.bodySmall, color = RecoveryAmber)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProbeChip(label: String, value: String, highlight: Boolean) {
    val accent = if (highlight) RecoveryAmber else MaterialTheme.colorScheme.outlineVariant
    Surface(
        color = if (highlight) RecoveryAmber.copy(alpha = 0.12f) else MaterialTheme.colorScheme.background,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, accent),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("$label:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (highlight) RecoveryAmber else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun PatchSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
