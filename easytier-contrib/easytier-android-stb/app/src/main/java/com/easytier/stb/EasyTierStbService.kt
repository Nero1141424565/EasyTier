package com.easytier.stb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * EasyTier 机顶盒前台服务
 * 负责在后台静默运行 EasyTier 核心，并监控网络状态
 */
class EasyTierStbService : Service() {

    companion object {
        private const val TAG = "EasyTierStbService"
        const val CHANNEL_ID = "easytier_stb_channel"
        const val NOTIFICATION_ID = 1001
        const val MONITOR_INTERVAL = 5000L

        const val ACTION_START = "com.easytier.stb.START"
        const val ACTION_STOP = "com.easytier.stb.STOP"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var currentIpv4: String? = null
    private var currentProxyCidrs: List<String> = emptyList()

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                monitorNetworkStatus()
                handler.postDelayed(this, MONITOR_INTERVAL)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "EasyTier STB Service 创建")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.i(TAG, "收到启动命令")
                startEasyTier()
            }

            ACTION_STOP -> {
                Log.i(TAG, "收到停止命令")
                stopEasyTier()
                stopSelf()
            }

            else -> {
                Log.i(TAG, "服务启动，开始运行 EasyTier")
                startEasyTier()
            }
        }

        return START_STICKY
    }

    private fun startEasyTier() {
        if (isRunning) {
            Log.w(TAG, "EasyTier 已经在运行中")
            return
        }

        try {
            startForegroundNotification()

            val config = EasyTierConfig.generateConfig(this)
            val result = EasyTierJNI.runNetworkInstance(config)

            if (result == 0) {
                isRunning = true
                Log.i(TAG, "EasyTier 实例启动成功")
                handler.postDelayed(monitorRunnable, 2000)
            } else {
                Log.e(TAG, "EasyTier 实例启动失败: $result")
                val error = EasyTierJNI.getLastError()
                Log.e(TAG, "错误信息: $error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动 EasyTier 实例时发生异常", e)
        }
    }

    private fun stopEasyTier() {
        if (!isRunning) {
            Log.w(TAG, "EasyTier 未在运行")
            return
        }

        isRunning = false
        handler.removeCallbacks(monitorRunnable)

        try {
            stopVpnService()
            EasyTierJNI.stopAllInstances()
            Log.i(TAG, "EasyTier 实例已停止")

            currentIpv4 = null
            currentProxyCidrs = emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "停止 EasyTier 实例时发生异常", e)
        }
    }

    private fun monitorNetworkStatus() {
        try {
            val infosJson = EasyTierJNI.collectNetworkInfos(10)
            if (infosJson.isNullOrEmpty()) {
                Log.d(TAG, "未获取到网络信息")
                return
            }

            Log.d(TAG, "网络信息: $infosJson")

            val ipv4Pattern = """"virtual_ipv4"\s*:\s*\{\s*"address"\s*:\s*\{\s*"addr"\s*:\s*(-?\d+)""".toRegex()
            val matchResult = ipv4Pattern.find(infosJson)

            if (matchResult != null) {
                val addrInt = matchResult.groupValues[1].toInt()
                val ip = String.format(
                    "%d.%d.%d.%d",
                    (addrInt shr 24) and 0xFF,
                    (addrInt shr 16) and 0xFF,
                    (addrInt shr 8) and 0xFF,
                    addrInt and 0xFF
                )

                val networkLengthPattern = """"network_length"\s*:\s*(\d+)""".toRegex()
                val lengthMatch = networkLengthPattern.find(infosJson)
                val networkLength = lengthMatch?.groupValues?.get(1) ?: "24"

                val newIpv4 = "$ip/$networkLength"

                val proxyCidrsPattern = """"proxy_cidrs"\s*:\s*\[(.*?)\]""".toRegex()
                val proxyCidrsMatch = proxyCidrsPattern.find(infosJson)
                val newProxyCidrs = mutableListOf<String>()
                proxyCidrsMatch?.groupValues?.get(1)?.let { cidrsStr ->
                    val cidrPattern = """"([^"]+)"""".toRegex()
                    cidrPattern.findAll(cidrsStr).forEach {
                        newProxyCidrs.add(it.groupValues[1])
                    }
                }

                if (newIpv4 != currentIpv4 || newProxyCidrs != currentProxyCidrs) {
                    Log.i(TAG, "网络状态变化 - IPv4: $currentIpv4 -> $newIpv4, Proxy CIDRs: ${currentProxyCidrs.size} -> ${newProxyCidrs.size}")

                    currentIpv4 = newIpv4
                    currentProxyCidrs = newProxyCidrs

                    restartVpnService(newIpv4, newProxyCidrs)
                } else {
                    Log.d(TAG, "网络状态无变化 - IPv4: $currentIpv4")
                }
            } else {
                Log.d(TAG, "未找到 IPv4 地址信息")
            }
        } catch (e: Exception) {
            Log.e(TAG, "监控网络状态时发生异常", e)
        }
    }

    private fun restartVpnService(ipv4: String, proxyCidrs: List<String>) {
        try {
            stopVpnService()
            Thread.sleep(500)
            startVpnService(ipv4, proxyCidrs)
        } catch (e: Exception) {
            Log.e(TAG, "重启 VPN 服务时发生异常", e)
        }
    }

    private fun startVpnService(ipv4: String, proxyCidrs: List<String>) {
        try {
            val intent = Intent(this, EasyTierVpnService::class.java)
            intent.action = EasyTierVpnService.ACTION_START_VPN
            intent.putExtra(EasyTierVpnService.EXTRA_IPV4_ADDRESS, ipv4)
            intent.putStringArrayListExtra(
                EasyTierVpnService.EXTRA_PROXY_CIDRS,
                ArrayList(proxyCidrs)
            )
            intent.putExtra(EasyTierVpnService.EXTRA_INSTANCE_NAME, EasyTierConfig.INSTANCE_NAME)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            Log.i(TAG, "VPN 服务已启动 - IPv4: $ipv4")
        } catch (e: Exception) {
            Log.e(TAG, "启动 VPN 服务时发生异常", e)
        }
    }

    private fun stopVpnService() {
        try {
            val intent = Intent(this, EasyTierVpnService::class.java)
            intent.action = EasyTierVpnService.ACTION_STOP_VPN
            startService(intent)
            Log.i(TAG, "VPN 服务停止命令已发送")
        } catch (e: Exception) {
            Log.e(TAG, "停止 VPN 服务时发生异常", e)
        }
    }

    private fun startForegroundNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val deviceInfo = DeviceInfoUtil.getDeviceHostname(this)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EasyTier 运行中")
            .setContentText("设备: $deviceInfo")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        Log.i(TAG, "前台服务通知已启动")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "EasyTier STB Service",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "EasyTier 机顶盒后台服务"
                    setShowBadge(false)
                }

                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
                Log.i(TAG, "通知渠道已创建")
            } catch (e: Exception) {
                Log.e(TAG, "创建通知渠道失败", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "EasyTier STB Service 销毁")
        stopEasyTier()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun getStatus(): ServiceStatus {
        return ServiceStatus(
            isRunning = isRunning,
            ipv4 = currentIpv4,
            proxyCidrs = currentProxyCidrs,
            deviceHostname = DeviceInfoUtil.getDeviceHostname(this)
        )
    }

    data class ServiceStatus(
        val isRunning: Boolean,
        val ipv4: String?,
        val proxyCidrs: List<String>,
        val deviceHostname: String
    )
}
