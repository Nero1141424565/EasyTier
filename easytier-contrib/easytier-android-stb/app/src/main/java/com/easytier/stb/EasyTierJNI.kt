package com.easytier.stb

/**
 * EasyTier JNI 接口类
 * 提供 Android 应用调用 EasyTier 网络功能的接口
 */
object EasyTierJNI {

    init {
        try {
            System.loadLibrary("easytier_android_jni")
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("EasyTierJNI", "加载 native 库失败", e)
        }
    }

    /**
     * 设置 TUN 文件描述符
     * @param instanceName 实例名称
     * @param fd TUN 文件描述符
     * @return 0 表示成功，-1 表示失败
     */
    @JvmStatic
    external fun setTunFd(instanceName: String, fd: Int): Int

    /**
     * 解析配置字符串
     * @param config TOML 格式的配置字符串
     * @return 0 表示成功，-1 表示失败
     */
    @JvmStatic
    external fun parseConfig(config: String): Int

    /**
     * 运行网络实例
     * @param config TOML 格式的配置字符串
     * @return 0 表示成功，-1 表示失败
     */
    @JvmStatic
    external fun runNetworkInstance(config: String): Int

    /**
     * 保留指定的网络实例，停止其他实例
     * @param instanceNames 要保留的实例名称数组，传入 null 或空数组将停止所有实例
     * @return 0 表示成功，-1 表示失败
     */
    @JvmStatic
    external fun retainNetworkInstance(instanceNames: Array<String>?): Int

    /**
     * 收集网络信息
     * @param maxLength 最大返回条目数
     * @return 包含网络信息的 JSON 字符串
     */
    @JvmStatic
    external fun collectNetworkInfos(maxLength: Int): String?

    /**
     * 获取最后的错误消息
     * @return 错误消息字符串，如果没有错误则返回 null
     */
    @JvmStatic
    external fun getLastError(): String?

    /**
     * 停止所有网络实例
     * @return 0 表示成功，-1 表示失败
     */
    @JvmStatic
    fun stopAllInstances(): Int {
        return retainNetworkInstance(null)
    }

    /**
     * 保留单个实例，停止其他所有实例
     * @param instanceName 要保留的实例名称
     * @return 0 表示成功，-1 表示失败
     */
    @JvmStatic
    fun retainSingleInstance(instanceName: String): Int {
        return retainNetworkInstance(arrayOf(instanceName))
    }
}
