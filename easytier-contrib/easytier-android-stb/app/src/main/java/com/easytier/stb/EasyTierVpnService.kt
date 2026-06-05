package com.easytier.stb

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * EasyTier VPN 服务
 * 负责创建虚拟网络接口并与 EasyTier 核心通信
 */
class EasyTierVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private var instanceName: String? = null

    companion object {
        private const val TAG = "EasyTierVpnService"
        const val ACTION_START_VPN = "com.easytier.stb.START_VPN"
        const val ACTION_STOP_VPN = "com.easytier.stb.STOP_VPN"
        const val EXTRA_IPV4_ADDRESS = "ipv4_address"
        const val EXTRA_PROXY_CIDRS = "proxy_cidrs"
        const val EXTRA_INSTANCE_NAME = "instance_name"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VPN Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_VPN -> {
                val ipv4Address = intent.getStringExtra(EXTRA_IPV4_ADDRESS)
                val proxyCidrs = intent.getStringArrayListExtra(EXTRA_PROXY_CIDRS) ?: arrayListOf()
                instanceName = intent.getStringExtra(EXTRA_INSTANCE_NAME)

                if (ipv4Address == null || instanceName == null) {
                    Log.e(TAG, "缺少必要参数: ipv4Address=$ipv4Address, instanceName=$instanceName")
                    stopSelf()
                    return START_NOT_STICKY
                }

                Log.i(
                    TAG,
                    "启动 VPN Service - IPv4: $ipv4Address, Proxy CIDRs: $proxyCidrs, Instance: $instanceName"
                )

                thread {
                    try {
                        setupVpnInterface(ipv4Address, proxyCidrs)
                    } catch (t: Throwable) {
                        Log.e(TAG, "VPN 设置失败", t)
                        stopSelf()
                    }
                }
            }

            ACTION_STOP_VPN -> {
                Log.i(TAG, "收到停止 VPN 命令")
                cleanup()
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun setupVpnInterface(ipv4Address: String, proxyCidrs: List<String>) {
        try {
            val (ip, networkLength) = parseIpv4Address(ipv4Address)

            val builder = Builder()
            builder.setSession("EasyTier VPN")
                .addAddress(ip, networkLength)
                .addDnsServer("223.5.5.5")
                .addDnsServer("114.114.114.114")
                .setMtu(1420)
                .addDisallowedApplication(packageName)

            proxyCidrs.forEach { cidr ->
                try {
                    val (routeIp, routeLength) = parseCidr(cidr)
                    builder.addRoute(routeIp, routeLength)
                    Log.d(TAG, "添加路由: $routeIp/$routeLength")
                } catch (e: Exception) {
                    Log.w(TAG, "解析 CIDR 失败: $cidr", e)
                }
            }

            builder.addRoute("10.126.126.0", 24)

            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                Log.e(TAG, "创建 VPN 接口失败")
                return
            }

            Log.i(TAG, "VPN 接口创建成功")

            instanceName?.let { name ->
                val fd = vpnInterface!!.fd
                val result = EasyTierJNI.setTunFd(name, fd)
                if (result == 0) {
                    Log.i(TAG, "TUN 文件描述符设置成功: $fd")
                } else {
                    Log.e(TAG, "TUN 文件描述符设置失败: $result")
                }
            }

            isRunning.set(true)

            while (isRunning.get() && vpnInterface != null) {
                Thread.sleep(1000)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "VPN 接口设置过程中发生错误", t)
        } finally {
            cleanup()
        }
    }

    private fun parseIpv4Address(ipv4Address: String): Pair<String, Int> {
        return if (ipv4Address.contains("/")) {
            val parts = ipv4Address.split("/")
            Pair(parts[0], parts[1].toInt())
        } else {
            Pair(ipv4Address, 24)
        }
    }

    private fun parseCidr(cidr: String): Pair<String, Int> {
        val parts = cidr.split("/")
        if (parts.size != 2) {
            throw IllegalArgumentException("无效的 CIDR 格式: $cidr")
        }
        return Pair(parts[0], parts[1].toInt())
    }

    private fun cleanup() {
        isRunning.set(false)
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.w(TAG, "关闭 VPN 接口失败", e)
        }
        vpnInterface = null
        Log.i(TAG, "VPN 接口已清理")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VPN Service destroyed")
        cleanup()
    }
}
