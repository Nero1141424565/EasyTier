package com.easytier.stb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 开机启动广播接收器
 * 监听设备开机完成事件，自动启动 EasyTier 服务
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "收到广播: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "设备开机完成，准备启动 EasyTier 服务")
                startEasyTierService(context)
            }

            Intent.ACTION_REBOOT -> {
                Log.i(TAG, "设备重启，准备启动 EasyTier 服务")
                startEasyTierService(context)
            }

            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.i(TAG, "设备快速启动，准备启动 EasyTier 服务")
                startEasyTierService(context)
            }

            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                Log.i(TAG, "HTC 快速启动，准备启动 EasyTier 服务")
                startEasyTierService(context)
            }
        }
    }

    private fun startEasyTierService(context: Context) {
        try {
            val serviceIntent = Intent(context, EasyTierStbService::class.java)
            serviceIntent.action = EasyTierStbService.ACTION_START

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.i(TAG, "使用 startForegroundService 启动服务")
                context.startForegroundService(serviceIntent)
            } else {
                Log.i(TAG, "使用 startService 启动服务")
                context.startService(serviceIntent)
            }

            Log.i(TAG, "EasyTier 服务启动命令已发送")
        } catch (e: Exception) {
            Log.e(TAG, "启动 EasyTier 服务失败", e)

            try {
                val serviceIntent = Intent(context, EasyTierStbService::class.java)
                serviceIntent.action = EasyTierStbService.ACTION_START
                context.startService(serviceIntent)
                Log.i(TAG, "重试使用 startService 启动服务成功")
            } catch (e2: Exception) {
                Log.e(TAG, "重试启动服务也失败", e2)
            }
        }
    }
}
