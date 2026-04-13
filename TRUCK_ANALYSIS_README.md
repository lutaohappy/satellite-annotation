# 货车通过性分析系统 v2.1

## 功能概述

本系统在原有卫星影像标注功能基础上，新增了货车通过性分析功能，主要包括：

1. **路网数据下载**：从 OpenStreetMap 下载指定区域的道路网络数据
2. **路网标注**：在路网上标注特殊属性标签（限高、限重、限宽等）
3. **货车通过性分析**：输入起点、终点和货车参数，分析路线是否可通过

---

## 核心功能

### 1. 路网数据下载

从 OpenStreetMap 的 Overpass API 下载道路网络数据，支持：
- 多种道路类型：motorway, trunk, primary, secondary, tertiary, residential
- 道路约束信息：maxheight, maxweight, maxwidth, maxlength
- 通行限制：hgv=no, motor_vehicle=no

**使用方式**：
```javascript
import { downloadRoadNetwork } from '@/api/roadNetwork'

const result = await downloadRoadNetwork({
  name: '北京市朝阳区路网',
  region: '北京市',
  minLat: 39.8,
  minLon: 116.2,
  maxLat: 40.1,
  maxLon: 116.6
})
```

### 2. 路网约束标注

支持手动添加或编辑道路约束信息：
- 限高（米）
- 限重（吨）
- 限宽（米）
- 限长（米）
- 轴重限制（吨）
- 时间限制

### 3. 货车通过性分析

**输入**：
- 起点 A（经纬度）
- 终点 B（经纬度）
- 货车参数：
  - 车长（米）
  - 车宽（米）
  - 车高（米）
  - 总重（吨）
  - 轴距（米）

**输出**：
1. A-B 行驶路线（GeoJSON）
2. 通过性分析结果
3. 禁行点列表（位置、原因、详情）
4. 总距离和预计时间

**使用方式**：
```javascript
import { analyzeTruck } from '@/api/truckAnalysis'

const result = await analyzeTruck({
  requestName: '测试路线',
  startLat: 39.9,
  startLon: 116.4,
  endLat: 39.8,
  endLon: 116.5,
  roadNetworkId: 1,
  truck: {
    length: 15,
    width: 2.5,
    height: 3.5,
    weight: 20,
    wheelbase: 8
  }
})

// result.isPassable: true/false
// result.violations: [{ lat, lon, reason, detail }]
// result.routeGeoJson: 路线 GeoJSON
```

---

## 技术架构

### 后端技术栈
- Spring Boot 3.2 + Java 17
- PostgreSQL + PostGIS（空间数据）
- OSRM（路线规划）
- Overpass API（OSM 数据下载）

### 前端技术栈
- Vue 3 + Vite
- OpenLayers 8（地图渲染）
- Element Plus（UI 组件）
- Pinia（状态管理）

### 数据库设计

```
road_networks           - 路网数据表
  ├── id
  ├── name              - 路网名称
  ├── region            - 区域
  ├── min_lat/lon       - bounding box
  ├── max_lat/lon
  ├── geojson_path      - GeoJSON 文件路径
  └── total_roads       - 道路总数

road_constraints        - 道路约束表
  ├── id
  ├── road_network_id
  ├── osm_way_id        - OSM 道路 ID
  ├── road_name
  ├── max_height        - 限高
  ├── max_weight        - 限重
  ├── max_width         - 限宽
  └── restriction_type  - 限制类型

truck_analysis_requests - 分析请求表
  ├── id
  ├── start/end lat/lon
  ├── truck_length/width/height/weight
  ├── route_geojson     - 路线 GeoJSON
  ├── is_passable       - 是否可通过
  └── violation_points  - 禁行点 JSON
```

---

## API 接口

### 路网管理

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/road-networks/download` | POST | 下载路网数据 |
| `/api/road-networks` | GET | 获取路网列表 |
| `/api/road-networks/{id}` | GET | 获取路网详情 |
| `/api/road-networks/region/{region}` | GET | 按区域查询 |
| `/api/road-networks/{id}` | DELETE | 删除路网 |

### 货车分析

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/truck-analysis/analyze` | POST | 执行通过性分析 |
| `/api/truck-analysis/history` | GET | 获取分析历史 |
| `/api/truck-analysis/{id}` | GET | 获取分析详情 |

---

## 部署步骤

### 1. 部署 OSRM 服务

