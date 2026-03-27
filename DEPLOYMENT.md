# 卫星影像标注系统 - 部署说明

## 服务器环境要求

### 192.168.200.77 服务器

**必需软件:**
- Java 17 (OpenJDK)
- PostgreSQL 14+
- PostGIS 3.0+

**可选软件:**
- Nginx (用于反向代理和静态文件服务)

## 一、数据库配置

### 1. 安装 PostgreSQL 和 PostGIS

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install postgresql postgresql-contrib postgis postgresql-14-postgis-3

# CentOS/RHEL
sudo yum install postgresql postgresql-server postgis postgresql-contrib
```

### 2. 创建数据库和用户

```bash
# 切换到 postgres 用户
sudo -i -u postgres

# 进入 PostgreSQL
psql

# 创建数据库
CREATE DATABASE satellite_annotation;

# 创建用户
CREATE USER postgres WITH PASSWORD 'postgres';

# 授权
GRANT ALL PRIVILEGES ON DATABASE satellite_annotation TO postgres;

# 启用 PostGIS 扩展
\c satellite_annotation
CREATE EXTENSION IF NOT EXISTS postgis;

# 退出
\q
exit
```

## 二、后端部署

### 1. 上传 JAR 文件

将打包好的 JAR 文件上传到服务器：

```bash
# 创建应用目录
sudo mkdir -p /opt/satellite-annotation
sudo mkdir -p /opt/uploads/images
sudo mkdir -p /opt/uploads/tiles
sudo mkdir -p /opt/uploads/symbols

# 上传 JAR (从本地执行)
scp target/satellite-annotation-backend-0.0.1-SNAPSHOT.jar user@192.168.200.77:/opt/satellite-annotation/
```

### 2. 创建系统服务

创建 systemd 服务文件：

```bash
sudo nano /etc/systemd/system/satellite-annotation.service
```

内容如下：

```ini
[Unit]
Description=Satellite Annotation Backend
After=syslog.target network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/satellite-annotation
ExecStart=/usr/bin/java -jar -Dspring.profiles.active=prod satellite-annotation-backend-0.0.1-SNAPSHOT.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### 3. 启动服务

```bash
# 重新加载 systemd
sudo systemctl daemon-reload

# 启动服务
sudo systemctl start satellite-annotation

# 设置开机自启
sudo systemctl enable satellite-annotation

# 查看状态
sudo systemctl status satellite-annotation
```

### 4. 配置防火墙

```bash
# 开放端口
sudo ufw allow 8080/tcp

# 或者使用 firewall-cmd (CentOS/RHEL)
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
```

## 三、前端部署

### 1. 修改 API 配置

编辑前端配置文件，将 API 地址指向后端服务器：

```javascript
// frontend/vite.config.js 或 frontend/.env.production
VITE_API_BASE_URL=http://192.168.200.77:8080/api
```

### 2. 构建前端

```bash
cd frontend
npm run build
```

### 3. 部署到 Nginx

```bash
# 创建 Nginx 目录
sudo mkdir -p /var/www/satellite-annotation

# 上传构建产物
scp -r dist/* user@192.168.200.77:/var/www/satellite-annotation/
```

### 4. 配置 Nginx

```bash
sudo nano /etc/nginx/sites-available/satellite-annotation
```

内容如下：

```nginx
server {
    listen 80;
    server_name 192.168.200.77;

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
```

启用配置并重启 Nginx：

```bash
sudo ln -s /etc/nginx/sites-available/satellite-annotation /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

## 四、验证部署

### 1. 检查后端服务

```bash
curl http://localhost:8080/api/health
```

### 2. 访问应用

浏览器访问：http://192.168.200.77

## 五、日志查看

```bash
# 查看服务日志
sudo journalctl -u satellite-annotation -f

# 查看 Nginx 日志
sudo tail -f /var/log/nginx/access.log
sudo tail -f /var/log/nginx/error.log
```

## 六、常见问题

### 1. 数据库连接失败

检查 PostgreSQL 配置允许远程连接：

```bash
# 编辑 postgresql.conf
sudo nano /etc/postgresql/14/main/postgresql.conf
# 修改：listen_addresses = '*'

# 编辑 pg_hba.conf
sudo nano /etc/postgresql/14/main/pg_hba.conf
# 添加：host    all             all             0.0.0.0/0               md5

# 重启 PostgreSQL
sudo systemctl restart postgresql
```

### 2. 端口被占用

修改 application.yml 中的端口配置：

```yaml
server:
  port: 8081  # 改为其他端口
```

## 七、更新部署

### 后端更新

```bash
# 停止服务
sudo systemctl stop satellite-annotation

# 备份旧版本
cd /opt/satellite-annotation
cp satellite-annotation-backend-0.0.1-SNAPSHOT.jar satellite-annotation-backend-0.0.1-SNAPSHOT.jar.bak

# 上传新版本并重启
# ...上传新 JAR 文件...
sudo systemctl start satellite-annotation
```

### 前端更新

```bash
# 构建新版本
npm run build

# 上传并替换
scp -r dist/* user@192.168.200.77:/var/www/satellite-annotation/
```
