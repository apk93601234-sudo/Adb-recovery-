package com.example.data.local

import kotlinx.coroutines.flow.Flow

class RecoveryRepository(private val dao: RecoveryDao) {

    val savedDevices: Flow<List<SavedDeviceEntity>> = dao.observeSavedDevices()
    val batchScripts: Flow<List<BatchScriptEntity>> = dao.observeBatchScripts()
    val commandLogs: Flow<List<CommandLogEntity>> = dao.observeCommandLogs()
    val savedPresets: Flow<List<SavedPresetEntity>> = dao.observeSavedPresets()

    suspend fun ensureBuiltInScriptsSeeded() {
        if (dao.countBatchScripts() == 0) {
            dao.insertScripts(defaultOfflineScripts())
        }
    }

    suspend fun saveDevice(device: SavedDeviceEntity) = dao.insertDevice(device)
    suspend fun deleteDevice(id: Int) = dao.deleteDeviceById(id)

    suspend fun saveScript(script: BatchScriptEntity) = dao.insertScript(script)
    suspend fun updateScript(script: BatchScriptEntity) = dao.updateScript(script)
    suspend fun deleteCustomScript(id: Int) = dao.deleteCustomScriptById(id)

    suspend fun logCommand(
        deviceLabel: String,
        command: String,
        output: String,
        isSuccess: Boolean,
        durationMs: Long,
    ) {
        dao.insertCommandLog(
            CommandLogEntity(
                deviceLabel = deviceLabel,
                command = command,
                output = output.take(4000),
                isSuccess = isSuccess,
                durationMs = durationMs,
            )
        )
    }

    suspend fun clearHistory() = dao.clearCommandLogs()

    suspend fun savePreset(preset: SavedPresetEntity) = dao.insertPreset(preset)
    suspend fun deletePreset(id: Int) = dao.deletePresetById(id)

    private fun defaultOfflineScripts(): List<BatchScriptEntity> = listOf(
        BatchScriptEntity(
            title = "אבחון מערכת ומחיצות מלא",
            titleEn = "Full System & Partition Diagnostics",
            description = "בודק דגם, גרסת אנדרואיד, חריץ A/B פעיל, Bootloader, אחסון וטבלאות מחיצות.",
            descriptionEn = "Checks device model, Android build, active A/B slot, bootloader state, storage, and partitions.",
            category = "Diagnostics",
            commandsText = listOf(
                "getprop ro.product.manufacturer",
                "getprop ro.product.model",
                "getprop ro.build.version.release",
                "getprop ro.build.version.security_patch",
                "getprop ro.boot.slot_suffix",
                "getprop ro.boot.verifiedbootstate",
                "df -h /data /system /vendor 2>/dev/null || df -h",
                "cat /proc/partitions",
            ).joinToString("\n"),
            delayMs = 200L,
            stopOnError = false,
            isBuiltIn = true,
        ),
        BatchScriptEntity(
            title = "בדיקת מוכנות Recovery ו-Bootloader",
            titleEn = "Recovery & Bootloader Readiness Check",
            description = "בודק תמיכה ב-A/B Seamless Updates, מחיצות דינמיות (Super), ומצב OEM Unlock.",
            descriptionEn = "Inspects A/B seamless updates, dynamic super partitions, and OEM unlock properties.",
            category = "Recovery",
            commandsText = listOf(
                "getprop ro.build.ab_update",
                "getprop ro.boot.dynamic_partitions",
                "getprop ro.boot.flash.locked",
                "getprop sys.oem_unlock_allowed",
                "ls -la /dev/block/by-name 2>/dev/null || ls -la /dev/block/bootdevice/by-name 2>/dev/null",
            ).joinToString("\n"),
            delayMs = 200L,
            stopOnError = false,
            isBuiltIn = true,
        ),
        BatchScriptEntity(
            title = "צילום מצב סוללה, זיכרון וחום",
            titleEn = "Battery, Memory & Thermal Snapshot",
            description = "שולף נתוני טעינה, טמפרטורה, זיכרון RAM פנוי ועומס מעבד מקומי.",
            descriptionEn = "Reads battery charge stats, thermal sensors, free RAM, and CPU load.",
            category = "Hardware",
            commandsText = listOf(
                "dumpsys battery",
                "cat /proc/meminfo | head -n 6",
                "uptime",
                "dumpsys thermalservice | head -n 25",
            ).joinToString("\n"),
            delayMs = 250L,
            stopOnError = false,
            isBuiltIn = true,
        ),
        BatchScriptEntity(
            title = "לכידת קריסות אחרונות (Logcat + Tombstones)",
            titleEn = "Crash & Bootloop Diagnostics",
            description = "מושך שגיאות FATAL אחרונות מ-Logcat ובודק סיבת אתחול אחרונה.",
            descriptionEn = "Fetches recent FATAL errors from logcat and last boot reason.",
            category = "Debug",
            commandsText = listOf(
                "getprop sys.boot.reason.last",
                "getprop persist.sys.boot.reason",
                "logcat -d -b crash -t 40",
                "logcat -d *:E -t 30",
            ).joinToString("\n"),
            delayMs = 250L,
            stopOnError = false,
            isBuiltIn = true,
        ),
        BatchScriptEntity(
            title = "ספירת אפליקציות צד-שלישי ומצב הרשאות",
            titleEn = "Third-Party Packages & Permissions Audit",
            description = "מציג חבילות מותקנות על ידי המשתמש וחבילות מבוטלות במכשיר.",
            descriptionEn = "Lists user-installed packages and disabled system packages.",
            category = "Packages",
            commandsText = listOf(
                "pm list packages -3 | head -n 40",
                "pm list packages -d",
                "settings get global adb_enabled",
            ).joinToString("\n"),
            delayMs = 200L,
            stopOnError = false,
            isBuiltIn = true,
        ),
        BatchScriptEntity(
            title = "סריקת MediaTek ומבנה Recovery-in-Boot",
            titleEn = "MediaTek & Recovery-in-Boot Audit",
            description = "בודק אם המעבד הוא MediaTek, את זמינות Root (su), והאם ה-Recovery יושב במחיצה נפרדת או ב-boot.",
            descriptionEn = "Checks MediaTek SoC properties, root su availability, and separate recovery vs recovery-in-boot partitions.",
            category = "MediaTek",
            commandsText = listOf(
                "getprop ro.hardware",
                "getprop ro.board.platform",
                "getprop ro.build.ab_update",
                "getprop ro.boot.slot_suffix",
                "which su || echo 'su not in PATH'",
                "ls -la /dev/block/by-name 2>/dev/null | grep -E 'boot|recovery|vendor_boot|vbmeta|nvram'",
            ).joinToString("\n"),
            delayMs = 200L,
            stopOnError = false,
            isBuiltIn = true,
        ),
    )
}
