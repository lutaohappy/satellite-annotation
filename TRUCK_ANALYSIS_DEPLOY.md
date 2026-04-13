# 货车通过性分析系统 - 部署指南

## 一、服务器信息

| 项目 | 值 |
|------|-----|
| IP 地址 | `192.168.200.77` |
| SSH 账号 | `root` |
| SSH 密码 | `box@gridknow` |
| 部署目录 | `/var/www/satellite-annotation/` |

---

## 二、部署步骤

### 2.1 安装 Docker（如果未安装）

```bash
ssh root@192.168.200.77

# 安装 Docker
curl -fsSL https://get.docker.com | sh

# 启动 Docker
systemctl enable docker
systemctl start docker

# 验证安装
docker --version
```

### 2.2 下载 OSM 数据

```bash
# 创建 OSRM 数据目录
mkdir -p /opt/osrm/data
cd /opt/osrm/data

# 下载中国数据（约 1GB）
wget https://download.geofabrik.de/asia/china-latest.osm.pbf

# 或者下载北京区域（更快）
wget https://download.geofabrik.de/asia/china/beijing-latest.osm.pbf
```

### 2.3 运行 OSRM 容器

```bash
# 提取路网数据（使用货车配置文件）
docker run --rm -v /opt/osrm:/data osrm/osrm-backend osrm-extract -p /opt/car.lua /data/china.osm.pbf

# 创建路由网络
docker run --rm -v /opt/osrm:/data osrm/osrm-backend osrm-partition /data/china.osrm

# 自定义路由数据
docker run --rm -v /opt/osrm:/data osrm/osrm-backend osrm-customize /data/china.osrm

# 启动 OSRM 路由服务
docker run -d --name osrm \
  -v /opt/osrm:/data \
  -p 5000:5000 \
  --restart=always \
  osrm/osrm-backend \
  osrm-routed --algorithm mld /data/china.osrm
```

### 2.4 验证 OSRM 服务

```bash
# 测试路线规划 API
curl "http://localhost:5000/route/v1/driving/116.4,39.9;116.5,39.8?overview=false"

# 应该返回类似：
# {"code":"Ok","routes":[{"distance":1234.5,"duration":67.8}]}
```

### 2.5 部署后端服务

```bash
# 1. 构建后端
cd /Users/taolu/Downloads/test/satellite-annotation/backend
mvn clean package -DskipTests

# 2. 上传 JAR 到服务器
scp target/satellite-annotation-backend-*.jar root@192.168.200.77:/opt/app/

# 3. 重启服务
ssh root@192.168.200.77
cd /opt/app
pkill -f satellite-annotation
nohup java -jar satellite-annotation-backend-*.jar > app.log 2>&1 &
```

### 2.6 部署前端

```bash
# 1. 构建前端
cd /Users/taolu/Downloads/test/satellite-annotation/frontend
npm run build

# 2. 上传到服务器
sshpass -p 'box@gridknow' scp -r frontend/dist/* root@192.168.200.77:/var/www/satellite-annotation/

# 3. 重启 Nginx
sshpass -p 'box@gridknow' ssh root@192.168.200.77 "nginx -s reload"
```

---

## 三、数据库迁移

```bash
# 连接到数据库
mysql -h localhost -u root -p satellite_annotation

# 执行迁移脚本
source /path/to/migration_v2.0_road_network.sql
```

---

## 四、配置文件修改

### 4.1 application.yml

```yaml
# 添加 OSRM 配置
osrm:
  base-url: http://localhost:5000
  
# Overpass API 配置
overpass:
  api-url: https://overpass-api.de/api/interpreter
  data-dir: data/road_networks/
```

### 4.2 Nginx 配置

```nginx
# 添加 OSRM 反向代理
location /osrm/ {
    proxy_pass http://localhost:5000/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

---

## 五、快速部署脚本

```bash
#!/bin/bash
# deploy.sh - 一键部署脚本

set -e

SERVER="192.168.200.77"
USER="root"
PASSWORD="box@gridknow"

echo "=== 构建前端 ==="
cd /Users/taolu/Downloads/test/satellite-annotation/frontend
npm run build

echo "=== 上传前端文件 ==="
sshpass -p "$PASSWORD" scp -r frontend/dist/* ${USER}@${SERVER}:/var/www/satellite-annotation/

echo "=== 构建后端 ==="
cd /Users/taolu/Downloads/test/satellite-annotation/backend
mvn clean package -DskipTests

echo "=== 上传后端 JAR ==="
sshpass -p "$PASSWORD" scp target/satellite-annotation-backend-*.jar ${USER}@${SERVER}:/opt/app/

echo "=== 重启服务 ==="
sshpass -p "$PASSWORD" ssh ${USER}@${SERVER} << 'EOF'
cd /opt/app
pkill -f satellite-annotation || true
nohup java -jar satellite-annotation-backend-*.jar > app.log 2>&1 &
sleep 3
nginx -s reload
EOF

echo "=== 部署完成 ==="
echo "访问地址：http://192.168.200.77/gis/"
echo "OSRM API: http://192.168.200.77/osrm/"
```

---

## 六、验证清单

### 6.1 OSRM 服务验证
- [ ] OSRM 容器运行正常：`docker ps | grep osrm`
- [ ] 路线规划 API 可用：`curl http://localhost:5000/route/v1/driving/116.4,39.9;116.5,39.8`

### 6.2 后端服务验证
- [ ] 应用启动成功
- [ ] 路网下载接口可用：`POST /api/road-networks/download`
- [ ] 分析接口可用：`POST /api/truck-analysis/analyze`

### 6.3 前端功能验证
- [ ] 路网下载组件显示正常
- [ ] 货车分析面板显示正常
- [ ] 可以在地图上选择起点和终点
- [ ] 分析结果正确显示

---

## 七、常见问题

### 7.1 OSRM 启动失败

```bash
# 查看日志
docker logs osrm

# 内存不足
# 解决：使用更小的区域数据或增加服务器内存
```

### 7.2 Overpass API 超时

```bash
# Overpass 公共 API 有限流
# 解决：使用更小的区域或搭建本地 Overpass 实例
```

### 7.3 路线规划失败

```bash
# 检查 OSRM 是否正常运行
curl http://localhost:5000/route/v1/driving/116.4,39.9;116.5,39.8

# 如果返回 404，检查数据文件是否正确生成
ls -la /opt/osrm/
```

---

## 八、性能优化建议

1. **OSRM 性能**：
   - 使用 SSD 存储数据文件
   - 分配足够内存（中国全境约需 16-32GB）
   - 使用 `mld` 算法（默认）

2. **Overpass 缓存**：
   - 缓存已下载的路网数据
   - 避免重复请求相同区域

3. **前端优化**：
   - 路线 GeoJSON 简化显示
   - 禁行点聚合显示
