package com.easytier.stb

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * EasyTier 机顶盒主活动
 * 提供简单的 UI 来显示状态和控制服务
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val VPN_PERMISSION_CODE = 100
    }

    private lateinit var tvDeviceInfo: TextView
    private lateinit var tvNetworkInfo: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnStart: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var btnCheckPermission: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        displayDeviceInfo()
        checkAndRequestVpnPermission()
        startEasyTierService()
    }

    private fun initViews() {
        tvDeviceInfo = findViewById(R.id.tvDeviceInfo)
        tvNetworkInfo = findViewById(R.id.tvNetworkInfo)
        tvStatus = findViewById(R.id.tvStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnCheckPermission = findViewById(R.id.btnCheckPermission)

        btnStart.setOnClickListener {
            startEasyTierService()
        }

        btnStop.setOnClickListener {
            stopEasyTierService()
        }

        btnCheckPermission.setOnClickListener {
            checkAndRequestVpnPermission()
        }
    }

    private fun displayDeviceInfo() {
        val deviceInfo = DeviceInfoUtil.getDeviceInfoDebug(this)
        val configInfo = EasyTierConfig.getConfigInfo(this)

        val deviceInfoText = buildString {
            append("设备信息:\n")
            append("厂商: ${deviceInfo["manufacturer"]}\n")
            append("品牌: ${deviceInfo["brand"]}\n")
            append("型号: ${deviceInfo["model"]}\n")
            append("主机名: ${deviceInfo["hostname"]}\n")
            append("\n")
            append("网络配置:\n")
            append("网络名称: ${configInfo["network_name"]}\n")
            append("初始节点: ${configInfo["initial_peer"]}\n")
            append("节点名称: ${configInfo["node_name"]}")
        }

        tvDeviceInfo.text = deviceInfoText

        val networkInfoText = buildString {
            append("运行状态: 正在启动...\n")
            append("虚拟IP: 等待分配\n")
            append("已连接节点: 0\n")
        }
        tvNetworkInfo.text = networkInfoText
    }

    private fun checkAndRequestVpnPermission() {
        try {
            val intent = Intent(android.net.VpnService.SERVICE_INTERFACE)
            intent.setPackage("com.easytier.stb")

            val vpnIntent = android.net.VpnService.prepare(this)
            if (vpnIntent != null) {
                Log.i(TAG, "需要请求 VPN 权限")
                startActivityForResult(vpnIntent, VPN_PERMISSION_CODE)
                Toast.makeText(this, "请授予 VPN 权限以使用 EasyTier", Toast.LENGTH_LONG).show()
            } else {
                Log.i(TAG, "已具有 VPN 权限")
                tvStatus.text = "VPN 权限: 已获取"
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查 VPN 权限失败", e)
            Toast.makeText(this, "检查 VPN 权限失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_PERMISSION_CODE) {
            if (resultCode == RESULT_OK) {
                Log.i(TAG, "VPN 权限已授予")
                tvStatus.text = "VPN 权限: 已获取"
                Toast.makeText(this, "VPN 权限已授予", Toast.LENGTH_SHORT).show()
                startEasyTierService()
            } else {
                Log.w(TAG, "VPN 权限被拒绝")
                tvStatus.text = "VPN 权限: 被拒绝"
                Toast.makeText(this, "VPN 权限被拒绝，无法正常工作", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startEasyTierService() {
        try {
            val serviceIntent = Intent(this, EasyTierStbService::class.java)
            serviceIntent.action = EasyTierStbService.ACTION_START

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            Log.i(TAG, "EasyTier 服务启动命令已发送")
            tvStatus.text = "服务状态: 正在启动..."
            Toast.makeText(this, "正在启动 EasyTier 服务...", Toast.LENGTH_SHORT).show()

            Thread {
                Thread.sleep(3000)
                updateNetworkStatus()
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "启动 EasyTier 服务失败", e)
            tvStatus.text = "服务状态: 启动失败 - ${e.message}"
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopEasyTierService() {
        try {
            val serviceIntent = Intent(this, EasyTierStbService::class.java)
            serviceIntent.action = EasyTierStbService.ACTION_STOP

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            Log.i(TAG, "EasyTier 服务停止命令已发送")
            tvStatus.text = "服务状态: 已停止"
            tvNetworkInfo.text = "运行状态: 已停止\n虚拟IP: 无\n已连接节点: 0"
            Toast.makeText(this, "正在停止 EasyTier 服务...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "停止 EasyTier 服务失败", e)
            Toast.makeText(this, "停止失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateNetworkStatus() {
        try {
            val infosJson = EasyTierJNI.collectNetworkInfos(10)
            if (infosJson.isNullOrEmpty()) {
                Log.d(TAG, "未获取到网络信息")
                return
            }

            val ipv4Pattern = """"virtual_ipv4"\s*:\s*\{\s*"address"\s*:\s*\{\s*"addr"\s*:\s*(-?\d+)""".toRegex()
            val matchResult = ipv4Pattern.find(infosJson)

            var ipv4 = "未分配"
            if (matchResult != null) {
                val addrInt = matchResult.groupValues[1].toInt()
                ipv4 = String.format(
                    "%d.%d.%d.%d",
                    (addrInt shr 24) and 0xFF,
                    (addrInt shr 16) and 0xFF,
                    (addrInt shr 8) and 0xFF,
                    addrInt and 0xFF
                )
            }

            val peerCount = "\"peer_id\"".toRegex().findAll(infosJson).count()

            val runningPattern = """"running"\s*:\s*true""".toRegex()
            val isRunning = runningPattern.containsMatchIn(infosJson)

            val statusText = if (isRunning) "运行中" else "正在连接..."

            runOnUiThread {
                tvStatus.text = "服务状态: 运行中"
                val networkInfoText = buildString {
                    append("运行状态: $statusText\n")
                    append("虚拟IP: $ipv4\n")
                    append("已连接节点: $peerCount\n")
                }
                tvNetworkInfo.text = networkInfoText
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新网络状态失败", e)
        }
    }

    override fun onResume() {
        super.onResume()
        Thread {
            Thread.sleep(1000)
            updateNetworkStatus()
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
