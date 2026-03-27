# 卫星影像标注系统

基于 OpenLayers + Vue3 + Spring Boot 的专业级卫星影像标注系统

## 技术栈

### 前端
- Vue 3 + Vite
- Element Plus
- OpenLayers
- Pinia (状态管理)
- Vue Router
- Axios

### 后端
- Spring Boot 3.2
- JDK 17
- Spring Security + JWT
- Spring Data JPA
- PostgreSQL + PostGIS
- GeoTools (GeoTIFF 处理)

### 部署
- Nginx (静态资源服务器 + 反向代理)
- Maven (阿里云镜像)

## 功能列表

### 已实现 (基础框架)
- [x] 用户认证 (登录/注册/JWT)
- [x] RBAC 权限控制
- [x] 地图基础浏览 (缩放/平移)
- [x] 点/线/面标绘
- [x] 标注数据 CRUD API
- [x] 符号库管理 API

### 待实现 (高级功能)
- [ ] 符号管理 (SVG 上传/符号库)
- [ ] 距离/面积量测
- [ ] 要素编辑 (选择/移动/复制/删除)
- [ ] 图层管理
- [ ] 影像调整 (亮度/对比度/色阶)
- [ ] 影像卷帘/开窗对比
- [ ] 影像裁切 (GeoTIFF 输出)
- [ ] 打印功能 (比例尺/指北针/图例)
- [ ] 坐标系切换 (WGS84/CGCS2000)
- [ ] 工程文件管理

## 项目结构

```
satellite-annotation/
├── backend/                 # 后端 Spring Boot 项目
│   ├── src/main/java/
│   │   └── com/annotation/
│   │       ├── config/      # 配置类
│   │       ├── controller/  # 控制器
│   │       ├── dto/         # 数据传输对象
│   │       ├── entity/      # 实体类
│   │       ├── repository/  # 数据访问层
│   │       ├── security/    # 安全认证
│   │       └── service/     # 服务层
│   ├── src/main/resources/
│   │   └── application.yml
│   ├── pom.xml
│   └── settings.xml         # Maven 阿里云镜像配置
├── frontend/                # 前端 Vue3 项目
│   ├── src/
│   │   ├── api/             # API 接口
│   │   ├── router/          # 路由
│   │   ├── stores/          # Pinia 状态管理
│   │   ├── utils/           # 工具函数
│   │   ├── views/           # 页面组件
│   │   ├── App.vue
│   │   └── main.js
│   ├── package.json
│   └── vite.config.js
├── database/                # 数据库脚本
│   └── init.sql
├── deploy/                  # 部署脚本
│   ├── deploy-local.sh      # 本地打包上传
│   ├── deploy.sh            # 服务器部署
│   └── nginx.conf           # Nginx 配置
└── docs/                    # 文档
```

## 快速开始

### 开发环境

#### 后端启动
```bash
cd backend
# 确保 JDK 17 已安装
java -version

# 运行应用
mvn spring-boot:run
# 或打包后运行
mvn clean package
java -jar target/*.jar
```

#### 前端启动
```bash
cd frontend
npm install
npm run dev
```

#### 数据库初始化
```bash
# 安装 PostgreSQL 和 PostGIS 后
psql -U postgres -f database/init.sql
```

### 生产部署

#### 方式一：使用部署脚本
```bash
# 1. 本地打包并上传
cd deploy
chmod +x deploy-local.sh
./deploy-local.sh

# 2. 登录服务器执行部署
ssh root@192.168.200.77
cd /opt
bash deploy.sh
```

#### 方式二：手动部署
```bash
# 1. 服务器安装依赖
yum install -y java-17-openjdk postgresql15 nginx
yum install -y postgis33_15

# 2. 初始化数据库
su - postgres -c "psql -f /path/to/init.sql"

# 3. 上传并运行后端
java -jar satellite-annotation-backend.jar

# 4. 配置 Nginx
cp deploy/nginx.conf /etc/nginx/nginx.conf
systemctl restart nginx
```

## 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | ROLE_ADMIN |
| user | admin123 | ROLE_USER |

## API 接口

### 认证
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/register` - 用户注册

### 标注
- `GET /api/annotations` - 获取所有标注
- `GET /api/annotations/{id}` - 获取单个标注
- `POST /api/annotations` - 创建标注
- `PUT /api/annotations/{id}` - 更新标注
- `DELETE /api/annotations/{id}` - 删除标注

### 符号
- `GET /api/symbols` - 获取所有符号
- `POST /api/symbols/upload` - 上传 SVG 符号
- `DELETE /api/symbols/{id}` - 删除符号

## 服务器信息

- IP: 192.168.200.77
- SSH: root / box@gridknow
- 后端端口：8080
- 前端端口：80 (Nginx)

## 开发计划

### 第一阶段：基础框架 (已完成)
- [x] 项目初始化
- [x] 用户认证
- [x] 地图基础交互

### 第二阶段：核心功能
- [ ] 完整标绘功能 (带样式设置)
- [ ] 量测功能 (距离/面积/角度)
- [ ] 编辑功能 (撤销/重做/旋转)

### 第三阶段：影像处理
- [ ] GeoTIFF 加载
- [ ] 影像调整 (亮度/对比度/色阶)
- [ ] 影像卷帘/开窗

### 第四阶段：输出与完善
- [ ] 打印功能
- [ ] 工程文件管理
- [ ] 系统优化
