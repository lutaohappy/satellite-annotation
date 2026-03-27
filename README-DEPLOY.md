# 卫星影像标注系统 - 快速部署指南

## 项目结构

```
satellite-annotation/
├── backend/                    # 后端 Spring Boot 项目
│   ├── target/
│   │   └── satellite-annotation-backend-0.0.1-SNAPSHOT.jar  (73MB)
│   ├── deploy.sh              # 后端部署脚本
│   └── ...
├── frontend/                   # 前端 Vue3 项目
│   ├── dist/                  # 构建产物
│   └── ...
├── deploy-all.sh              # 完整部署脚本
└── DEPLOYMENT.md              # 详细部署文档
```

## 快速部署 (推荐)

执行一键部署脚本:

```bash
cd /Users/taolu/Downloads/test/satellite-annotation
./deploy-all.sh
```

该脚本会自动:
1. 构建前端项目
2. 构建后端项目
3. 上传到 192.168.200.77 服务器
4. 配置 Nginx 反向代理
5. 配置 systemd 系统服务
6. 启动并验证服务

## 手动部署

### 1. 部署后端

```bash
# 构建
cd backend
mvn clean package -DskipTests

# 上传 JAR
scp target/satellite-annotation-backend-0.0.1-SNAPSHOT.jar root@192.168.200.77:/opt/satellite-annotation/

# 创建服务
ssh root@192.168.200.77 "cat > /etc/systemd/system/satellite-annotation.service" << 'EOF'
[Unit]
Description=Satellite Annotation Backend
After=syslog.target network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/satellite-annotation
ExecStart=/usr/bin/java -jar -Dspring.profiles.active=prod /opt/satellite-annotation/satellite-annotation-backend-0.0.1-SNAPSHOT.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# 启动服务
ssh root@192.168.200.77 "systemctl daemon-reload && systemctl start satellite-annotation && systemctl enable satellite-annotation"
```

### 2. 部署前端

```bash
# 构建
cd frontend
npm run build

# 上传
scp -r dist/* root@192.168.200.77:/var/www/satellite-annotation/
```

### 3. 配置 Nginx

```bash
ssh root@192.168.200.77 "cat > /etc/nginx/sites-available/satellite-annotation" << 'EOF'
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
    }

    location /uploads/ {
        alias /opt/uploads/;
        add_header Access-Control-Allow-Origin *;
    }
}
EOF

ssh root@192.168.200.77 "ln -sf /etc/nginx/sites-available/satellite-annotation /etc/nginx/sites-enabled/ && nginx -t && systemctl restart nginx"
```

## 服务器要求

**目标服务器:** 192.168.200.77

**必需软件:**
- Java 17+
- PostgreSQL 14+ with PostGIS 3.0+
- Nginx
- Node.js 18+ (仅构建时需要)
- Maven 3.9+ (仅构建时需要)

## 数据库配置

在服务器上执行:

```bash
sudo -i -u postgres
psql

CREATE DATABASE satellite_annotation;
CREATE USER postgres WITH PASSWORD 'postgres';
GRANT ALL PRIVILEGES ON DATABASE satellite_annotation TO postgres;

\c satellite_annotation
CREATE EXTENSION IF NOT EXISTS postgis;

\q
exit
```

## 验证部署

```bash
# 检查后端
curl http://192.168.200.77:8080/api/annotations

# 检查前端
curl http://192.168.200.77

# 浏览器访问
http://192.168.200.77
```

## 服务管理

```bash
# 查看状态
ssh root@192.168.200.77 "systemctl status satellite-annotation"

# 查看日志
ssh root@192.168.200.77 "journalctl -u satellite-annotation -f"

# 重启后端
ssh root@192.168.200.77 "systemctl restart satellite-annotation"

# 重启 Nginx
ssh root@192.168.200.77 "systemctl restart nginx"
```

## 默认用户

系统初始化后，可以创建管理员用户:

```bash
curl -X POST http://192.168.200.77:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123","email":"admin@example.com"}'
```

## 常见问题

**Q: 数据库连接失败**
- 检查 PostgreSQL 是否运行：`systemctl status postgresql`
- 检查 pg_hba.conf 允许远程连接
- 检查防火墙是否开放 5432 端口

**Q: 端口被占用**
- 修改 application.yml 中的 server.port
- 或停止占用端口的服务

**Q: 前端无法访问 API**
- 检查 Nginx 配置
- 检查后端服务是否运行
- 检查浏览器控制台网络请求
