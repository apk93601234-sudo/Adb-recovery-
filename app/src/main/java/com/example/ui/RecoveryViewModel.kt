package com.example.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.adb.AdbBannerInfo
import com.example.core.adb.AdbClient
import com.example.core.adb.AdbKeyManager
import com.example.core.adb.FastbootClient
import com.example.core.adb.HostDiagnostics
import com.example.core.adb.LanCandidate
import com.example.core.adb.LocalDiagnostics
import com.example.core.adb.LocalLanScanner
import com.example.core.adb.TcpAdbTransport
import com.example.core.adb.UsbInterfaceMode
import com.example.core.adb.UsbOtgDiscovery
import com.example.core.adb.UsbTargetDevice
import com.example.core.inspector.InspectedFileReport
import com.example.core.inspector.PackageImageInspector
import com.example.core.patcher.ParsedBootImageInfo
import com.example.core.patcher.PatchOptions
import com.example.core.patcher.PatchResultReport
import com.example.core.patcher.RecoveryPatchEngine
import com.example.data.catalog.AdbCommandSpec
import com.example.data.catalog.CommandExecutionType
import com.example.data.local.BatchScriptEntity
import com.example.data.local.CommandLogEntity
import com.example.data.local.RecoveryDatabase
import com.example.data.local.RecoveryRepository
import com.example.data.local.SavedDeviceEntity
import com.example.data.local.SavedPresetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    AWAITING_RSA_AUTH,
    CONNECTED_ADB,
    CONNECTED_FASTBOOT,
}

enum class TerminalLineType {
    COMMAND,
    OUTPUT,
    ERROR,
    SYSTEM,
    SUCCESS,
}

data class TerminalLine(
    val id: Long,
    val timestamp: String,
    val text: String,
    val type: TerminalLineType,
)

data class TargetTelemetry(
    val manufacturer: String = "",
    val model: String = "",
    val androidVersion: String = "",
    val securityPatch: String = "",
    val activeSlot: String = "",
    val verifiedBoot: String = "",
    val batteryLevel: String = "",
)

data class DeviceRecoveryProbe(
    val hardware: String,
    val platform: String,
    val isMediaTek: Boolean,
    val isAbDevice: Boolean,
    val activeSlot: String,
    val hasDedicatedRecoveryPartition: Boolean,
    val hasBootPartition: Boolean,
    val hasVendorBootPartition: Boolean,
    val hasRootSu: Boolean,
    val recommendationHe: String,
    val recommendationEn: String,
)

data class RecoveryUiState(
    val isHebrew: Boolean = true,
    val darkTheme: Boolean = true,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val connectedLabel: String = "",
    val transportBadge: String = "",
    val bannerInfo: AdbBannerInfo? = null,
    val targetTelemetry: TargetTelemetry? = null,
    val statusMessage: String? = null,
    val errorBanner: String? = null,
    val rsaFingerprint: String = "",
    val hostDiagnostics: HostDiagnostics? = null,
    val usbDevices: List<UsbTargetDevice> = emptyList(),
    val tcpHostInput: String = "192.168.1.100",
    val tcpPortInput: String = "5555",
    val isScanningLan: Boolean = false,
    val lanCandidates: List<LanCandidate> = emptyList(),
    val terminalLines: List<TerminalLine> = emptyList(),
    val terminalCommandInput: String = "",
    val suModeEnabled: Boolean = false,
    val isRunningCommand: Boolean = false,
    val activeScriptTitle: String? = null,
    val scriptProgress: Float = 0f,
    val packages: List<String> = emptyList(),
    val isLoadingPackages: Boolean = false,
    val logcatLines: List<String> = emptyList(),
    val isLogcatStreaming: Boolean = false,
    val inspectedFile: InspectedFileReport? = null,
    val isInspectingFile: Boolean = false,
    val pushProgress: Float? = null,
    val pushStatus: String? = null,
    val patchTargetInfo: ParsedBootImageInfo? = null,
    val patchOptions: PatchOptions = PatchOptions(),
    val patchResult: PatchResultReport? = null,
    val isAnalyzingPatchImage: Boolean = false,
    val isPatchingImage: Boolean = false,
    val deviceRecoveryProbe: DeviceRecoveryProbe? = null,
    val isProbingDevice: Boolean = false,
)

class RecoveryViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val keyManager = AdbKeyManager(appContext)
    private val database = RecoveryDatabase.getInstance(appContext)
    private val repository = RecoveryRepository(database.recoveryDao())

    private val _uiState = MutableStateFlow(RecoveryUiState())
    val uiState: StateFlow<RecoveryUiState> = _uiState.asStateFlow()

    val savedDevices: StateFlow<List<SavedDeviceEntity>> = repository.savedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val batchScripts: StateFlow<List<BatchScriptEntity>> = repository.batchScripts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val commandLogs: StateFlow<List<CommandLogEntity>> = repository.commandLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedPresets: StateFlow<List<SavedPresetEntity>> = repository.savedPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var activeAdbClient: AdbClient? = null
    private var activeFastbootClient: FastbootClient? = null
    private var logcatJob: Job? = null
    private var lineIdCounter = 1L
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbOtgDiscovery.ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    refreshUsbDevices()
                    if (granted) {
                        appendTerminal("USB OTG permission granted by user.", TerminalLineType.SUCCESS)
                    } else {
                        _uiState.update { it.copy(errorBanner = if (it.isHebrew) "הרשאת USB נדחתה" else "USB permission denied") }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    refreshUsbDevices()
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(UsbOtgDiscovery.ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(usbReceiver, filter)
        }

        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureBuiltInScriptsSeeded()
            val fp = keyManager.getSha256Fingerprint()
            val diag = LocalDiagnostics.readHostInfo(appContext)
            _uiState.update {
                it.copy(
                    rsaFingerprint = fp,
                    hostDiagnostics = diag,
                )
            }
        }
        refreshUsbDevices()
        appendTerminal(
            "Recovery ADB Offline Engine initialized (Pure Kotlin USB OTG + TCP + RSA-2048).",
            TerminalLineType.SYSTEM,
        )
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { appContext.unregisterReceiver(usbReceiver) }
        disconnect()
    }

    fun toggleLanguage() {
        _uiState.update { it.copy(isHebrew = !it.isHebrew) }
    }

    fun toggleTheme() {
        _uiState.update { it.copy(darkTheme = !it.darkTheme) }
    }

    fun clearErrorBanner() {
        _uiState.update { it.copy(errorBanner = null, statusMessage = null) }
    }

    fun updateTcpHost(host: String) {
        _uiState.update { it.copy(tcpHostInput = host) }
    }

    fun updateTcpPort(port: String) {
        _uiState.update { it.copy(tcpPortInput = port.filter { ch -> ch.isDigit() }.take(5)) }
    }

    fun updateTerminalInput(input: String) {
        _uiState.update { it.copy(terminalCommandInput = input) }
    }

    fun regenerateRsaKey() {
        viewModelScope.launch(Dispatchers.IO) {
            keyManager.regenerateKeyPair()
            val fp = keyManager.getSha256Fingerprint()
            _uiState.update {
                it.copy(
                    rsaFingerprint = fp,
                    statusMessage = if (it.isHebrew) "נוצר מפתח RSA-2048 חדש" else "Generated new RSA-2048 key",
                )
            }
            appendTerminal("Regenerated local RSA-2048 ADB key ($fp).", TerminalLineType.SYSTEM)
        }
    }

    fun refreshHostAndUsb() {
        refreshUsbDevices()
        viewModelScope.launch(Dispatchers.IO) {
            val diag = LocalDiagnostics.readHostInfo(appContext)
            _uiState.update { it.copy(hostDiagnostics = diag) }
        }
    }

    fun refreshUsbDevices() {
        val list = UsbOtgDiscovery.scanDevices(appContext)
        _uiState.update { it.copy(usbDevices = list) }
    }

    fun requestUsbPermission(device: UsbDevice) {
        UsbOtgDiscovery.requestPermission(appContext, device)
    }

    fun connectUsbTarget(target: UsbTargetDevice) {
        if (!target.hasPermission) {
            requestUsbPermission(target.device)
            return
        }
        disconnect()
        if (target.mode == UsbInterfaceMode.FASTBOOT) {
            viewModelScope.launch {
                val fb = withContext(Dispatchers.IO) { UsbOtgDiscovery.openFastbootClient(appContext, target) }
                if (fb == null) {
                    _uiState.update { it.copy(errorBanner = if (it.isHebrew) "פתיחת ממשק Fastboot נכשלה" else "Failed to open Fastboot USB interface") }
                    return@launch
                }
                activeFastbootClient = fb
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.CONNECTED_FASTBOOT,
                        connectedLabel = target.displayName,
                        transportBadge = "USB FASTBOOT",
                        errorBanner = null,
                    )
                }
                appendTerminal("Connected to Fastboot USB: ${target.displayName}", TerminalLineType.SUCCESS)
            }
            return
        }

        _uiState.update { it.copy(connectionState = ConnectionState.CONNECTING, errorBanner = null) }
        viewModelScope.launch {
            try {
                val transport = withContext(Dispatchers.IO) {
                    UsbOtgDiscovery.openAdbTransport(appContext, target)
                } ?: error("Could not claim USB OTG ADB interface")
                val client = AdbClient(transport, keyManager)
                val banner = client.connect(
                    onAwaitingAuth = {
                        _uiState.update { it.copy(connectionState = ConnectionState.AWAITING_RSA_AUTH) }
                    },
                )
                activeAdbClient = client
                val label = banner.model.ifBlank { target.displayName }
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.CONNECTED_ADB,
                        connectedLabel = label,
                        transportBadge = "USB OTG ADB",
                        bannerInfo = banner,
                        errorBanner = null,
                    )
                }
                appendTerminal("Connected via USB OTG ADB: $label (${banner.state})", TerminalLineType.SUCCESS)
                loadTargetTelemetry(client)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.DISCONNECTED,
                        errorBanner = e.message ?: "USB ADB connection failed",
                    )
                }
                appendTerminal("USB ADB error: ${e.message}", TerminalLineType.ERROR)
            }
        }
    }

    fun connectTcp(host: String = _uiState.value.tcpHostInput, portStr: String = _uiState.value.tcpPortInput) {
        val cleanHost = host.trim()
        val port = portStr.toIntOrNull() ?: 5555
        if (cleanHost.isBlank()) {
            _uiState.update { it.copy(errorBanner = if (it.isHebrew) "הזן כתובת IP תקינה" else "Enter a valid IP address") }
            return
        }
        disconnect()
        _uiState.update {
            it.copy(
                connectionState = ConnectionState.CONNECTING,
                tcpHostInput = cleanHost,
                tcpPortInput = port.toString(),
                errorBanner = null,
            )
        }
        viewModelScope.launch {
            try {
                val transport = withContext(Dispatchers.IO) { TcpAdbTransport(cleanHost, port) }
                val client = AdbClient(transport, keyManager)
                val banner = client.connect(
                    onAwaitingAuth = {
                        _uiState.update { it.copy(connectionState = ConnectionState.AWAITING_RSA_AUTH) }
                        appendTerminal("Waiting for 'Allow USB Debugging' RSA confirmation on $cleanHost...", TerminalLineType.SYSTEM)
                    },
                )
                activeAdbClient = client
                val label = banner.model.ifBlank { "$cleanHost:$port" }
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.CONNECTED_ADB,
                        connectedLabel = label,
                        transportBadge = "TCP $cleanHost:$port",
                        bannerInfo = banner,
                        errorBanner = null,
                    )
                }
                repository.saveDevice(
                    SavedDeviceEntity(
                        label = label,
                        host = cleanHost,
                        port = port,
                        model = banner.model,
                    )
                )
                appendTerminal("Connected via TCP ADB: $label ($cleanHost:$port)", TerminalLineType.SUCCESS)
                loadTargetTelemetry(client)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.DISCONNECTED,
                        errorBanner = e.message ?: "TCP connection failed",
                    )
                }
                appendTerminal("TCP ADB error ($cleanHost:$port): ${e.message}", TerminalLineType.ERROR)
            }
        }
    }

    fun scanLocalLanForAdb() {
        if (_uiState.value.isScanningLan) return
        _uiState.update { it.copy(isScanningLan = true, lanCandidates = emptyList(), errorBanner = null) }
        viewModelScope.launch {
            val found = LocalLanScanner.scanSubnetForAdb(5555)
            _uiState.update {
                it.copy(
                    isScanningLan = false,
                    lanCandidates = found,
                    statusMessage = if (found.isEmpty()) {
                        if (it.isHebrew) "לא נמצאו פורטי ADB פתוחים (5555) ברשת המקומית" else "No open port 5555 found on local LAN"
                    } else null,
                )
            }
        }
    }

    fun disconnect() {
        stopLogcatStream()
        activeAdbClient?.close()
        activeAdbClient = null
        activeFastbootClient?.close()
        activeFastbootClient = null
        _uiState.update {
            it.copy(
                connectionState = ConnectionState.DISCONNECTED,
                connectedLabel = "",
                transportBadge = "",
                bannerInfo = null,
                targetTelemetry = null,
                packages = emptyList(),
            )
        }
    }

    fun deleteSavedDevice(id: Int) {
        viewModelScope.launch { repository.deleteDevice(id) }
    }

    private fun loadTargetTelemetry(client: AdbClient) {
        viewModelScope.launch {
            runCatching {
                val raw = client.runShell(
                    "echo \"MFR=$(getprop ro.product.manufacturer)\"; " +
                        "echo \"MDL=$(getprop ro.product.model)\"; " +
                        "echo \"REL=$(getprop ro.build.version.release)\"; " +
                        "echo \"SEC=$(getprop ro.build.version.security_patch)\"; " +
                        "echo \"SLT=$(getprop ro.boot.slot_suffix)\"; " +
                        "echo \"AVB=$(getprop ro.boot.verifiedbootstate)\"; " +
                        "dumpsys battery | grep level | head -n 1"
                )
                val lines = raw.lines()
                fun getVal(prefix: String) = lines.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.trim().orEmpty()
                val bat = lines.firstOrNull { it.contains("level:") }?.substringAfter("level:")?.trim()?.let { "$it%" }.orEmpty()
                _uiState.update {
                    it.copy(
                        targetTelemetry = TargetTelemetry(
                            manufacturer = getVal("MFR="),
                            model = getVal("MDL="),
                            androidVersion = getVal("REL="),
                            securityPatch = getVal("SEC="),
                            activeSlot = getVal("SLT=").ifBlank { "Legacy / Non-A/B" },
                            verifiedBoot = getVal("AVB=").ifBlank { "unknown" },
                            batteryLevel = bat,
                        )
                    )
                }
            }
        }
    }

    fun rebootTarget(targetMode: String) {
        val adb = activeAdbClient
        val fb = activeFastbootClient
        if (adb == null && fb == null) {
            _uiState.update {
                it.copy(errorBanner = if (it.isHebrew) "יש להתחבר למכשיר יעד תחילה" else "Connect to a target device first")
            }
            return
        }
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            try {
                if (adb != null) {
                    adb.reboot(targetMode)
                    val label = if (targetMode.isBlank()) "system" else targetMode
                    appendTerminal("Sent ADB reboot:$label command.", TerminalLineType.SUCCESS)
                    repository.logCommand(_uiState.value.connectedLabel, "adb reboot $targetMode".trim(), "Reboot sent", true, System.currentTimeMillis() - start)
                    disconnect()
                } else if (fb != null) {
                    val cmd = when (targetMode) {
                        "recovery" -> "reboot-recovery"
                        "bootloader" -> "reboot-bootloader"
                        "fastboot" -> "reboot-fastboot"
                        else -> "reboot"
                    }
                    val res = fb.runCommand(cmd)
                    appendTerminal("Fastboot $cmd -> ${if (res.success) "OKAY" else "FAIL"} ${res.message}", if (res.success) TerminalLineType.SUCCESS else TerminalLineType.ERROR)
                    if (res.success) disconnect()
                }
            } catch (e: Exception) {
                appendTerminal("Reboot error: ${e.message}", TerminalLineType.ERROR)
                _uiState.update { it.copy(errorBanner = e.message) }
            }
        }
    }

    fun executeShellCommand(rawCommand: String = _uiState.value.terminalCommandInput) {
        val cmd = rawCommand.trim()
        if (cmd.isBlank()) return

        if (cmd == "clear") {
            clearTerminal()
            _uiState.update { it.copy(terminalCommandInput = "") }
            return
        }

        val adb = activeAdbClient
        val fb = activeFastbootClient
        if (adb == null && fb == null) {
            _uiState.update {
                it.copy(errorBanner = if (it.isHebrew) "אין מכשיר מחובר. חבר USB OTG או TCP כדי להריץ פקודה חיה." else "No device connected. Connect via USB OTG or TCP first.")
            }
            return
        }

        val useSu = _uiState.value.suModeEnabled &&
            fb == null &&
            !cmd.startsWith("su ") &&
            !cmd.startsWith("fastboot ")
        val effectiveDisplayCmd = if (useSu) "su -c \"$cmd\"" else cmd

        appendTerminal("$ $effectiveDisplayCmd", TerminalLineType.COMMAND)
        _uiState.update { it.copy(terminalCommandInput = "", isRunningCommand = true, errorBanner = null) }

        viewModelScope.launch {
            val start = System.currentTimeMillis()
            try {
                val output = if (fb != null) {
                    val res = fb.runCommand(cmd.removePrefix("fastboot ").trim())
                    buildString {
                        res.infoLines.forEach { appendLine(it) }
                        append(if (res.success) "OKAY ${res.message}" else "FAIL ${res.message}")
                    }.trim()
                } else {
                    val cleanShell = cmd.removePrefix("adb shell ").trim()
                    val shellToRun = if (useSu) {
                        val escaped = cleanShell.replace("'", "'\\''")
                        "su -c '$escaped'"
                    } else {
                        cleanShell
                    }
                    adb!!.runShell(shellToRun)
                }
                val elapsed = System.currentTimeMillis() - start
                appendTerminal(output.ifBlank { "(exit 0, no output)" }, TerminalLineType.OUTPUT)
                repository.logCommand(_uiState.value.connectedLabel, effectiveDisplayCmd, output, true, elapsed)
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - start
                val err = e.message ?: "Command failed"
                appendTerminal("ERR: $err", TerminalLineType.ERROR)
                repository.logCommand(_uiState.value.connectedLabel, effectiveDisplayCmd, err, false, elapsed)
            } finally {
                _uiState.update { it.copy(isRunningCommand = false) }
            }
        }
    }

    fun runCatalogCommand(spec: AdbCommandSpec) {
        when (spec.execType) {
            CommandExecutionType.ADB_SHELL -> executeShellCommand(spec.shellCommand)
            CommandExecutionType.ADB_REBOOT_TARGET -> rebootTarget(spec.shellCommand)
            CommandExecutionType.FASTBOOT_USB -> {
                val fb = activeFastbootClient
                if (fb == null) {
                    _uiState.update {
                        it.copy(errorBanner = if (it.isHebrew) "פקודה זו דורשת חיבור USB במצב Fastboot" else "This command requires a USB Fastboot connection")
                    }
                    return
                }
                executeShellCommand(spec.shellCommand)
            }
            CommandExecutionType.REFERENCE_ONLY -> {
                appendTerminal("Reference syntax: ${spec.command}", TerminalLineType.SYSTEM)
            }
        }
    }

    fun runBatchScript(script: BatchScriptEntity) {
        val adb = activeAdbClient
        if (adb == null) {
            _uiState.update {
                it.copy(errorBanner = if (it.isHebrew) "חבר מכשיר ב-ADB לפני הרצת סקריפט" else "Connect an ADB device before running a script")
            }
            return
        }
        val commands = script.commandsText.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
        if (commands.isEmpty()) return

        viewModelScope.launch {
            val title = if (_uiState.value.isHebrew) script.title else script.titleEn
            _uiState.update { it.copy(activeScriptTitle = title, scriptProgress = 0f, isRunningCommand = true) }
            appendTerminal("=== Running Script: $title (${commands.size} steps) ===", TerminalLineType.SYSTEM)

            for ((index, cmd) in commands.withIndex()) {
                val start = System.currentTimeMillis()
                appendTerminal("[${index + 1}/${commands.size}] $ $cmd", TerminalLineType.COMMAND)
                try {
                    val out = adb.runShell(cmd)
                    appendTerminal(out.ifBlank { "(ok)" }, TerminalLineType.OUTPUT)
                    repository.logCommand(_uiState.value.connectedLabel, cmd, out, true, System.currentTimeMillis() - start)
                } catch (e: Exception) {
                    val err = e.message ?: "Script error"
                    appendTerminal("ERR: $err", TerminalLineType.ERROR)
                    repository.logCommand(_uiState.value.connectedLabel, cmd, err, false, System.currentTimeMillis() - start)
                    if (script.stopOnError) break
                }
                _uiState.update { it.copy(scriptProgress = (index + 1).toFloat() / commands.size.toFloat()) }
                if (script.delayMs > 0) delay(script.delayMs)
            }
            appendTerminal("=== Script Finished ===", TerminalLineType.SUCCESS)
            _uiState.update { it.copy(activeScriptTitle = null, scriptProgress = 0f, isRunningCommand = false) }
        }
    }

    fun saveCustomScript(title: String, description: String, commandsText: String, stopOnError: Boolean) {
        if (title.isBlank() || commandsText.isBlank()) return
        viewModelScope.launch {
            repository.saveScript(
                BatchScriptEntity(
                    title = title.trim(),
                    titleEn = title.trim(),
                    description = description.trim().ifBlank { "סקריפט מותאם אישית" },
                    descriptionEn = description.trim().ifBlank { "Custom offline script" },
                    category = "Custom",
                    commandsText = commandsText.trim(),
                    stopOnError = stopOnError,
                    isBuiltIn = false,
                )
            )
        }
    }

    fun deleteCustomScript(id: Int) {
        viewModelScope.launch { repository.deleteCustomScript(id) }
    }

    fun savePreset(title: String, command: String, category: String, risk: String) {
        viewModelScope.launch {
            repository.savePreset(
                SavedPresetEntity(
                    title = title,
                    command = command,
                    category = category,
                    riskLevel = risk,
                )
            )
            _uiState.update {
                it.copy(statusMessage = if (it.isHebrew) "הפקודה נשמרה במועדפים" else "Command saved to favorites")
            }
        }
    }

    fun deletePreset(id: Int) {
        viewModelScope.launch { repository.deletePreset(id) }
    }

    fun loadPackages(filterFlag: String = "-3") {
        val adb = activeAdbClient
        if (adb == null) {
            _uiState.update { it.copy(errorBanner = if (it.isHebrew) "חבר מכשיר ADB כדי לטעון חבילות" else "Connect an ADB device to list packages") }
            return
        }
        _uiState.update { it.copy(isLoadingPackages = true) }
        viewModelScope.launch {
            try {
                val raw = adb.runShell("pm list packages $filterFlag")
                val pkgs = raw.lines()
                    .mapNotNull { line ->
                        if (line.startsWith("package:")) line.removePrefix("package:").trim() else null
                    }
                    .sorted()
                _uiState.update { it.copy(packages = pkgs, isLoadingPackages = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingPackages = false, errorBanner = e.message) }
            }
        }
    }

    fun startLogcatStream(priority: String = "I") {
        val adb = activeAdbClient
        if (adb == null) {
            _uiState.update { it.copy(errorBanner = if (it.isHebrew) "חבר מכשיר ADB כדי להזרים Logcat" else "Connect an ADB device to stream Logcat") }
            return
        }
        stopLogcatStream()
        _uiState.update { it.copy(isLogcatStreaming = true, logcatLines = emptyList()) }
        logcatJob = viewModelScope.launch {
            val buffer = StringBuilder()
            try {
                adb.streamShell(
                    command = "logcat -v time *:$priority",
                    isStopped = { !_uiState.value.isLogcatStreaming },
                    onTextChunk = { chunk ->
                        buffer.append(chunk)
                        val text = buffer.toString()
                        val lastNewline = text.lastIndexOf('\n')
                        if (lastNewline >= 0) {
                            val complete = text.substring(0, lastNewline).lines().filter { it.isNotBlank() }
                            buffer.delete(0, lastNewline + 1)
                            if (complete.isNotEmpty()) {
                                _uiState.update { state ->
                                    state.copy(logcatLines = (state.logcatLines + complete).takeLast(300))
                                }
                            }
                        }
                    },
                )
            } finally {
                _uiState.update { it.copy(isLogcatStreaming = false) }
            }
        }
    }

    fun stopLogcatStream() {
        _uiState.update { it.copy(isLogcatStreaming = false) }
        logcatJob?.cancel()
        logcatJob = null
    }

    fun inspectPickedFile(uri: Uri) {
        _uiState.update { it.copy(isInspectingFile = true, errorBanner = null, pushStatus = null) }
        viewModelScope.launch {
            try {
                val report = PackageImageInspector.inspectUri(appContext, uri)
                _uiState.update { it.copy(inspectedFile = report, isInspectingFile = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isInspectingFile = false, errorBanner = e.message) }
            }
        }
    }

    fun pushInspectedFileToTarget() {
        val adb = activeAdbClient
        val file = _uiState.value.inspectedFile
        if (adb == null || file == null) {
            _uiState.update { it.copy(errorBanner = if (it.isHebrew) "נדרש חיבור ADB וקובץ נבחר" else "Connect ADB and pick a file first") }
            return
        }
        val uri = Uri.parse(file.uriString)
        _uiState.update { it.copy(pushProgress = 0f, pushStatus = null) }
        viewModelScope.launch {
            try {
                val status = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        adb.pushFile(
                            input = stream,
                            totalBytes = file.sizeBytes,
                            remotePath = "/sdcard/Download/${file.fileName}",
                            onProgress = { pct -> _uiState.update { it.copy(pushProgress = pct) } },
                        )
                    } ?: error("Cannot reopen selected file")
                }
                _uiState.update { it.copy(pushProgress = null, pushStatus = "Pushed to /sdcard/Download/${file.fileName} ($status)") }
                appendTerminal("ADB Sync Push complete: /sdcard/Download/${file.fileName} ($status)", TerminalLineType.SUCCESS)
            } catch (e: Exception) {
                _uiState.update { it.copy(pushProgress = null, errorBanner = e.message) }
            }
        }
    }

    fun toggleSuMode() {
        _uiState.update { it.copy(suModeEnabled = !it.suModeEnabled) }
    }

    fun analyzeImageForPatch(uri: Uri) {
        _uiState.update {
            it.copy(
                isAnalyzingPatchImage = true,
                patchTargetInfo = null,
                patchResult = null,
                errorBanner = null,
            )
        }
        viewModelScope.launch {
            try {
                val info = RecoveryPatchEngine.analyzeImage(appContext, uri)
                _uiState.update {
                    it.copy(
                        patchTargetInfo = info,
                        isAnalyzingPatchImage = false,
                        patchOptions = it.patchOptions.copy(
                            enableMediaTekUsbGadgetFix = info.isMediaTekImage || it.patchOptions.enableMediaTekUsbGadgetFix,
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isAnalyzingPatchImage = false, errorBanner = e.message ?: "Failed to parse boot/recovery image")
                }
            }
        }
    }

    fun updatePatchOptions(transform: (PatchOptions) -> PatchOptions) {
        _uiState.update { it.copy(patchOptions = transform(it.patchOptions)) }
    }

    fun applyPatchAndSave(destUri: Uri, outputName: String) {
        val target = _uiState.value.patchTargetInfo ?: return
        _uiState.update { it.copy(isPatchingImage = true, patchResult = null, errorBanner = null) }
        viewModelScope.launch {
            try {
                val report = RecoveryPatchEngine.patchAndSaveImage(
                    context = appContext,
                    sourceUri = Uri.parse(target.sourceUri),
                    destUri = destUri,
                    options = _uiState.value.patchOptions,
                    outputFileName = outputName,
                )
                _uiState.update {
                    it.copy(
                        isPatchingImage = false,
                        patchResult = report,
                        statusMessage = if (it.isHebrew) "הפאטצ' הושלם ונשמר: $outputName" else "Patched image saved: $outputName",
                    )
                }
                appendTerminal("Recovery Patcher saved $outputName (${report.outputSizeBytes} bytes)", TerminalLineType.SUCCESS)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isPatchingImage = false, errorBanner = e.message ?: "Patching failed")
                }
            }
        }
    }

    fun probeConnectedRecoveryLayout() {
        val adb = activeAdbClient
        if (adb == null) {
            _uiState.update {
                it.copy(errorBanner = if (it.isHebrew) "חבר מכשיר ב-ADB כדי לסרוק את מיקום ה-Recovery" else "Connect via ADB to probe recovery layout")
            }
            return
        }
        _uiState.update { it.copy(isProbingDevice = true, errorBanner = null) }
        viewModelScope.launch {
            try {
                val raw = adb.runShell(
                    "echo \"HW=$(getprop ro.hardware)\"; " +
                        "echo \"PLT=$(getprop ro.board.platform)\"; " +
                        "echo \"AB=$(getprop ro.build.ab_update)\"; " +
                        "echo \"SLOT=$(getprop ro.boot.slot_suffix)\"; " +
                        "echo \"SU=$(which su 2>/dev/null)\"; " +
                        "echo \"PARTS=$(ls /dev/block/by-name 2>/dev/null || ls /dev/block/bootdevice/by-name 2>/dev/null | tr '\n' ' ')\""
                )
                val lines = raw.lines()
                fun v(p: String) = lines.firstOrNull { it.startsWith(p) }?.removePrefix(p)?.trim().orEmpty()
                val hw = v("HW=")
                val plt = v("PLT=")
                val ab = v("AB=").equals("true", ignoreCase = true)
                val slot = v("SLOT=").ifBlank { if (ab) "_a" else "none" }
                val suPath = v("SU=")
                val parts = v("PARTS=").lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

                val isMtk = hw.lowercase().contains("mt") ||
                    plt.lowercase().contains("mt") ||
                    hw.lowercase().contains("mediatek") ||
                    parts.any { it.contains("nvram") || it.contains("preloader") }

                val hasRec = parts.any { it == "recovery" || it == "recovery_a" || it == "recovery_b" }
                val hasBoot = parts.any { it == "boot" || it == "boot_a" || it == "boot_b" }
                val hasVendorBoot = parts.any { it == "vendor_boot" || it == "vendor_boot_a" || it == "vendor_boot_b" }

                val (recHe, recEn) = when {
                    !hasRec && hasBoot && hasVendorBoot ->
                        "מכשיר A/B מודרני: ה-Recovery יושב בתוך boot / vendor_boot. שלוף והחל פאטצ' על boot.img או vendor_boot.img." to
                            "Modern A/B layout: Recovery is inside boot / vendor_boot. Patch boot.img or vendor_boot.img."
                    !hasRec && hasBoot ->
                        "Recovery-in-Boot! במכשיר זה אין מחיצת recovery נפרדת — ה-Recovery נמצא בתוך boot.img." to
                            "Recovery-in-Boot! No separate recovery partition — recovery is inside boot.img."
                    hasRec ->
                        "קיימת מחיצת recovery נפרדת במכשיר (וגם boot זמין). פאטצ'ר האפליקציה מזהה את שתיהן." to
                            "Dedicated recovery partition detected on device."
                    else ->
                        "לא ניתן היה לקרוא את שמות הבלוקים ללא Root; הפאטצ'ר יזהה אוטומטית מקובץ ה-.img שתבחר." to
                            "Block list restricted without root; the offline patcher auto-detects layout from any .img file."
                }

                _uiState.update {
                    it.copy(
                        isProbingDevice = false,
                        deviceRecoveryProbe = DeviceRecoveryProbe(
                            hardware = hw.ifBlank { "unknown" },
                            platform = plt.ifBlank { "unknown" },
                            isMediaTek = isMtk,
                            isAbDevice = ab,
                            activeSlot = slot,
                            hasDedicatedRecoveryPartition = hasRec,
                            hasBootPartition = hasBoot,
                            hasVendorBootPartition = hasVendorBoot,
                            hasRootSu = suPath.isNotBlank(),
                            recommendationHe = recHe,
                            recommendationEn = recEn,
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProbingDevice = false, errorBanner = e.message) }
            }
        }
    }

    fun clearTerminal() {
        _uiState.update { it.copy(terminalLines = emptyList()) }
    }

    fun clearHistoryLogs() {
        viewModelScope.launch { repository.clearHistory() }
    }

    fun buildExportLogsText(): String {
        val logs = commandLogs.value
        return buildString {
            appendLine("=== Recovery ADB Offline Session Export ===")
            logs.forEach { entry ->
                appendLine("[${timeFmt.format(Date(entry.timestamp))}] (${entry.deviceLabel}) $ ${entry.command}")
                appendLine(entry.output)
                appendLine("---")
            }
        }
    }

    private fun appendTerminal(text: String, type: TerminalLineType) {
        val ts = timeFmt.format(Date())
        val line = TerminalLine(
            id = lineIdCounter++,
            timestamp = ts,
            text = text,
            type = type,
        )
        _uiState.update { state ->
            state.copy(terminalLines = (state.terminalLines + line).takeLast(350))
        }
    }
}
