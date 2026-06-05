#!/bin/bash

# EasyTier STB Native 库构建脚本
# 用于构建适用于 Android 平台的 JNI 库
# 使用 cargo-ndk 工具简化 Android 编译过程

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

REPO_ROOT=$(git rev-parse --show-toplevel)
PROJECT_DIR="$REPO_ROOT/easytier-contrib/easytier-android-stb"
JNI_LIBS_DIR="$PROJECT_DIR/app/src/main/jniLibs"

echo -e "${GREEN}EasyTier STB Native 库构建脚本${NC}"
echo "=============================================="

# 检查 Rust 是否安装
if ! command -v rustc &> /dev/null; then
    echo -e "${RED}错误: 未找到 Rust 编译器，请先安装 Rust${NC}"
    exit 1
fi

# 检查 cargo 是否安装
if ! command -v cargo &> /dev/null; then
    echo -e "${RED}错误: 未找到 Cargo，请先安装 Rust 工具链${NC}"
    exit 1
fi

# 检查 cargo-ndk 是否安装
if ! cargo ndk --version &> /dev/null; then
    echo -e "${YELLOW}cargo-ndk 未安装，正在安装...${NC}"
    cargo install cargo-ndk
    if ! cargo ndk --version &> /dev/null; then
        echo -e "${RED}错误: cargo-ndk 安装失败${NC}"
        exit 1
    fi
fi

echo -e "${GREEN}cargo-ndk 版本: $(cargo ndk --version)${NC}"

# Android 目标架构
ANDROID_TARGETS=("arm64-v8a" "armeabi-v7a")

# Android 架构到 Rust target 的映射
declare -A TARGET_MAP
TARGET_MAP["arm64-v8a"]="aarch64-linux-android"
TARGET_MAP["armeabi-v7a"]="armv7-linux-androideabi"
TARGET_MAP["x86"]="i686-linux-android"
TARGET_MAP["x86_64"]="x86_64-linux-android"

# 检查并安装所需的 Rust target
echo -e "${YELLOW}检查并安装 Android 目标架构...${NC}"
for android_target in "${ANDROID_TARGETS[@]}"; do
    rust_target="${TARGET_MAP[$android_target]}"
    if ! rustup target list --installed | grep -q "$rust_target"; then
        echo -e "${YELLOW}安装目标架构: $rust_target (for $android_target)${NC}"
        rustup target add "$rust_target"
    else
        echo -e "${GREEN}目标架构已安装: $rust_target (for $android_target)${NC}"
    fi
done

# 创建输出目录
mkdir -p "$JNI_LIBS_DIR"

# 构建函数
build_for_target() {
    local android_target=$1
    echo -e "${YELLOW}构建目标: $android_target${NC}"

    # 构建 easytier-android-jni
    echo -e "${YELLOW}构建 easytier-android-jni for $android_target${NC}"
    (cd "$REPO_ROOT/easytier-contrib/easytier-android-jni" && cargo ndk -t $android_target build --release)

    # 构建 easytier-ffi
    echo -e "${YELLOW}构建 easytier-ffi for $android_target${NC}"
    (cd "$REPO_ROOT/easytier-contrib/easytier-ffi" && cargo ndk -t $android_target build --release)

    # 创建目标目录
    mkdir -p "$JNI_LIBS_DIR/$android_target"

    # 复制库文件
    rust_target="${TARGET_MAP[$android_target]}"
    cp "$REPO_ROOT/target/$rust_target/release/libeasytier_android_jni.so" "$JNI_LIBS_DIR/$android_target/"
    cp "$REPO_ROOT/target/$rust_target/release/libeasytier_ffi.so" "$JNI_LIBS_DIR/$android_target/"

    echo -e "${GREEN}库文件已复制到: $JNI_LIBS_DIR/$android_target/${NC}"
}

# 检查 Android NDK
if [ -z "$ANDROID_NDK_ROOT" ] && [ -z "$ANDROID_NDK_HOME" ] && [ -z "$NDK_HOME" ]; then
    echo -e "${YELLOW}警告: 未设置 Android NDK 环境变量${NC}"
    echo "cargo-ndk 将尝试自动检测 NDK 路径"
    echo "如果构建失败，请设置以下环境变量之一:"
    echo "  - ANDROID_NDK_ROOT"
    echo "  - ANDROID_NDK_HOME"
    echo "  - NDK_HOME"
else
    if [ -n "$ANDROID_NDK_ROOT" ]; then
        echo -e "${GREEN}使用 Android NDK: $ANDROID_NDK_ROOT${NC}"
    elif [ -n "$ANDROID_NDK_HOME" ]; then
        echo -e "${GREEN}使用 Android NDK: $ANDROID_NDK_HOME${NC}"
    elif [ -n "$NDK_HOME" ]; then
        echo -e "${GREEN}使用 Android NDK: $NDK_HOME${NC}"
    fi
fi

# 构建所有目标
echo -e "${YELLOW}开始构建所有目标架构...${NC}"
for target in "${ANDROID_TARGETS[@]}"; do
    build_for_target "$target"
done

echo -e "${GREEN}构建完成！${NC}"
echo -e "${GREEN}所有库文件已生成到: $JNI_LIBS_DIR${NC}"
echo ""
echo "目录结构:"
ls -la "$JNI_LIBS_DIR"/*/

echo ""
echo -e "${YELLOW}下一步:${NC}"
echo "1. 在 Android Studio 中打开项目"
echo "2. 同步 Gradle 项目"
echo "3. 构建 APK: Build -> Build Bundle(s) / APK(s) -> Build APK(s)"
echo ""
echo -e "${GREEN}注意: 您也可以使用命令行构建 APK:${NC}"
echo "  cd $PROJECT_DIR"
echo "  ./gradlew assembleDebug  # 构建调试版本"
echo "  ./gradlew assembleRelease # 构建发布版本"
