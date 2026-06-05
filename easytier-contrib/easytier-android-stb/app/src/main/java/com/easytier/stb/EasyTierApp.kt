package com.easytier.stb

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * EasyTier 机顶盒应用程序类
 * 负责应用程序级别的初始化
 */
class EasyTierApp : Application() {

    companion object {
        private const val TAG = "EasyTierApp"
        lateinit var instance: EasyTierApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "EasyTier 机顶盒应用启动")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val serviceIntent = Intent(this, EasyTierStbService::class.java)
                serviceIntent.action = EasyTierStbService.ACTION_START
                startForegroundService(serviceIntent)
                Log.i(TAG, "应用启动时启动 EasyTier 服务")
            } catch (e: Exception) {
                Log.e(TAG, "应用启动时启动服务失败", e)
            }
        }
    }
}
