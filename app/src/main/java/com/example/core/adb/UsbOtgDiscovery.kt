package com.example.core.adb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build

enum class UsbInterfaceMode {
    ADB,
    FASTBOOT,
}

data class UsbTargetDevice(
    val device: UsbDevice,
    val displayName: String,
    val vendorIdHex: String,
    val productIdHex: String,
    val serialNumber: String?,
    val mode: UsbInterfaceMode,
    val hasPermission: Boolean,
    val interfaceIndex: Int,
)

object UsbOtgDiscovery {
    const val ACTION_USB_PERMISSION = "com.aistudio.recoveryadb.USB_PERMISSION"

    private const val ADB_CLASS = 0xFF
    private const val ADB_SUBCLASS = 0x42
    private const val ADB_PROTOCOL = 0x01
    private const val FASTBOOT_PROTOCOL = 0x03

    fun scanDevices(context: Context): List<UsbTargetDevice> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        val results = mutableListOf<UsbTargetDevice>()

        for (device in usbManager.deviceList.values) {
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                val mode = when {
                    intf.interfaceClass == ADB_CLASS &&
                        intf.interfaceSubclass == ADB_SUBCLASS &&
                        intf.interfaceProtocol == ADB_PROTOCOL -> UsbInterfaceMode.ADB
                    intf.interfaceClass == ADB_CLASS &&
                        intf.interfaceSubclass == ADB_SUBCLASS &&
                        intf.interfaceProtocol == FASTBOOT_PROTOCOL -> UsbInterfaceMode.FASTBOOT
                    else -> null
                } ?: continue

                val hasPerm = usbManager.hasPermission(device)
                val name = buildString {
                    append(device.manufacturerName?.takeIf { it.isNotBlank() } ?: "Android USB")
                    device.productName?.takeIf { it.isNotBlank() }?.let { append(" $it") }
                }
                val serial = if (hasPerm) runCatching { device.serialNumber }.getOrNull() else null
                results += UsbTargetDevice(
                    device = device,
                    displayName = name,
                    vendorIdHex = "0x%04X".format(device.vendorId),
                    productIdHex = "0x%04X".format(device.productId),
                    serialNumber = serial,
                    mode = mode,
                    hasPermission = hasPerm,
                    interfaceIndex = i,
                )
            }
        }
        return results
    }

    fun requestPermission(context: Context, device: UsbDevice) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val intent = PendingIntent.getBroadcast(
            context,
            device.deviceId,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
            flags,
        )
        usbManager.requestPermission(device, intent)
    }

    fun openAdbTransport(context: Context, target: UsbTargetDevice): UsbAdbTransport? {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
        if (!usbManager.hasPermission(target.device)) return null
        val intf = target.device.getInterface(target.interfaceIndex)
        val (epIn, epOut) = findBulkEndpoints(intf) ?: return null
        val conn = usbManager.openDevice(target.device) ?: return null
        if (!conn.claimInterface(intf, true)) {
            conn.close()
            return null
        }
        return UsbAdbTransport(
            connection = conn,
            endpointIn = epIn,
            endpointOut = epOut,
            description = "USB OTG ${target.displayName} (${target.vendorIdHex}:${target.productIdHex})",
        )
    }

    fun openFastbootClient(context: Context, target: UsbTargetDevice): FastbootClient? {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
        if (!usbManager.hasPermission(target.device)) return null
        val intf = target.device.getInterface(target.interfaceIndex)
        val (epIn, epOut) = findBulkEndpoints(intf) ?: return null
        val conn = usbManager.openDevice(target.device) ?: return null
        if (!conn.claimInterface(intf, true)) {
            conn.close()
            return null
        }
        return FastbootClient(conn, epIn, epOut)
    }

    private fun findBulkEndpoints(intf: UsbInterface): Pair<UsbEndpoint, UsbEndpoint>? {
        var epIn: UsbEndpoint? = null
        var epOut: UsbEndpoint? = null
        for (i in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) epIn = ep
                else if (ep.direction == UsbConstants.USB_DIR_OUT) epOut = ep
            }
        }
        return if (epIn != null && epOut != null) epIn to epOut else null
    }
}
