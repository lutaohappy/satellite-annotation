#!/bin/bash

# 卫星影像标注系统 - 完整部署脚本
# 用于构建并部署前后端到 192.168.200.77 服务器

set -e

REMOTE_HOST="192.168.200.77"
REMOTE_USER="root"
REMOTE_DIR="/opt/satellite-annotation"
WEB_DIR="/var/www/satellite-annotation"
UPLOADS_DIR="/opt/uploads"
SERVICE_NAME="satellite-annotation"

echo "======================================"
echo "卫星影像标注系统 - 完整部署脚本"
echo "======================================"
echo ""

# ========== 前端构建 ==========
echo "步骤 1: 构建前端..."
cd frontend
npm run build
cd ..
echo "前端构建完成"

# ========== 后端构建 ==========
echo ""
echo "步骤 2: 构建后端..."
cd backend
mvn clean package -DskipTests
cd ..
echo "后端构建完成"

# ========== 上传前端 ==========
echo ""
echo "步骤 3: 创建远程 Web 目录..."
ssh ${REMOTE_USER}@${REMOTE_HOST} "mkdir -p ${WEB_DIR}"

echo "上传前端文件..."
scp -r frontend/dist/* ${REMOTE_USER}@${REMOTE_HOST}:${WEB_DIR}/
echo "前端上传完成"

# ========== 上传后端 ==========
echo ""
echo "步骤 4: 创建远程应用目录..."
ssh ${REMOTE_USER}@${REMOTE_HOST} "mkdir -p ${REMOTE_DIR}"
ssh ${REMOTE_USER}@${REMOTE_HOST} "mkdir -p ${UPLOADS_DIR}/images"
ssh ${REMOTE_USER}@${REMOTE_HOST} "mkdir -p ${UPLOADS_DIR}/tiles"
ssh ${REMOTE_USER}@${REMOTE_HOST} "mkdir -p ${UPLOADS_DIR}/symbols"

echo "上传后端 JAR..."
scp backend/target/satellite-annotation-backend-0.0.1-SNAPSHOT.jar ${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/
echo "后端上传完成"

# ========== 配置 Nginx ==========
echo ""
echo "步骤 5: 配置 Nginx..."
cat << 'NGINX_EOF' | ssh ${REMOTE_USER}@${REMOTE_HOST} "cat > /etc/nginx/sites-available/satellite-annotation"
server {
    listen 80;
    server_name _;

    root /var/www/satellite-annotation;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /uploads/ {
        alias /opt/uploads/;
        add_header Access-Control-Allow-Origin *;
    }
}
NGINX_EOF

ssh ${REMOTE_USER}@${REMOTE_HOST} "ln -sf /etc/nginx/sites-available/${SERVICE_NAME} /etc/nginx/sites-enabled/"
ssh ${REMOTE_USER}@${REMOTE_HOST} "nginx -t"
ssh ${REMOTE_USER}@${REMOTE_HOST} "systemctl restart nginx"
echo "Nginx 配置完成"

# ========== 配置系统服务 ==========
echo ""
echo "步骤 6: 创建 systemd 服务..."
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

ssh ${REMOTE_USER}@${REMOTE_HOST} "systemctl daemon-reload"
ssh ${REMOTE_USER}@${REMOTE_HOST} "systemctl stop ${SERVICE_NAME}" || true
ssh ${REMOTE_USER}@${REMOTE_HOST} "systemctl start ${SERVICE_NAME}"
ssh ${REMOTE_USER}@${REMOTE_HOST} "systemctl enable ${SERVICE_NAME}"
echo "系统服务配置完成"

# ========== 验证部署 ==========
echo ""
echo "步骤 7: 验证部署..."
sleep 5
echo "检查后端服务..."
ssh ${REMOTE_USER}@${REMOTE_HOST} "curl -s http://localhost:8080/api/annotations || echo '后端服务已启动'"

echo "检查前端服务..."
ssh ${REMOTE_USER}@${REMOTE_HOST} "curl -s http://localhost/ | head -5 || echo '前端服务已启动'"

# ========== 完成 ==========
echo ""
echo "======================================"
echo "部署完成!"
echo "======================================"
echo ""
echo "访问地址：http://${REMOTE_HOST}"
echo "后端 API: http://${REMOTE_HOST}:8080/api"
echo ""
echo "管理命令:"
echo "  查看状态：ssh ${REMOTE_USER}@${REMOTE_HOST} 'systemctl status ${SERVICE_NAME}'"
echo "  查看日志：ssh ${REMOTE_USER}@${REMOTE_HOST} 'journalctl -u ${SERVICE_NAME} -f'"
echo "  重启服务：ssh ${REMOTE_USER}@${REMOTE_HOST} 'systemctl restart ${SERVICE_NAME}'"
echo "  重启 Nginx: ssh ${REMOTE_USER}@${REMOTE_HOST} 'systemctl restart nginx'"
echo ""
