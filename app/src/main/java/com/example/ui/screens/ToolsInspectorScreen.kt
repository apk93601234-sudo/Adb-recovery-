package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.catalog.AdbCommandSpec
import com.example.data.catalog.CommandCatalog
import com.example.data.catalog.CommandCategory
import com.example.data.catalog.CommandExecutionType
import com.example.data.catalog.CommandRisk
import com.example.data.local.SavedPresetEntity
import com.example.ui.ConnectionState
import com.example.ui.RecoveryUiState
import com.example.ui.RecoveryViewModel
import com.example.ui.components.MonoCodeBlock
import com.example.ui.components.RiskBadge
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.RecoveryAmber
import com.example.ui.theme.TerminalEmerald
import com.example.ui.theme.TerminalMonoStyle

@Composable
fun ToolsInspectorScreen(
    state: RecoveryUiState,
    savedPresets: List<SavedPresetEntity>,
    viewModel: RecoveryViewModel,
    onNavigateToTerminal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isHe = state.isHebrew
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = { Text(if (isHe) "מאגר פקודות" else "Commands") },
                modifier = Modifier.testTag("tools_tab_commands"),
            )
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = { Text(if (isHe) "Recovery Patch (MTK)" else "Recovery Patch (MTK)") },
                modifier = Modifier.testTag("tools_tab_patcher"),
            )
            FilterChip(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                label = { Text(if (isHe) "חבילות ו-Logcat" else "Apps & Logcat") },
                modifier = Modifier.testTag("tools_tab_apps"),
            )
            FilterChip(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                label = { Text(if (isHe) "בודק קבצים" else "File Inspector") },
                modifier = Modifier.testTag("tools_tab_inspector"),
            )
        }

        when (selectedTab) {
            0 -> CommandCatalogPane(
                isHebrew = isHe,
                savedPresets = savedPresets,
                onRunSpec = { spec ->
                    viewModel.runCatalogCommand(spec)
                    if (spec.execType != CommandExecutionType.ADB_REBOOT_TARGET) {
                        onNavigateToTerminal()
                    }
                },
                onRunRaw = { raw ->
                    viewModel.executeShellCommand(raw)
                    onNavigateToTerminal()
                },
                onSavePreset = { spec ->
                    viewModel.savePreset(
                        title = if (isHe) spec.titleHe else spec.titleEn,
                        command = spec.shellCommand.ifBlank { spec.command },
                        category = spec.category.name,
                        risk = spec.risk.name,
                    )
                },
                onDeletePreset = { viewModel.deletePreset(it) },
                modifier = Modifier.weight(1f),
            )
            1 -> RecoveryPatcherPane(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
            )
            2 -> PackagesAndLogcatPane(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
            )
            else -> ImageInspectorPane(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CommandCatalogPane(
    isHebrew: Boolean,
    savedPresets: List<SavedPresetEntity>,
    onRunSpec: (AdbCommandSpec) -> Unit,
    onRunRaw: (String) -> Unit,
    onSavePreset: (AdbCommandSpec) -> Unit,
    onDeletePreset: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<CommandCategory?>(null) }
    var confirmAdvancedSpec by remember { mutableStateOf<AdbCommandSpec?>(null) }

    confirmAdvancedSpec?.let { spec ->
        AlertDialog(
            onDismissRequest = { confirmAdvancedSpec = null },
            title = { Text(if (isHebrew) "אישור פקודה מתקדמת" else "Confirm Advanced Command") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isHebrew) spec.descriptionHe else spec.descriptionEn)
                    MonoCodeBlock(code = spec.command, isHebrew = isHebrew)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRunSpec(spec)
                        confirmAdvancedSpec = null
                    }
                ) {
                    Text(if (isHebrew) "הרץ כעת" else "Execute")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAdvancedSpec = null }) {
                    Text(if (isHebrew) "ביטול" else "Cancel")
                }
            },
        )
    }

    val filtered = remember(search, selectedCategory) {
        CommandCatalog.allCommands.filter { spec ->
            val matchesCat = selectedCategory == null || spec.category == selectedCategory
            val q = search.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                spec.command.lowercase().contains(q) ||
                spec.titleHe.lowercase().contains(q) ||
                spec.titleEn.lowercase().contains(q) ||
                spec.descriptionHe.lowercase().contains(q)
            matchesCat && matchesQuery
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = {
                    Text(if (isHebrew) "חפש פקודות Recovery, Fastboot, Shell..." else "Search ADB, Recovery, Fastboot...")
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("catalog_search_input"),
            )
        }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text(if (isHebrew) "הכל (${CommandCatalog.allCommands.size})" else "All") },
                )
                CommandCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                        label = { Text(if (isHebrew) cat.titleHe else cat.titleEn) },
                    )
                }
            }
        }

        if (savedPresets.isNotEmpty() && search.isBlank() && selectedCategory == null) {
            item {
                Text(
                    text = if (isHebrew) "פקודות שמורות במועדפים (${savedPresets.size})" else "Saved Favorites (${savedPresets.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = PhosphorCyan,
                )
            }
            items(savedPresets, key = { "preset_${it.id}" }) { preset ->
                ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(preset.title, style = MaterialTheme.typography.titleSmall)
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(preset.command, style = TerminalMonoStyle, color = PhosphorCyan)
                            }
                        }
                        IconButton(onClick = { onRunRaw(preset.command) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = TerminalEmerald)
                        }
                        IconButton(onClick = { onDeletePreset(preset.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        items(filtered, key = { it.id }) { spec ->
            val isSaved = savedPresets.any { it.command == (spec.shellCommand.ifBlank { spec.command }) }
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isHebrew) spec.titleHe else spec.titleEn,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        RiskBadge(risk = spec.risk, isHebrew = isHebrew)
                    }

                    MonoCodeBlock(code = spec.command, isHebrew = isHebrew)

                    Text(
                        text = if (isHebrew) spec.descriptionHe else spec.descriptionEn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { if (!isSaved) onSavePreset(spec) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isHebrew) "שמור למועדפים" else "Save favorite",
                                tint = if (isSaved) RecoveryAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (spec.execType != CommandExecutionType.REFERENCE_ONLY) {
                            Button(
                                onClick = {
                                    if (spec.risk == CommandRisk.SAFE) onRunSpec(spec)
                                    else confirmAdvancedSpec = spec
                                },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isHebrew) "הפעל" else "Run")
                            }
                        } else {
                            Text(
                                text = if (isHebrew) "תחביר Fastboot לעיון" else "Reference Syntax",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackagesAndLogcatPane(
    state: RecoveryUiState,
    viewModel: RecoveryViewModel,
    modifier: Modifier = Modifier,
) {
    val isHe = state.isHebrew
    var subMode by remember { mutableIntStateOf(0) } // 0 = Packages, 1 = Logcat
    var pkgFilter by remember { mutableStateOf("") }
    var logPriority by remember { mutableStateOf("W") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = subMode == 0,
                onClick = { subMode = 0 },
                leadingIcon = { Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text(if (isHe) "מנהל חבילות" else "Package Manager") },
            )
            FilterChip(
                selected = subMode == 1,
                onClick = { subMode = 1 },
                leadingIcon = { Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Live Logcat") },
            )
        }

        if (subMode == 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.loadPackages("-3") },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(if (isHe) "אפליקציות משתמש" else "3rd-Party")
                }
                OutlinedButton(
                    onClick = { viewModel.loadPackages("-s") },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(if (isHe) "מערכת" else "System")
                }
                OutlinedButton(
                    onClick = { viewModel.loadPackages("-d") },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(if (isHe) "מבוטלות" else "Disabled")
                }
            }

            OutlinedTextField(
                value = pkgFilter,
                onValueChange = { pkgFilter = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(if (isHe) "סנן לפי שם חבילה..." else "Filter package name...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.isLoadingPackages) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            val shownPkgs = remember(state.packages, pkgFilter) {
                val q = pkgFilter.trim().lowercase()
                if (q.isEmpty()) state.packages else state.packages.filter { it.lowercase().contains(q) }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (shownPkgs.isEmpty() && !state.isLoadingPackages) {
                    item {
                        Text(
                            text = if (isHe) "חבר מכשיר ב-ADB ולחץ על 'אפליקציות משתמש' או 'מערכת' לשליפת החבילות."
                            else "Connect via ADB and tap '3rd-Party' or 'System' to inspect installed packages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(shownPkgs, key = { it }) { pkg ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = pkg,
                                    style = TerminalMonoStyle,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            TextButton(onClick = { viewModel.executeShellCommand("pm path $pkg") }) {
                                Text(if (isHe) "נתיב" else "Path")
                            }
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("V", "D", "I", "W", "E").forEach { lvl ->
                        FilterChip(
                            selected = logPriority == lvl,
                            onClick = { logPriority = lvl },
                            label = { Text(lvl, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
                Button(
                    onClick = {
                        if (state.isLogcatStreaming) viewModel.stopLogcatStream()
                        else viewModel.startLogcatStream(logPriority)
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        if (state.isLogcatStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (state.isLogcatStreaming) (if (isHe) "עצור" else "Stop")
                        else (if (isHe) "התחל" else "Start")
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        if (state.logcatLines.isEmpty()) {
                            item {
                                Text(
                                    text = if (isHe) "לחץ 'התחל' להזרמת Logcat בזמן אמת מהמכשיר המחובר." else "Tap Start to stream live Logcat from connected target.",
                                    style = TerminalMonoStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(state.logcatLines) { line ->
                            val color = when {
                                line.contains(" E/") || line.contains(" F/") -> CriticalCrimson
                                line.contains(" W/") -> RecoveryAmber
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Text(line, style = TerminalMonoStyle, color = color)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageInspectorPane(
    state: RecoveryUiState,
    viewModel: RecoveryViewModel,
    modifier: Modifier = Modifier,
) {
    val isHe = state.isHebrew
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.inspectPickedFile(uri)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = if (isHe) "בודק קבצי Recovery / OTA / Boot IMG (ללא הרשאות אחסון)" else "Offline Recovery Image & ZIP Inspector",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (isHe) {
                            "בחר קובץ .img, .zip (עדכון OTA / TWRP), payload.bin או .apk לבדיקת SHA-256, MD5, כותרת ANDROID!, חבילות פנימיות והעברה למכשיר היעד."
                        } else {
                            "Pick any .img, .zip OTA/Recovery archive, payload.bin, or .apk to compute SHA-256/MD5, inspect headers, and push via ADB Sync."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("pick_recovery_file_button"),
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isHe) "בחר קובץ לבדיקה (.img / .zip / .apk)" else "Select File (.img / .zip / .apk)")
                    }
                }
            }
        }

        if (state.isInspectingFile) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(if (isHe) "מחשב SHA-256 ובודק מבנה קובץ..." else "Computing checksums & parsing headers...")
                }
            }
        }

        state.inspectedFile?.let { report ->
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(report.fileName, style = MaterialTheme.typography.titleMedium, color = PhosphorCyan)
                        Text(
                            text = report.detectedFormat,
                            style = MaterialTheme.typography.labelLarge,
                            color = TerminalEmerald,
                        )
                        Text(
                            text = if (isHe) "גודל: ${report.sizeBytes / 1024} KB (${report.sizeBytes} bytes)"
                            else "Size: ${report.sizeBytes / 1024} KB (${report.sizeBytes} bytes)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        MonoCodeBlock(code = "SHA-256: ${report.sha256}", isHebrew = isHe)
                        MonoCodeBlock(code = "MD5: ${report.md5}", isHebrew = isHe)

                        report.details.forEach { (k, v) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(v, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        if (report.zipEntriesPreview.isNotEmpty()) {
                            Text(
                                text = if (isHe) "תוכן ארכיון (דגימה):" else "Archive Entries Preview:",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    report.zipEntriesPreview.take(15).forEach { entry ->
                                        Text("• $entry", style = TerminalMonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        state.pushProgress?.let { prog ->
                            LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth())
                        }
                        state.pushStatus?.let { st ->
                            Text(st, style = MaterialTheme.typography.bodySmall, color = TerminalEmerald)
                        }

                        Button(
                            onClick = { viewModel.pushInspectedFileToTarget() },
                            enabled = state.connectionState == ConnectionState.CONNECTED_ADB && state.pushProgress == null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize(),
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isHe) "דחוף למכשיר (/sdcard/Download/${report.fileName})"
                                else "ADB Sync Push to /sdcard/Download/"
                            )
                        }
                    }
                }
            }
        }
    }
}
