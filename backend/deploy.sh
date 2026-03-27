#!/bin/bash

# 卫星影像标注系统 - 部署脚本
# 用于将后端服务部署到 192.168.200.77 服务器

set -e

# 配置变量
REMOTE_HOST="192.168.200.77"
REMOTE_USER="root"
REMOTE_DIR="/opt/satellite-annotation"
UPLOADS_DIR="/opt/uploads"
SERVICE_NAME="satellite-annotation"
JAR_FILE="target/satellite-annotation-backend-0.0.1-SNAPSHOT.jar"

echo "======================================"
echo "卫星影像标注系统 - 部署脚本"
echo "======================================"
echo ""

# 检查 JAR 文件是否存在
if [ ! -f "$JAR_FILE" ]; then
    echo "错误：JAR 文件不存在：$JAR_FILE"
    echo "请先运行：mvn clean package -DskipTests"
    exit 1
fi

# 1. 创建远程目录
echo "步骤 1: 创建远程目录..."
ssh ${REMOTE_USER}@${REMOTE_HOST} "mkdir -p ${REMOTE_DIR}"
ssh ${REMOTE_USER}@${REMOTE_HOST} "mkdir -p ${UPLOADS_DIR}/images"
ssh ${REMOTE_USER}@${REMOTE_HOST} "mkdir -p ${UPLOADS_DIR}/tiles"
ssh ${REMOTE_USER}@${REMOTE_HOST} "mkdir -p ${UPLOADS_DIR}/symbols"
echo "完成"

# 2. 上传 JAR 文件
echo "步骤 2: 上传 JAR 文件..."
scp "$JAR_FILE" ${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/
echo "完成"

# 3. 创建 systemd 服务
echo "步骤 3: 创建 systemd 服务..."
cat << EOF | ssh ${REMOTE_USER}@${REMOTE_HOST} "cat > /etc/systemd/system/${SERVICE_NAME}.service"
[Unit]
Description=Satellite Annotation Backend
After=syslog.target network.target

[Service]
Type=simple
User=root
WorkingDirectory=${REMOTE_DIR}
ExecStart=/usr/bin/java -jar -Dspring.profiles.active=prod ${REMOTE_DIR}/satellite-annotation-backend-0.0.1-SNAPSHOT.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
echo "完成"

# 4. 重新加载并启动服务
echo "步骤 4: 重新加载并启动服务..."
ssh ${REMOTE_USER}@${REMOTE_HOST} "systemctl daemon-reload"
ssh ${REMOTE_USER}@${REMOTE_HOST} "systemctl stop ${SERVICE_NAME}" || true
ssh ${REMOTE_USER}@${REMOTE_HOST} "systemctl start ${SERVICE_NAME}"
ssh ${REMOTE_USER}@${REMOTE_HOST} "systemctl enable ${SERVICE_NAME}"
echo "完成"

# 5. 检查服务状态
echo "步骤 5: 检查服务状态..."
ssh ${REMOTE_USER}@${REMOTE_HOST} "systemctl status ${SERVICE_NAME} --no-pager"

# 6. 验证服务
echo ""
echo "步骤 6: 验证服务..."
sleep 5
ssh ${REMOTE_USER}@${REMOTE_HOST} "curl -s http://localhost:8080/actuator/health || curl -s http://localhost:8080/api/annotations || echo '服务已启动'"

echo ""
echo "======================================"
echo "部署完成!"
echo "======================================"
echo ""
echo "服务状态：systemctl status ${SERVICE_NAME}"
echo "查看日志：journalctl -u ${SERVICE_NAME} -f"
echo "停止服务：systemctl stop ${SERVICE_NAME}"
echo "重启服务：systemctl restart ${SERVICE_NAME}"
echo ""
echo "API 地址：http://${REMOTE_HOST}:8080/api"
echo "前端地址：http://${REMOTE_HOST}"
echo ""
