# EasyTier STB (机顶盒版)

专为 Android 机顶盒设计的 EasyTier 客户端，支持开机自启动、静默运行、自动连接共享节点组网。

## 功能特性

- ✅ 开机自动启动
- ✅ 后台静默运行（前台服务）
- ✅ 自动连接指定共享节点
- ✅ 读取设备厂商编号 + 硬件序列号作为主机名和节点名称
- ✅ 支持 VPN 模式
- ✅ 简洁的状态显示界面

## 配置信息

| 配置项 | 值 |
|--------|-----|
| 网络名称 | `lkwtestsdn` |
| 网络密码 | `2wsx@WSX` |
| 初始节点 | `tcp://lkwcomputer.szyddnskjgs.xyz:11010` |

## 设备标识规则

应用会自动读取以下信息组合成主机名和节点名称：

```
主机名 = 厂商编号_硬件序列号
```

例如：
- 厂商: `Amlogic`
- 序列号: `ABC123456789`
- 主机名: `Amlogic_ABC123456789`

序列号读取优先级：
1. `Build.getSerial()` (Android 8.0+)
2. `Build.SERIAL` (已弃用，但某些设备仍然有效)
3. `Settings.Secure.ANDROID_ID`
4. `/proc/cpuinfo` 中的 Serial 字段
5. 硬件信息 + 时间戳（备选方案）

## 项目结构

```
easytier-android-stb/
├── app/
│   └── src/main/
│       ├── java/com/easytier/stb/
│       │   ├── BootReceiver.kt          # 开机启动广播接收器
│       │   ├── DeviceInfoUtil.kt        # 设备信息工具类
│       │   ├── EasyTierApp.kt           # 应用程序类
│       │   ├── EasyTierConfig.kt        # 配置管理类
│       │   ├── EasyTierJNI.kt           # JNI 接口类
│       │   ├── EasyTierStbService.kt    # 核心前台服务
│       │   ├── EasyTierVpnService.kt    # VPN 服务
│       │   └── MainActivity.kt          # 主界面
│       ├── jniLibs/                     # Native 库（需自行构建）
│       │   ├── arm64-v8a/
│       │   │   ├── libeasytier_android_jni.so
│       │   │   └── libeasytier_ffi.so
│       │   └── armeabi-v7a/
│       │       ├── libeasytier_android_jni.so
│       │       └── libeasytier_ffi.so
│       ├── res/
│       │   ├── layout/activity_main.xml
│       │   └── values/
│       └── AndroidManifest.xml
├── build_native_libs.sh                 # 构建 Native 库脚本
├── build_apk.sh                         # 构建 APK 脚本
└── README.md
```

## 快速开始

### 1. 环境准备

#### 必需环境
- **Rust 工具链** (1.95+)
- **Android NDK** (r25+)
- **Android Studio** (Hedgehog 2023.1+)
- **JDK** (17+)

#### 安装 Rust Android 目标

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi
```

#### 安装 cargo-ndk

```bash
cargo install cargo-ndk
```

### 2. 构建 Native 库

```bash
cd easytier-contrib/easytier-android-stb
chmod +x build_native_libs.sh
./build_native_libs.sh
```

### 3. 构建 APK

#### 方法一：使用脚本（推荐）

```bash
chmod +x build_apk.sh

# 构建 Debug 版本
./build_apk.sh

# 构建 Release 版本
./build_apk.sh release
```

#### 方法二：使用 Android Studio

1. 用 Android Studio 打开 `easytier-contrib/easytier-android-stb` 目录
2. 等待 Gradle 同步完成
3. 菜单：`Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`

### 4. 安装到机顶盒

```bash
# 安装 Debug 版本
adb install -r output/easytier-stb-debug.apk

