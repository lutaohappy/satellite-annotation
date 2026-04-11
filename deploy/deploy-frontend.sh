#!/bin/bash
# 前端部署脚本 - 将构建的文件复制到服务器

SERVER="192.168.200.77"
REMOTE_DIR="/opt/html"
LOCAL_DIST="/Users/taolu/Downloads/test/satellite-annotation/frontend/dist"

echo "=== 前端部署脚本 ==="
echo "本地目录：$LOCAL_DIST"
echo "服务器：$SERVER"
echo "远程目录：$REMOTE_DIR"

# 检查本地构建是否存在
if [ ! -d "$LOCAL_DIST" ]; then
    echo "错误：本地构建目录不存在，请先运行 npm run build"
    exit 1
fi

# 使用 scp 复制文件
echo "正在复制文件..."
scp -r $LOCAL_DIST/* root@$SERVER:$REMOTE_DIR/

if [ $? -eq 0 ]; then
    echo "文件复制成功！"
    echo ""
    echo "请在服务器上执行以下命令重启 Nginx："
    echo "  nginx -s reload"
    echo ""
    echo "或者更新 Nginx 配置以禁用缓存："
    echo "  见 nginx-cache-fix.conf 文件"
else
    echo "文件复制失败，请检查网络连接和 SSH 凭据"
fi
