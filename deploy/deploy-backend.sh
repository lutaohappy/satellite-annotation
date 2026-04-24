#!/bin/bash
# 后端部署脚本

SERVER="192.168.200.77"
USER="root"
PASSWORD="box@gridknow"
REMOTE_JAR="/opt/satellite-annotation-backend-0.0.1-SNAPSHOT.jar"
LOCAL_JAR="/Users/taolu/Downloads/test/satellite-annotation/backend/target/satellite-annotation-backend-0.0.1-SNAPSHOT.jar"

echo "=== 后端部署脚本 ==="
echo "本地 JAR: $LOCAL_JAR"
echo "服务器：$SERVER"
echo "远程 JAR: $REMOTE_JAR"
echo "SSH 账号：$USER"

# 检查本地构建是否存在
if [ ! -f "$LOCAL_JAR" ]; then
    echo "错误：本地 JAR 文件不存在，请先运行 mvn package -DskipTests"
    exit 1
fi

# 使用 sshpass 复制文件
if command -v sshpass &> /dev/null; then
    echo "正在上传 JAR 文件..."
    sshpass -p "$PASSWORD" scp -o StrictHostKeyChecking=no "$LOCAL_JAR" "$USER@$SERVER:$REMOTE_JAR"
else
    echo "未安装 sshpass，使用普通 scp (需要手动输入密码)"
    scp "$LOCAL_JAR" "$USER@$SERVER:$REMOTE_JAR"
fi

if [ $? -eq 0 ]; then
    echo "JAR 文件上传成功！"
    echo ""
    echo "正在重启后端服务..."

    # 停止旧进程
    sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no "$USER@$SERVER" "ps aux | grep java | grep -v grep | awk '{print \$2}' | xargs kill 2>/dev/null || true"
    sleep 2

    # 启动新进程（使用 prod profile）
    sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no "$USER@$SERVER" "nohup java -jar $REMOTE_JAR --spring.profiles.active=prod > /opt/backend.log 2>&1 &"

    sleep 5
    echo "后端服务已启动"
    echo ""
    echo "查看日志：ssh $USER@$SERVER 'tail -50 /opt/backend.log'"
else
    echo "JAR 文件上传失败"
    exit 1
fi
