# 卫星影像标注系统 - 部署说明

## 服务器信息

- **IP 地址**: 192.168.200.77
- **用户名**: root
- **密码**: box@gridknow
- **SSH 命令**: `ssh root@192.168.200.77`

## 目录结构

### 服务器目录
```
/opt/
├── html/              # 前端静态文件（根路径）
├── html/gis/          # 前端静态文件（/gis/路径）
├── satellite-annotation-backend-0.0.1-SNAPSHOT.jar  # 后端 JAR
├── satellite-annotation/
│   └── data/road_networks/  # 路网数据目录
└── uploads/           # 上传文件目录
```

### 本地目录
```
/Users/taolu/Downloads/test/satellite-annotation/
├── frontend/          # 前端项目
├── backend/           # 后端项目
├── deploy/            # 部署脚本
└── DEPLOYMENT.md      # 本文件
```

## 部署流程

### 方式一：使用部署脚本（推荐）

#### 1. 前端部署
```bash
cd /Users/taolu/Downloads/test/satellite-annotation

# 构建前端
cd frontend
npm run build

# 使用脚本部署
cd ../deploy
./deploy-frontend.sh
```

#### 2. 后端部署
```bash
cd /Users/taolu/Downloads/test/satellite-annotation

# 构建后端
cd backend
mvn package -DskipTests

# 使用脚本部署
cd ../deploy
./deploy-backend.sh
```

### 方式二：手动部署

#### 前端部署
```bash
# 1. 本地构建
cd /Users/taolu/Downloads/test/satellite-annotation/frontend
npm run build

# 2. 上传到服务器（使用 sshpass）
sshpass -p 'box@gridknow' scp -o StrictHostKeyChecking=no -r dist/* root@192.168.200.77:/opt/html/
sshpass -p 'box@gridknow' scp -o StrictHostKeyChecking=no -r dist/* root@192.168.200.77:/opt/html/gis/

# 3. 重启 Nginx
sshpass -p 'box@gridknow' ssh -o StrictHostKeyChecking=no root@192.168.200.77 "nginx -s reload"
```

#### 后端部署
```bash
# 1. 本地构建
cd /Users/taolu/Downloads/test/satellite-annotation/backend
mvn package -DskipTests

# 2. 上传 JAR 到服务器
sshpass -p 'box@gridnow' scp -o StrictHostKeyChecking=no target/satellite-annotation-backend-0.0.1-SNAPSHOT.jar root@192.168.200.77:/opt/

# 3. 重启后端（必须使用 prod profile）
sshpass -p 'box@gridknow' ssh -o StrictHostKeyChecking=no root@192.168.200.77 "
  ps aux | grep java | grep -v grep | awk '{print \$2}' | xargs kill
  sleep 2
  nohup java -jar /opt/satellite-annotation-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod > /opt/backend.log 2>&1 &
"

# 4. 检查启动状态
sshpass -p 'box@gridknow' ssh -o StrictHostKeyChecking=no root@192.168.200.77 "tail -30 /opt/backend.log"
```

## 重要配置

### 后端启动参数
**必须使用 prod profile 启动**，否则路网数据路径会错误：
```bash
java -jar satellite-annotation-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

### 环境变量说明

| Profile | 路网数据路径 |
|---------|-------------|
| dev (默认) | /Users/taolu/Downloads/test/satellite-annotation/backend/data/road_networks/ |
| prod | /opt/satellite-annotation/data/road_networks/ |

### Nginx 配置
```nginx
# 静态资源
location /gis/assets/ {
    alias /opt/html/gis/assets/;
}

location /gis/ {
    alias /opt/html/gis/;
    index index.html;
    try_files $uri $uri/ /gis/index.html;
}

# API 代理
location /api/ {
    proxy_pass http://127.0.0.1:4000/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header Authorization $http_authorization;
}
```

## 常用命令

### 查看后端日志
```bash
ssh root@192.168.200.77 "tail -100 /opt/backend.log"
```

### 重启后端
```bash
ssh root@192.168.200.77 "
  ps aux | grep java | grep -v grep | awk '{print \$2}' | xargs kill
  sleep 2
  nohup java -jar /opt/satellite-annotation-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod > /opt/backend.log 2>&1 &
"
```

### 重启 Nginx
```bash
ssh root@192.168.200.77 "nginx -s reload"
```

### 查看路网数据
```bash
ssh root@192.168.200.77 "ls -la /opt/satellite-annotation/data/road_networks/"
```

## 版本信息

- 前端版本：v3.1.1 (20260424-修复路网缩放问题)
- 后端版本：Spring Boot 3.2.0
- 数据库：PostgreSQL
- 地图服务器：OSRM (端口 5000)

## 注意事项

1. **后端必须使用 prod profile 启动**，否则路网数据路径错误
2. **前端需要部署到两个目录**：`/opt/html/` 和 `/opt/html/gis/`
3. **sshpass 需要预先安装**：`brew install sshpass` (macOS)
4. **部署前端后需要刷新浏览器缓存**：Ctrl+Shift+R

## 货车分析功能说明

### 数据流程
1. 创建分析 → 生成 UUID 会话 ID
2. 打点选择起点终点 → 前端缓存坐标
3. 计算分析 → 后端计算并返回结果（**不保存到数据库**）
4. 点击保存结果 → 后端保存到数据库（**仅此一次**）
5. 重算后保存 → 更新同一条记录（**不会创建新记录**）

### UUID 约束
- 同一个 UUID 会话下的分析记录，保存时会更新现有记录
- 只有点击"创建分析"才会生成新的 UUID
- 点击"清空"会清除当前 UUID 和所有记录
