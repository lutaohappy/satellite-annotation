#!/bin/bash

# 卫星影像标注系统 - 部署脚本
# 服务器：192.168.200.77

SERVER_IP="192.168.200.77"
SERVER_USER="root"
SERVER_PASS="box@gridknow"

echo "=========================================="
echo "卫星影像标注系统 - 部署脚本"
echo "=========================================="

# 1. 检查 sshpass 是否安装
if ! command -v sshpass &> /dev/null; then
    echo "错误：请先安装 sshpass (brew install sshpass)"
    exit 1
fi

# 2. 后端打包
echo "正在打包后端项目..."
cd backend
mvn clean package -DskipTests -s settings.xml 2>/dev/null || mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "后端打包失败！"
    exit 1
fi
echo "后端打包完成"

# 3. 前端打包
echo "正在打包前端项目..."
cd ../frontend
npm install 2>/dev/null
npm run build
if [ $? -ne 0 ]; then
    echo "前端打包失败！"
    exit 1
fi
echo "前端打包完成"

# 4. 上传到服务器
echo "正在上传文件到服务器..."
cd ../deploy

# 创建上传脚本
cat > upload.sh << EOF
#!/bin/bash
sshpass -p '${SERVER_PASS}' scp -o StrictHostKeyChecking=no ../backend/target/*.jar ${SERVER_USER}@${SERVER_IP}:/opt/
sshpass -p '${SERVER_PASS}' scp -o StrictHostKeyChecking=no -r ../frontend/dist ${SERVER_USER}@${SERVER_IP}:/opt/html/
sshpass -p '${SERVER_PASS}' scp -o StrictHostKeyChecking=no nginx.conf ${SERVER_USER}@${SERVER_IP}:/etc/nginx/
sshpass -p '${SERVER_PASS}' scp -o StrictHostKeyChecking=no deploy.sh ${SERVER_USER}@${SERVER_IP}:/opt/
echo "文件上传完成"
EOF

chmod +x upload.sh
./upload.sh

echo "=========================================="
echo "部署文件上传完成！"
echo "请登录服务器执行：ssh root@${SERVER_IP}"
echo "然后执行：cd /opt && bash deploy.sh"
echo "=========================================="
