package com.example.core.patcher

enum class RecoveryLayoutKind {
    DEDICATED_RECOVERY,
    RECOVERY_IN_BOOT,
    VENDOR_BOOT_RECOVERY,
    BOOT_WITHOUT_RECOVERY,
    UNKNOWN,
}

enum class RamdiskCompression {
    GZIP,
    LZ4_LEGACY,
    LZ4_FRAME,
    RAW_CPIO,
    NONE,
    UNSUPPORTED,
}

data class PatchOptions(
    val addRecoveryAdbMenuTrigger: Boolean = true,
    val autoAuthorizeAdb: Boolean = true,
    val enableRootShellInRecovery: Boolean = true,
    val enableMediaTekUsbGadgetFix: Boolean = true,
)

data class ParsedBootImageInfo(
    val sourceUri: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val headerMagic: String,
    val headerVersion: Int,
    val pageSize: Int,
    val kernelSize: Int,
    val ramdiskSize: Int,
    val cmdline: String,
    val osVersionText: String,
    val isMediaTekImage: Boolean,
    val mediaTekDetails: String,
    val ramdiskCompression: RamdiskCompression,
    val cpioEntryCount: Int,
    val recoveryLayout: RecoveryLayoutKind,
    val recoveryIndicators: List<String>,
    val hasAvbFooter: Boolean,
    val sha256Before: String,
)

data class PatchResultReport(
    val outputFileName: String,
    val outputSizeBytes: Long,
    val sha256After: String,
    val appliedChanges: List<String>,
    val warnings: List<String>,
)
