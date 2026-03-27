#!/bin/bash

# 服务器部署脚本
# 执行位置：/opt/deploy.sh

echo "=========================================="
echo "开始部署卫星影像标注系统"
echo "=========================================="

# 1. 安装 JDK 17
echo "正在安装 JDK 17..."
yum install -y java-17-openjdk java-17-openjdk-devel
if [ $? -eq 0 ]; then
    echo "JDK 17 安装完成"
    java -version
else
    echo "JDK 安装失败，尝试使用已有 JDK"
fi

# 2. 安装 PostgreSQL
echo "正在安装 PostgreSQL..."
yum install -y postgresql15 postgresql15-server postgresql15-contrib
if [ $? -eq 0 ]; then
    /usr/pgsql-15/bin/postgresql-15-setup --initdb
    systemctl enable postgresql-15
    systemctl start postgresql-15
    echo "PostgreSQL 安装完成"
else
    echo "PostgreSQL 可能已安装"
fi

# 3. 安装 PostGIS
echo "正在安装 PostGIS..."
yum install -y postgis33_15
if [ $? -eq 0 ]; then
    echo "PostGIS 安装完成"
else
    echo "PostGIS 可能已安装"
fi

# 4. 配置数据库
echo "正在配置数据库..."
su - postgres -c "psql -c \"CREATE DATABASE satellite_annotation;\"" 2>/dev/null || echo "数据库可能已存在"
su - postgres -c "psql -d satellite_annotation -c \"CREATE EXTENSION IF NOT EXISTS postgis;\"" 2>/dev/null || echo "PostGIS 扩展可能已启用"

# 创建用户表（如果不存在）
su - postgres -c "psql -d satellite_annotation" << 'EOF'
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_USER',
    email VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 插入默认管理员用户 (密码：admin123)
INSERT INTO users (username, password, role, email) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iDJ9yqQSqH6QEZMk7y4.xYT6qZKG', 'ROLE_ADMIN', 'admin@example.com')
ON CONFLICT (username) DO NOTHING;
EOF

echo "数据库配置完成"

# 5. 安装 Nginx
echo "正在安装 Nginx..."
yum install -y nginx
systemctl enable nginx
systemctl start nginx
echo "Nginx 安装完成"

# 6. 配置防火墙
echo "正在配置防火墙..."
firewall-cmd --permanent --add-service=http 2>/dev/null || true
firewall-cmd --permanent --add-service=https 2>/dev/null || true
firewall-cmd --permanent --add-port=8080/tcp 2>/dev/null || true
firewall-cmd --reload 2>/dev/null || true
echo "防火墙配置完成"

# 7. 启动后端应用
echo "正在启动后端应用..."
chmod +x /opt/*.jar
nohup java -jar /opt/satellite-annotation-backend-*.jar --spring.profiles.active=prod > /var/log/app.log 2>&1 &
echo "后端应用已启动"

# 8. 重启 Nginx
echo "正在重启 Nginx..."
systemctl restart nginx
echo "Nginx 已重启"

echo "=========================================="
echo "部署完成！"
echo "=========================================="
echo "应用访问地址：http://${SERVER_IP}/"
echo "后端 API 地址：http://${SERVER_IP}:8080/api"
echo "默认账号：admin / admin123"
echo "=========================================="
