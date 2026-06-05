@echo off
REM EasyTier STB Native 库构建脚本 (Windows 版本)

setlocal enabledelayedexpansion

echo ==============================================
echo EasyTier STB Native 库构建脚本
echo ==============================================
echo.

REM 获取项目根目录
for %%i in ("%~dp0..\..") do set "REPO_ROOT=%%~fi"
set "PROJECT_DIR=%REPO_ROOT%\easytier-contrib\easytier-android-stb"
set "JNI_LIBS_DIR=%PROJECT_DIR%\app\src\main\jniLibs"

REM 检查 Rust 是否安装
where rustc >nul 2>&1
if errorlevel 1 (
    echo [ERROR] 未找到 Rust 编译器，请先安装 Rust
    echo 下载地址: https://rustup.rs/
    exit /b 1
)

REM 检查 cargo 是否安装
where cargo >nul 2>&1
if errorlevel 1 (
    echo [ERROR] 未找到 Cargo，请先安装 Rust 工具链
    exit /b 1
)

REM 检查 cargo-ndk 是否安装
cargo ndk --version >nul 2>&1
if errorlevel 1 (
    echo [WARN] cargo-ndk 未安装，正在安装...
    cargo install cargo-ndk
    if errorlevel 1 (
        echo [ERROR] cargo-ndk 安装失败
        exit /b 1
    )
)

for /f "delims=" %%i in ('cargo ndk --version') do echo [INFO] cargo-ndk 版本: %%i
echo.

REM Android 目标架构
set ANDROID_TARGETS=arm64-v8a armeabi-v7a

REM 检查并安装 Rust target
echo [INFO] 检查并安装 Android 目标架构...
for %%t in (%ANDROID_TARGETS%) do (
    if "%%t"=="arm64-v8a" set "RUST_TARGET=aarch64-linux-android"
    if "%%t"=="armeabi-v7a" set "RUST_TARGET=armv7-linux-androideabi"
    
    rustup target list --installed | findstr /c:"!RUST_TARGET!" >nul 2>&1
    if errorlevel 1 (
        echo [INFO] 安装目标架构: !RUST_TARGET! (for %%t)
        rustup target add !RUST_TARGET!
    ) else (
        echo [INFO] 目标架构已安装: !RUST_TARGET! (for %%t)
    )
)

REM 创建输出目录
if not exist "%JNI_LIBS_DIR%" mkdir "%JNI_LIBS_DIR%"

REM 构建函数
:build_for_target
for %%t in (%ANDROID_TARGETS%) do (
    echo.
    echo [INFO] 构建目标: %%t
    
    if "%%t"=="arm64-v8a" set "RUST_TARGET=aarch64-linux-android"
    if "%%t"=="armeabi-v7a" set "RUST_TARGET=armv7-linux-androideabi"
    
    REM 构建 easytier-android-jni
    echo [INFO] 构建 easytier-android-jni for %%t
    cd /d "%REPO_ROOT%\easytier-contrib\easytier-android-jni"
    cargo ndk -t %%t build --release
    if errorlevel 1 (
        echo [ERROR] 构建 easytier-android-jni 失败
        exit /b 1
    )
    
    REM 构建 easytier-ffi
    echo [INFO] 构建 easytier-ffi for %%t
    cd /d "%REPO_ROOT%\easytier-contrib\easytier-ffi"
    cargo ndk -t %%t build --release
    if errorlevel 1 (
        echo [ERROR] 构建 easytier-ffi 失败
        exit /b 1
    )
    
    REM 创建目标目录
    if not exist "%JNI_LIBS_DIR%\%%t" mkdir "%JNI_LIBS_DIR%\%%t"
    
    REM 复制库文件
    copy /y "%REPO_ROOT%\target\!RUST_TARGET!\release\libeasytier_android_jni.so" "%JNI_LIBS_DIR%\%%t\" >nul
    copy /y "%REPO_ROOT%\target\!RUST_TARGET!\release\libeasytier_ffi.so" "%JNI_LIBS_DIR%\%%t\" >nul
    
    echo [OK] 库文件已复制到: %JNI_LIBS_DIR%\%%t\
)

echo.
echo ==============================================
echo [OK] 构建完成！
echo ==============================================
echo.
echo Native 库目录: %JNI_LIBS_DIR%
echo.
echo 目录结构:
dir /b "%JNI_LIBS_DIR%\*"

echo.
echo 下一步:
echo 1. 在 Android Studio 中打开项目: %PROJECT_DIR%
echo 2. 同步 Gradle 项目
echo 3. 构建 APK: Build -^> Build Bundle(s) / APK(s) -^> Build APK(s)
echo.
echo 或者使用命令行:
echo   cd %PROJECT_DIR%
echo   gradlew assembleDebug  REM 构建调试版本
echo   gradlew assembleRelease REM 构建发布版本

cd /d "%PROJECT_DIR%"
endlocal
