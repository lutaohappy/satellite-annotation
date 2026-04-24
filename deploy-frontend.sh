#!/bin/bash

# 前端部署脚本
# 用法：./deploy-frontend.sh

set -e

echo "=== 前端部署脚本 ==="

# 1. 构建前端
echo "[1/3] 构建前端..."
cd frontend
npm run build
cd ..

# 2. 复制到服务器
echo "[2/3] 复制文件到服务器..."

# 方式 1: 如果有 SSH 密钥
if [ -f ~/.ssh/id_rsa ]; then
    echo "使用 SSH 密钥部署到 192.168.200.77..."
    ssh root@192.168.200.77 "mkdir -p /opt/html/gis/assets"
    scp -o StrictHostKeyChecking=no frontend/dist/assets/* root@192.168.200.77:/opt/html/gis/assets/
    scp -o StrictHostKeyChecking=no frontend/dist/index.html root@192.168.200.77:/opt/html/gis/
    scp -o StrictHostKeyChecking=no frontend/dist/index.html root@192.168.200.77:/opt/html/
    echo "部署完成！"
else
    echo "未找到 SSH 密钥，请手动复制以下文件："
    echo "  scp frontend/dist/assets/* root@192.168.200.77:/opt/html/gis/assets/"
    echo "  scp frontend/dist/index.html root@192.168.200.77:/opt/html/gis/"
    echo "  scp frontend/dist/index.html root@192.168.200.77:/opt/html/"
fi

echo "[3/3] 完成！"
