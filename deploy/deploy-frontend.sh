#!/bin/bash
# 前端部署脚本 - 将构建的文件复制到服务器

SERVER="192.168.200.77"
USER="root"
PASSWORD="box@gridknow"
REMOTE_DIR="/opt/html"
REMOTE_GIS_DIR="/opt/html/gis"
LOCAL_DIST="/Users/taolu/Downloads/test/satellite-annotation/frontend/dist"

echo "=== 前端部署脚本 ==="
echo "本地目录：$LOCAL_DIST"
echo "服务器：$SERVER"
echo "远程目录：$REMOTE_DIR 和 $REMOTE_GIS_DIR"
echo "SSH 账号：$USER"

# 检查本地构建是否存在
if [ ! -d "$LOCAL_DIST" ]; then
    echo "错误：本地构建目录不存在，请先运行 npm run build"
    exit 1
fi

# 使用 sshpass 复制文件（如果已安装）
if command -v sshpass &> /dev/null; then
    echo "正在复制文件到 $REMOTE_DIR..."
    sshpass -p "$PASSWORD" scp -o StrictHostKeyChecking=no -r $LOCAL_DIST/* $USER@$SERVER:$REMOTE_DIR/

    echo "正在复制文件到 $REMOTE_GIS_DIR..."
    sshpass -p "$PASSWORD" scp -o StrictHostKeyChecking=no -r $LOCAL_DIST/* $USER@$SERVER:$REMOTE_GIS_DIR/
else
    echo "未安装 sshpass，使用普通 scp (需要手动输入密码)"
    echo "请手动复制文件到以下两个目录："
    echo "  scp -r $LOCAL_DIST/* $USER@$SERVER:$REMOTE_DIR/"
    echo "  scp -r $LOCAL_DIST/* $USER@$SERVER:$REMOTE_GIS_DIR/"
fi

if [ $? -eq 0 ]; then
    echo "文件复制成功！"
    echo ""
    echo "请在服务器上执行以下命令重启 Nginx："
    echo "  nginx -s reload"
else
    echo "文件复制失败"
fi
