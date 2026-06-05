@echo off
REM EasyTier STB APK 构建脚本 (Windows 版本)

setlocal enabledelayedexpansion

echo ==============================================
echo EasyTier STB APK 构建脚本
echo ==============================================
echo.

set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%"
set "OUTPUT_DIR=%PROJECT_DIR%output"
set "JNI_LIBS_DIR=%PROJECT_DIR%app\src\main\jniLibs"

REM 构建类型 (debug/release)
set BUILD_TYPE=%1
if "%BUILD_TYPE%"=="" set BUILD_TYPE=debug

REM 检查 native 库是否存在
if not exist "%JNI_LIBS_DIR%" (
    echo [WARN] Native 库目录不存在
    set "NEED_BUILD_NATIVE=1"
) else (
    dir /b "%JNI_LIBS_DIR%" | findstr /r "." >nul 2>&1
    if errorlevel 1 (
        echo [WARN] Native 库不存在
        set "NEED_BUILD_NATIVE=1"
    )
)

if defined NEED_BUILD_NATIVE (
    echo [INFO] 正在先构建 Native 库...
    call "%PROJECT_DIR%build_native_libs.bat"
    if errorlevel 1 (
        echo [ERROR] Native 库构建失败
        exit /b 1
    )
)

REM 创建输出目录
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

cd /d "%PROJECT_DIR%"

echo [INFO] 开始构建 %BUILD_TYPE% 版本 APK...
echo.

if "%BUILD_TYPE%"=="release" (
    echo [INFO] 构建 Release 版本...
    call gradlew assembleRelease
    
    set "APK_FILE=%PROJECT_DIR%app\build\outputs\apk\release\app-release.apk"
    set "OUTPUT_FILE=%OUTPUT_DIR%\easytier-stb-release.apk"
    
    if exist "%APK_FILE%" (
        copy /y "%APK_FILE%" "%OUTPUT_FILE%" >nul
        echo.
        echo ==============================================
        echo [OK] Release APK 已构建完成: !OUTPUT_FILE!
        echo ==============================================
    ) else (
        echo [ERROR] 未找到 Release APK 文件
        exit /b 1
    )
) else (
    echo [INFO] 构建 Debug 版本...
    call gradlew assembleDebug
    
    set "APK_FILE=%PROJECT_DIR%app\build\outputs\apk\debug\app-debug.apk"
    set "OUTPUT_FILE=%OUTPUT_DIR%\easytier-stb-debug.apk"
    
    if exist "%APK_FILE%" (
        copy /y "%APK_FILE%" "%OUTPUT_FILE%" >nul
        echo.
        echo ==============================================
        echo [OK] Debug APK 已构建完成: !OUTPUT_FILE!
        echo ==============================================
    ) else (
        echo [ERROR] 未找到 Debug APK 文件
        exit /b 1
    )
)

echo.
echo APK 文件位置: %OUTPUT_FILE%
echo.
echo 安装命令:
echo   adb install -r "%OUTPUT_FILE%"
echo.
echo 如果需要构建 Release 版本，运行:
echo   build_apk.bat release

endlocal
