# OSRM 手动部署指南

## 当前状态

✅ **已完成：**
- 数据库迁移成功执行
- PostGIS 扩展已启用
- 新增表：`road_networks`, `road_constraints`, `truck_analysis_requests`
- 北京区域 OSM 数据已下载：`/opt/osrm/data/beijing-latest.osm.pbf`
- Podman 容器环境已安装

❌ **待完成：**
- OSRM 镜像无法从 Docker Hub 拉取（服务器网络限制）

## OSRM 部署方案

### 方案一：本地导入镜像（推荐）

在本地计算机执行：

```bash
# 1. 保存 OSRM 镜像到文件
docker pull osrm/osrm-backend:latest
docker save osrm/osrm-backend:latest -o osrm-image.tar.gz

# 2. 上传到服务器
sshpass -p 'box@gridknow' scp osrm-image.tar.gz root@192.168.200.77:/opt/osrm/

# 3. 在服务器上加载镜像
ssh root@192.168.200.77
podman load -i /opt/osrm/osrm-image.tar.gz

# 4. 运行 OSRM 容器
podman run -d --name osrm-truck \
  -v /opt/osrm:/data \
  -p 5000:5000 \
  --restart=always \
  osrm/osrm-backend \
  osrm-routed --algorithm mld /data/beijing.osrm
```

### 方案二：使用国内镜像源

```bash
ssh root@192.168.200.77

# 配置 Docker Hub 镜像加速
cat > /etc/containers/registries.conf.d/mirror.conf <<EOF
[[registry]]
prefix = "docker.io"
location = "registry.cn-hangzhou.aliyuncs.com/osrm"
EOF

# 拉取镜像
podman pull registry.cn-hangzhou.aliyuncs.com/osrm/osrm-backend:latest
```

### 方案三：手动编译 OSRM（备选）

如果无法使用镜像，可以手动编译：

```bash
ssh root@192.168.200.77

# 安装依赖
yum install -y gcc gcc-c++ cmake libbz2-dev libxml2-dev libzip-dev boost-devel

# 下载源码
cd /opt
git clone https://github.com/Project-OSRM/osrm-backend.git
cd osrm-backend

# 编译
mkdir build && cd build
cmake ..
make -j$(nproc)
make install

# 处理数据
cd /opt/osrm
osrm-extract -p /opt/truck.lua /data/beijing.osm.pbf
osrm-partition /data/beijing.osrm
osrm-customize /data/beijing.osrm

# 启动服务
osrm-routed --algorithm mld /data/beijing.osrm --port 5000
```

## 验证部署

```bash
# 测试 OSRM 服务
curl "http://192.168.200.77:5000/route/v1/driving/116.4,39.9;116.5,39.8?overview=false"

# 期望输出
{"code":"Ok","routes":[{"distance":1234.5,"duration":67.8}]}
```

## 后端服务重启

OSRM 部署完成后，重启后端服务：

```bash
ssh root@192.168.200.77

# 如果有 systemd
systemctl restart satellite-annotation

# 或者手动重启
cd /opt/app
pkill -f satellite-annotation || true
nohup java -jar satellite-annotation-backend-*.jar --spring.profiles.active=prod > app.log 2>&1 &
```

## 前端访问

部署完成后访问：
- 前端地址：http://192.168.200.77/gis/
- 路网下载：点击工具栏"路网下载"按钮
- 货车分析：点击工具栏"货车分析"按钮

## 数据库表结构

```sql
-- 查看表结构
\d road_networks
\d road_constraints
\d truck_analysis_requests
```

## 联系信息

如有问题，请联系运维团队协助部署 OSRM 服务。
