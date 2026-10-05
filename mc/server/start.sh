#!/usr/bin/env bash
# ===========================================
# StarStack MC Server 启动脚本 (Linux/macOS)
# 公网域名: jy.yoliyo.cn
# 本机 IP:  10.0.0.124
# 端口:     25565
# ===========================================
SERVER_JAR="server.jar"
MIN_RAM="1G"
MAX_RAM="4G"

cd "$(dirname "$0")"

if [ ! -f "$SERVER_JAR" ]; then
    echo "[ERROR] 未找到 $SERVER_JAR，请将 Minecraft 官方服务器核心放到本目录。"
    echo "下载地址: https://www.minecraft.net/en-us/download/server"
    exit 1
fi

echo "正在启动 StarStack MC 服务器..."
echo "本机绑定: 10.0.0.124:25565"
echo "公网域名: jy.yoliyo.cn"
echo "提示:     确保路由器把外网 25565 转发到 10.0.0.124:25565"
echo

java -Xms"$MIN_RAM" -Xmx"$MAX_RAM" -jar "$SERVER_JAR" nogui