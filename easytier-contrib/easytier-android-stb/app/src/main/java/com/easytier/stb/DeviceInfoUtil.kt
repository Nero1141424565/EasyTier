package com.easytier.stb

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * 设备信息工具类
 * 用于读取设备的厂商编号和硬件序列号作为主机名
 */
object DeviceInfoUtil {

    private const val TAG = "DeviceInfoUtil"

    /**
     * 获取设备主机名（厂商编号 + 硬件序列号）
     */
    fun getDeviceHostname(context: Context): String {
        val manufacturer = getManufacturer()
        val serial = getSerialNumber(context)
        val hostname = "${manufacturer}_$serial"
        Log.i(TAG, "设备主机名: $hostname")
        return hostname
    }

    /**
     * 获取设备节点名称（与主机名相同，可以根据需要修改）
     */
    fun getNodeName(context: Context): String {
        return getDeviceHostname(context)
    }

    /**
     * 获取厂商编号
     */
    private fun getManufacturer(): String {
        val manufacturer = Build.MANUFACTURER
        Log.i(TAG, "厂商: $manufacturer")
        return sanitize(manufacturer)
    }

    /**
     * 获取硬件序列号
     * 尝试多种方式获取，确保兼容性
     */
    private fun getSerialNumber(context: Context): String {
        var serial = "unknown"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val buildSerial = Build.getSerial()
                if (!buildSerial.isNullOrEmpty() && buildSerial != "unknown") {
                    serial = buildSerial
                    Log.i(TAG, "通过 Build.getSerial() 获取序列号: $serial")
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "无法通过 Build.getSerial() 获取序列号，权限不足", e)
        } catch (e: Exception) {
            Log.w(TAG, "通过 Build.getSerial() 获取序列号失败", e)
        }

        if (serial == "unknown") {
            try {
                @Suppress("DEPRECATION")
                val serialField = Build.SERIAL
                if (!serialField.isNullOrEmpty() && serialField != "unknown") {
                    serial = serialField
                    Log.i(TAG, "通过 Build.SERIAL 获取序列号: $serial")
                }
            } catch (e: Exception) {
                Log.w(TAG, "通过 Build.SERIAL 获取序列号失败", e)
            }
        }

        if (serial == "unknown") {
            try {
                val androidId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
                if (!androidId.isNullOrEmpty()) {
                    serial = androidId
                    Log.i(TAG, "通过 ANDROID_ID 获取序列号: $serial")
                }
            } catch (e: Exception) {
                Log.w(TAG, "通过 ANDROID_ID 获取序列号失败", e)
            }
        }

        if (serial == "unknown") {
            try {
                val cpuSerial = getCpuSerial()
                if (!cpuSerial.isNullOrEmpty()) {
                    serial = cpuSerial
                    Log.i(TAG, "通过 CPU 信息获取序列号: $serial")
                }
            } catch (e: Exception) {
                Log.w(TAG, "通过 CPU 信息获取序列号失败", e)
            }
        }

        if (serial == "unknown") {
            val hardware = Build.HARDWARE
            val device = Build.DEVICE
            val fallback = "${hardware}_${device}_${System.currentTimeMillis()}"
            serial = fallback
            Log.i(TAG, "使用硬件信息作为备选序列号: $serial")
        }

        return sanitize(serial)
    }

    /**
     * 尝试从 /proc/cpuinfo 获取序列号
     */
    private fun getCpuSerial(): String? {
        return try {
            val process = Runtime.getRuntime().exec("cat /proc/cpuinfo")
            val reader = process.inputStream.bufferedReader()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line?.startsWith("Serial") == true ||
                    line?.startsWith("serial") == true ||
                    line?.contains("Serial") == true
                ) {
                    val parts = line?.split(":")
                    if (parts?.size == 2) {
                        return parts[1].trim()
                    }
                }
            }
            reader.close()
            null
        } catch (e: Exception) {
            Log.w(TAG, "读取 /proc/cpuinfo 失败", e)
            null
        }
    }

    /**
     * 清理字符串，移除非法字符
     * 主机名只能包含字母、数字、下划线和连字符
     */
    private fun sanitize(input: String): String {
        var result = input.trim()
        result = result.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        if (result.length > 50) {
            result = result.substring(0, 50)
        }
        if (result.isEmpty()) {
            result = "unknown"
        }
        return result
    }

    /**
     * 获取完整的设备信息，用于调试
     */
    fun getDeviceInfoDebug(context: Context): Map<String, String> {
        return mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "brand" to Build.BRAND,
            "model" to Build.MODEL,
            "device" to Build.DEVICE,
            "product" to Build.PRODUCT,
            "hardware" to Build.HARDWARE,
            "board" to Build.BOARD,
            "android_id" to (Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "null"),
            "hostname" to getDeviceHostname(context)
        )
    }
}
