#!/bin/bash

# EasyTier STB APK 构建脚本
# 用于构建 Android APK 安装包

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PROJECT_DIR="$SCRIPT_DIR"
OUTPUT_DIR="$PROJECT_DIR/output"

echo -e "${GREEN}EasyTier STB APK 构建脚本${NC}"
echo "=============================================="

# 检查 native 库是否存在
JNI_LIBS_DIR="$PROJECT_DIR/app/src/main/jniLibs"
if [ ! -d "$JNI_LIBS_DIR" ] || [ -z "$(ls -A "$JNI_LIBS_DIR" 2>/dev/null)" ]; then
    echo -e "${YELLOW}Native 库不存在，正在先构建 Native 库...${NC}"
    cd "$PROJECT_DIR"
    ./build_native_libs.sh
fi

# 检查 Gradle
if [ ! -f "$PROJECT_DIR/gradlew" ]; then
    echo -e "${YELLOW}gradlew 不存在，检查是否需要下载 Gradle...${NC}"
fi

# 创建输出目录
mkdir -p "$OUTPUT_DIR"

# 构建类型
BUILD_TYPE=${1:-debug}

echo -e "${GREEN}开始构建 $BUILD_TYPE 版本 APK...${NC}"

cd "$PROJECT_DIR"

# 授予执行权限
chmod +x gradlew 2>/dev/null || true

if [ "$BUILD_TYPE" = "release" ]; then
    echo -e "${YELLOW}构建 Release 版本...${NC}"
    ./gradlew assembleRelease

    # 复制 APK
    APK_FILE="$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"
    OUTPUT_FILE="$OUTPUT_DIR/easytier-stb-release.apk"

    if [ -f "$APK_FILE" ]; then
        cp "$APK_FILE" "$OUTPUT_FILE"
        echo -e "${GREEN}Release APK 已构建完成: $OUTPUT_FILE${NC}"
    else
        echo -e "${RED}错误: 未找到 Release APK 文件${NC}"
        exit 1
    fi
else
    echo -e "${YELLOW}构建 Debug 版本...${NC}"
    ./gradlew assembleDebug

    # 复制 APK
    APK_FILE="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
    OUTPUT_FILE="$OUTPUT_DIR/easytier-stb-debug.apk"

    if [ -f "$APK_FILE" ]; then
        cp "$APK_FILE" "$OUTPUT_FILE"
        echo -e "${GREEN}Debug APK 已构建完成: $OUTPUT_FILE${NC}"
    else
        echo -e "${RED}错误: 未找到 Debug APK 文件${NC}"
        exit 1
    fi
fi

echo ""
echo -e "${GREEN}构建完成！${NC}"
echo "APK 文件位置: $OUTPUT_FILE"
echo ""
echo -e "${YELLOW}安装命令:${NC}"
echo "  adb install -r $OUTPUT_FILE"
echo ""
echo -e "${YELLOW}如果需要构建 Release 版本，运行:${NC}"
echo "  ./build_apk.sh release"
