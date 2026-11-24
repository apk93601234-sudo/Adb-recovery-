package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.local.BatchScriptEntity
import com.example.ui.RecoveryUiState
import com.example.ui.RecoveryViewModel
import com.example.ui.TerminalLineType
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.RecoveryAmber
import com.example.ui.theme.TerminalEmerald
import com.example.ui.theme.TerminalMonoStyle

@Composable
fun TerminalScriptsScreen(
    state: RecoveryUiState,
    scripts: List<BatchScriptEntity>,
    viewModel: RecoveryViewModel,
    modifier: Modifier = Modifier,
) {
    val isHe = state.isHebrew
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateScriptDialog by remember { mutableStateOf(false) }

    if (showCreateScriptDialog) {
        CreateScriptDialog(
            isHebrew = isHe,
            onDismiss = { showCreateScriptDialog = false },
            onSave = { title, desc, cmds, stopOnErr ->
                viewModel.saveCustomScript(title, desc, cmds, stopOnErr)
                showCreateScriptDialog = false
            },
        )
    }

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
                icon = { Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp)) },
            ) {
                Text(if (isHe) "טרמינל ADB חי" else "Live Terminal")
            }
            SegmentedButton(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp)) },
            ) {
                Text(if (isHe) "סקריפטים (${scripts.size})" else "Scripts (${scripts.size})")
            }
        }

        state.activeScriptTitle?.let { activeTitle ->
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = if (isHe) "מריץ: $activeTitle" else "Running: $activeTitle",
                    style = MaterialTheme.typography.labelMedium,
                    color = PhosphorCyan,
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { state.scriptProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (selectedTab == 0) {
            LiveTerminalPane(state = state, viewModel = viewModel, modifier = Modifier.weight(1f))
        } else {
            BatchScriptsPane(
                isHebrew = isHe,
                scripts = scripts,
                isRunning = state.isRunningCommand,
                onRunScript = {
                    viewModel.runBatchScript(it)
                    selectedTab = 0
                },
                onCreateNew = { showCreateScriptDialog = true },
                onDeleteScript = { viewModel.deleteCustomScript(it) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LiveTerminalPane(
    state: RecoveryUiState,
    viewModel: RecoveryViewModel,
    modifier: Modifier = Modifier,
) {
    val isHe = state.isHebrew
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    LaunchedEffect(state.terminalLines.size) {
        if (state.terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(state.terminalLines.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Quick commands + toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val quickCmds = listOf(
                    "id",
                    "getprop ro.hardware",
                    "ls /dev/block/by-name",
                    "getprop ro.product.model",
                    "df -h",
                    "dumpsys battery",
                    "pm list packages -3",
                    "cat /proc/partitions",
                    "logcat -d -t 30",
                )
                FilterChip(
                    selected = state.suModeEnabled,
                    onClick = { viewModel.toggleSuMode() },
                    leadingIcon = {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    label = { Text("ROOT (su)", style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CriticalCrimson.copy(alpha = 0.2f),
                        selectedLabelColor = CriticalCrimson,
                        selectedLeadingIconColor = CriticalCrimson,
                    ),
                    modifier = Modifier.testTag("terminal_su_toggle"),
                )
                quickCmds.forEach { q ->
                    AssistChip(
                        onClick = { viewModel.executeShellCommand(q) },
                        label = {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(q, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                    )
                }
            }
            IconButton(
                onClick = {
                    val all = state.terminalLines.joinToString("\n") { "[${it.timestamp}] ${it.text}" }
                    clipboard.setText(AnnotatedString(all))
                },
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = if (isHe) "העתק פלט" else "Copy output")
            }
            IconButton(
                onClick = { viewModel.clearTerminal() },
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = if (isHe) "נקה טרמינל" else "Clear terminal")
            }
        }

        // Terminal output console (always LTR for code/logs)
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                if (state.terminalLines.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isHe) "הטרמינל מוכן. הזן פקודת shell או הרץ סקריפט." else "Terminal ready. Enter a shell command.",
                            style = TerminalMonoStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.terminalLines, key = { it.id }) { line ->
                            val color = when (line.type) {
                                TerminalLineType.COMMAND -> PhosphorCyan
                                TerminalLineType.OUTPUT -> MaterialTheme.colorScheme.onSurface
                                TerminalLineType.ERROR -> CriticalCrimson
                                TerminalLineType.SYSTEM -> RecoveryAmber
                                TerminalLineType.SUCCESS -> TerminalEmerald
                            }
                            Text(
                                text = "[${line.timestamp}] ${line.text}",
                                style = TerminalMonoStyle,
                                color = color,
                            )
                        }
                    }
                }
            }
        }

        // Command input bar
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.terminalCommandInput,
                    onValueChange = { viewModel.updateTerminalInput(it) },
                    placeholder = {
                        Text(
                            if (isHe) "shell command (e.g. getprop, uname -a)..." else "Enter shell command...",
                            style = TerminalMonoStyle,
                        )
                    },
                    textStyle = TerminalMonoStyle,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { viewModel.executeShellCommand() }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("terminal_command_input"),
                )
                FilledIconButton(
                    onClick = { viewModel.executeShellCommand() },
                    enabled = !state.isRunningCommand && state.terminalCommandInput.isNotBlank(),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("terminal_send_button"),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (isHe) "הרץ פקודה" else "Run command",
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchScriptsPane(
    isHebrew: Boolean,
    scripts: List<BatchScriptEntity>,
    isRunning: Boolean,
    onRunScript: (BatchScriptEntity) -> Unit,
    onCreateNew: () -> Unit,
    onDeleteScript: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isHebrew) "סקריפטים מובנים ומותאמים (אופליין)" else "Offline Automation Scripts",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (isHebrew) "מחליף את סקריפטי ה-Python בהרצה מקומית ישירה" else "Native Kotlin multi-step ADB workflows",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onCreateNew,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("create_script_button"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isHebrew) "חדש" else "New")
                }
            }
        }

        items(scripts, key = { it.id }) { script ->
            val steps = script.commandsText.lines().filter { it.isNotBlank() }
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isHebrew) script.title else script.titleEn,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (isHebrew) script.description else script.descriptionEn,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!script.isBuiltIn) {
                            IconButton(onClick = { onDeleteScript(script.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = if (isHebrew) "מחק סקריפט" else "Delete script",
                                    tint = CriticalCrimson,
                                )
                            }
                        }
                    }

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.background)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            steps.take(4).forEach { line ->
                                Text(line, style = TerminalMonoStyle, color = MaterialTheme.colorScheme.primary)
                            }
                            if (steps.size > 4) {
                                Text(
                                    "+${steps.size - 4} more commands...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isHebrew) "${steps.size} שלבים • ${script.category}" else "${steps.size} steps • ${script.category}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { onRunScript(script) },
                            enabled = !isRunning,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isHebrew) "הרץ רצף" else "Run Sequence")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateScriptDialog(
    isHebrew: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Boolean) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var commands by remember { mutableStateOf("getprop ro.product.model\ndf -h") }
    var stopOnError by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isHebrew) "יצירת סקריפט ADB אופליין" else "Create Offline ADB Script") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isHebrew) "שם הסקריפט" else "Script Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text(if (isHebrew) "תיאור קצר" else "Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = commands,
                        onValueChange = { commands = it },
                        label = { Text(if (isHebrew) "פקודות (שורה לכל פקודה)" else "Commands (one per line)") },
                        textStyle = TerminalMonoStyle,
                        minLines = 4,
                        maxLines = 7,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = stopOnError, onCheckedChange = { stopOnError = it })
                    Text(
                        if (isHebrew) "עצור בשגיאה הראשונה" else "Stop on first error",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, desc, commands, stopOnError) },
                enabled = title.isNotBlank() && commands.isNotBlank(),
            ) {
                Text(if (isHebrew) "שמור מקומית" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isHebrew) "ביטול" else "Cancel")
            }
        },
    )
}