```bash
# 在 77 服务器上执行
cd /opt/osrm

# 下载数据
wget https://download.geofabrik.de/asia/china-latest.osm.pbf

# 启动 OSRM 容器
docker run -d --name osrm-truck \
  -v /opt/osrm:/data \
  -p 5000:5000 \
  osrm/osrm-backend \
  osrm-routed --algorithm mld /data/china.osrm
```

### 2. 执行数据库迁移

```sql
source database/migration_v2.0_road_network.sql
```

### 3. 构建并部署后端

```bash
cd backend
mvn clean package -DskipTests
scp target/*.jar root@192.168.200.77:/opt/app/
```

### 4. 构建并部署前端

```bash
cd frontend
npm run build
sshpass -p 'box@gridknow' scp -r dist/* root@192.168.200.77:/var/www/satellite-annotation/
```

详细部署指南请参考：[TRUCK_ANALYSIS_DEPLOY.md](TRUCK_ANALYSIS_DEPLOY.md)

---

## 使用说明

### 步骤 1：下载路网数据

1. 打开系统，进入"路网管理"页面
2. 点击"下载路网数据"
3. 输入路网名称和区域
4. 在地图上框选目标区域
5. 点击"下载"，等待下载完成

### 步骤 2：标注路网约束（可选）

1. 选择已下载的路网
2. 点击"编辑约束"
3. 在地图上选择道路段
4. 添加限高、限重等约束信息
5. 保存

### 步骤 3：执行通过性分析

1. 打开"货车分析"面板
2. 在地图上选择起点和终点
3. 输入货车参数（长、宽、高、重、轴距）
4. 选择路网数据（可选，用于更精确的分析）
5. 点击"开始分析"
6. 查看分析结果和禁行点

---

## 分析算法

### 通过性检查

1. **限高检查**：`truck.height <= road.maxHeight`
2. **限重检查**：`truck.weight <= road.maxWeight`
3. **限宽检查**：`truck.width <= road.maxWidth`
4. **限长检查**：`truck.length <= road.maxLength`
5. **禁行检查**：`hgv=no` 或 `motor_vehicle=no`
6. **转弯半径检查**：`R = wheelbase / sin(35°)`

### 转弯半径计算

```java
// 基于 Ackermann 转向几何
public double calculateMinTurningRadius(double wheelbase) {
    double maxSteeringAngleRad = Math.toRadians(35);
    return wheelbase / Math.sin(maxSteeringAngleRad);
}
```

---

## 文件清单

### 后端文件
| 文件 | 说明 |
|------|------|
| `entity/RoadNetwork.java` | 路网实体 |
| `entity/RoadConstraint.java` | 道路约束实体 |
| `entity/TruckAnalysisRequest.java` | 分析请求实体 |
| `repository/*.java` | 数据访问层 |
| `service/OverpassService.java` | Overpass API 服务 |
| `service/RoadNetworkService.java` | 路网数据服务 |
| `service/TruckAnalysisService.java` | 通过性分析服务 |
| `controller/RoadNetworkController.java` | 路网管理接口 |
| `controller/TruckAnalysisController.java` | 分析接口 |
| `dto/*.java` | 数据传输对象 |

### 前端文件
| 文件 | 说明 |
|------|------|
| `api/roadNetwork.js` | 路网 API |
| `api/truckAnalysis.js` | 分析 API |
| `components/RoadNetworkDownloader.vue` | 路网下载组件 |
| `components/TruckAnalysisPanel.vue` | 分析面板 |

### 部署文件
| 文件 | 说明 |
|------|------|
| `database/migration_v2.0_road_network.sql` | 数据库迁移脚本 |
| `deploy/truck.lua` | OSRM 货车配置文件 |
| `deploy/setup-osrm.sh` | OSRM 部署脚本 |
| `TRUCK_ANALYSIS_DEPLOY.md` | 部署指南 |

---

## 注意事项

1. **Overpass API 限流**：公共 API 有请求限制，建议缓存已下载的路网数据
2. **OSRM 性能**：大范围路网需要较大内存，建议使用 SSD 存储
3. **数据准确性**：OSM 数据可能不完整，需要支持手动标注补充
4. **法规参考**：系统仅提供技术参考，实际通行需遵守当地法规

---

## 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v2.1 | 2026-04-13 | 新增货车通过性分析功能 |
| v2.0 | 2026-04-11 | 批量上传、影像调整、批次管理 |
| v1.0 | - | 初始版本（卫星影像标注） |

---

## 开发团队

开发完成时间：2026-04-13
