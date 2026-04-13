#!/bin/bash
# OSRM 货车路由服务启动脚本
# 使用中国数据启动 OSRM 服务

set -e

DATA_DIR="/opt/osrm"
OSM_FILE="${DATA_DIR}/data/china-latest.osm.pbf"
PROFILE="${DATA_DIR}/truck.lua"

echo "=== OSRM 货车路由服务部署脚本 ==="

# 1. 下载 OSM 数据（如果不存在）
if [ ! -f "$OSM_FILE" ]; then
    echo "下载 OSM 数据..."
    mkdir -p ${DATA_DIR}/data
    cd ${DATA_DIR}/data

    # 下载中国数据
    wget -c https://download.geofabrik.de/asia/china-latest.osm.pbf

    # 或者下载北京区域（测试用）
    # wget -c https://download.geofabrik.de/asia/china/beijing-latest.osm.pbf
fi

# 2. 复制配置文件
cp /Users/taolu/Downloads/test/satellite-annotation/deploy/truck.lua ${DATA_DIR}/

# 3. 提取路网
echo "提取路网数据..."
docker run --rm -v ${DATA_DIR}:/data osrm/osrm-backend \
    osrm-extract -p /data/truck.lua /data/china.osm.pbf

# 4. 创建路由网络
echo "创建路由网络..."
docker run --rm -v ${DATA_DIR}:/data osrm/osrm-backend \
    osrm-partition /data/china.osrm

# 5. 自定义数据
echo "自定义数据..."
docker run --rm -v ${DATA_DIR}:/data osrm/osrm-backend \
    osrm-customize /data/china.osrm

# 6. 启动服务
echo "启动 OSRM 服务..."
# 停止旧容器
docker stop osrm-truck 2>/dev/null || true
docker rm osrm-truck 2>/dev/null || true

# 启动新容器
docker run -d --name osrm-truck \
    -v ${DATA_DIR}:/data \
    -p 5000:5000 \
    --restart=always \
    osrm/osrm-backend \
    osrm-routed --algorithm mld /data/china.osrm

echo "=== 部署完成 ==="
echo "OSRM 服务已启动在端口 5000"
echo "测试：curl 'http://localhost:5000/route/v1/driving/116.4,39.9;116.5,39.8?overview=false'"

# 等待服务启动
sleep 5

# 验证服务
echo "验证服务..."
curl -s "http://localhost:5000/route/v1/driving/116.4,39.9;116.5,39.8?overview=false" | head -100
