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

# MC 26.1+ 需要 Java 25+，强制使用 JDK 26（系统里有 jdk-26.0.2.1）
# 覆盖 PATH 里的 java 17，否则启动失败
JAVA26="/c/Program Files/Java/jdk-26.0.2.1/bin/java.exe"
if [ -x "$JAVA26" ]; then
    JAVA="$JAVA26"
    echo "[java] 使用 $JAVA"
else
    JAVA="java"
    echo "[java] 警告: 未找到 JDK 26，回退到 PATH 中的 java（可能因版本过低启动失败）"
fi

if [ ! -f "$SERVER_JAR" ]; then
    echo "[ERROR] 未找到 $SERVER_JAR，请将 Minecraft 官方服务器核心放到本目录。"
    echo "下载地址: https://www.minecraft.net/en-us/download/server"
    exit 1
fi

echo "正在启动 StarStack MC 服务器..."
echo "本机绑定: 10.0.0.124:25565"
echo "公网域名: jy.yoliyo.cn"
echo "登录认证: 离线模式（online-mode=false）"
echo "提示:     确保路由器把外网 25565 转发到 10.0.0.124:25565"
echo

# 在线模式关闭，不需要 authlib-injector 拦截 Yggdrasil
"$JAVA" -Xms"$MIN_RAM" -Xmx"$MAX_RAM" \
    -jar "$SERVER_JAR" nogui