# 安装 Release 版本
adb install -r output/easytier-stb-release.apk
```

## 使用说明

### 首次使用

1. 安装 APK 到机顶盒
2. 打开应用，授予 VPN 权限
3. 应用会自动启动服务并连接网络
4. 可以在界面查看设备信息和网络状态

### 设置开机自启动

大部分机顶盒需要手动设置：

1. 进入机顶盒的「设置」→「应用管理」
2. 找到「EasyTier STB」
3. 开启「自启动」或「开机自动运行」
4. 在「省电管理」或「后台管理」中添加白名单

### 查看状态

打开应用可以看到：
- 设备信息（厂商、型号、主机名）
- 网络状态（运行状态、虚拟IP、已连接节点数）
- 服务状态（运行中/已停止）

## 权限说明

| 权限 | 用途 |
|------|------|
| `INTERNET` | 网络连接 |
| `ACCESS_NETWORK_STATE` | 检查网络状态 |
| `RECEIVE_BOOT_COMPLETED` | 开机自动启动 |
| `FOREGROUND_SERVICE` | 前台服务 |
| `BIND_VPN_SERVICE` | VPN 服务 |
| `READ_PHONE_STATE` | 读取设备序列号 |
| `WAKE_LOCK` | 防止设备休眠 |

## 故障排查

### 1. 服务无法启动

- 检查是否已授予 VPN 权限
- 查看日志：`adb logcat -s EasyTierStbService`
- 确认初始节点地址可访问

### 2. 开机无法自启动

- 检查是否在系统设置中开启了自启动权限
- 某些品牌机顶盒需要额外设置（小米、华为、创维等）
- 查看日志：`adb logcat -s BootReceiver`

### 3. 无法获取设备序列号

- 某些机顶盒限制了序列号读取
- 应用会自动降级使用 ANDROID_ID 或其他标识
- 可以在界面的设备信息中查看实际使用的主机名

### 4. VPN 连接失败

- 确认已授予 VPN 权限
- 尝试手动停止再启动服务
- 查看日志：`adb logcat -s EasyTierVpnService`

## 常用 ADB 命令

```bash
# 查看应用日志
adb logcat -s EasyTierStbService
adb logcat -s EasyTierVpnService
adb logcat -s BootReceiver
adb logcat -s DeviceInfoUtil
adb logcat -s EasyTierJNI

# 启动/停止服务
adb shell am startservice -n com.easytier.stb/.EasyTierStbService -a com.easytier.stb.START
adb shell am startservice -n com.easytier.stb/.EasyTierStbService -a com.easytier.stb.STOP

# 查看前台服务
adb shell dumpsys activity services | grep EasyTierStbService

# 查看 VPN 状态
adb shell dumpsys vpn
```

## 修改配置

如需修改网络配置，编辑 [EasyTierConfig.kt](app/src/main/java/com/easytier/stb/EasyTierConfig.kt)：

```kotlin
const val NETWORK_NAME = "lkwtestsdn"        // 网络名称
const val NETWORK_SECRET = "2wsx@WSX"       // 网络密码
const val INITIAL_PEER = "tcp://lkwcomputer.szyddnskjgs.xyz:11010"  // 初始节点
```

## 构建说明

### Native 库构建详解

`build_native_libs.sh` 脚本会：
1. 检查并安装必要的 Rust 目标架构
2. 构建 `easytier-android-jni`
3. 构建 `easytier-ffi`
4. 将生成的 `.so` 文件复制到 `jniLibs` 目录

### APK 签名

Release 版本需要签名。在 `app/build.gradle.kts` 中配置签名信息：

```kotlin
android {
    signingConfigs {
        create("release") {
            keyAlias = "your_key_alias"
            keyPassword = "your_key_password"
            storeFile = file("your_keystore.jks")
            storePassword = "your_store_password"
        }
    }
}
```

## 注意事项

1. **Root 权限**：某些机顶盒可能需要 Root 才能读取完整的序列号
2. **网络限制**：某些网络环境可能限制 P2P 连接，此时会自动使用中继
3. **电源管理**：建议在系统设置中将应用加入电池优化白名单
4. **存储空间**：确保机顶盒有足够的存储空间（约 50MB）
5. **TV 模式**：应用已针对 TV 模式优化，支持遥控器操作

## 相关项目

- [EasyTier 主项目](../../)
- [easytier-android-jni](../easytier-android-jni/)
- [easytier-ffi](../easytier-ffi/)

## 许可证

本项目遵循 LGPL-3.0 许可证。
