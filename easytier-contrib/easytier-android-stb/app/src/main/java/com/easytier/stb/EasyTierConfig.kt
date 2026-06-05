package com.easytier.stb

import android.content.Context
import android.util.Log

/**
 * EasyTier 配置管理类
 * 负责生成配置文件和管理网络配置
 */
object EasyTierConfig {

    private const val TAG = "EasyTierConfig"

    const val NETWORK_NAME = "lkwtestsdn"
    const val NETWORK_SECRET = "2wsx@WSX"
    const val INITIAL_PEER = "tcp://lkwcomputer.szyddnskjgs.xyz:11010"

    const val INSTANCE_NAME = "stb_instance"

    /**
     * 生成 TOML 格式的配置字符串
     */
    fun generateConfig(context: Context): String {
        val hostname = DeviceInfoUtil.getDeviceHostname(context)
        val nodeName = DeviceInfoUtil.getNodeName(context)

        val config = """
            inst_name = "$INSTANCE_NAME"
            hostname = "$hostname"
            node_name = "$nodeName"
            network_name = "$NETWORK_NAME"
            network_secret = "$NETWORK_SECRET"
            peers = ["$INITIAL_PEER"]
            listeners = ["tcp://0.0.0.0:11010", "udp://0.0.0.0:11010"]
            log_level = "info"
            mtu = 1420
            latency_first = true
            use_multi_path = true
            enable_encryption = true
            enable_ipv4 = true
            enable_ipv6 = false
            disable_p2p = false
        """.trimIndent()

        Log.i(TAG, "生成配置:\n$config")
        return config
    }

    /**
     * 生成调试用的配置信息
     */
    fun getConfigInfo(context: Context): Map<String, String> {
        return mapOf(
            "network_name" to NETWORK_NAME,
            "network_secret" to "***",
            "initial_peer" to INITIAL_PEER,
            "instance_name" to INSTANCE_NAME,
            "hostname" to DeviceInfoUtil.getDeviceHostname(context),
            "node_name" to DeviceInfoUtil.getNodeName(context)
        )
    }
}
