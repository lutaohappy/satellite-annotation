-- 货车通过性分析系统 - 数据库迁移脚本
-- 版本：v2.0
-- 日期：2026-04-13

-- 启用 PostGIS 扩展
CREATE EXTENSION IF NOT EXISTS postgis;

-- 路网数据表
CREATE TABLE IF NOT EXISTS road_networks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL COMMENT '路网名称',
    region VARCHAR(255) COMMENT '区域名称',
    min_lat DOUBLE COMMENT '最小纬度',
    min_lon DOUBLE COMMENT '最小经度',
    max_lat DOUBLE COMMENT '最大纬度',
    max_lon DOUBLE COMMENT '最大经度',
    coverage_area GEOMETRY COMMENT '覆盖范围 (PostGIS Geometry)',
    geojson_path VARCHAR(500) COMMENT 'GeoJSON 文件存储路径',
    total_roads INTEGER DEFAULT 0 COMMENT '道路总数',
    download_date TIMESTAMP COMMENT '下载日期',
    downloaded_by BIGINT COMMENT '下载用户 ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (downloaded_by) REFERENCES users(id),
    INDEX idx_region (region),
    INDEX idx_download_date (download_date)
) COMMENT='路网数据表';

-- 道路约束表
CREATE TABLE IF NOT EXISTS road_constraints (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    road_network_id BIGINT COMMENT '路网 ID',
    osm_way_id VARCHAR(50) COMMENT 'OSM 道路 ID',
    road_name VARCHAR(255) COMMENT '道路名称',
    max_height DOUBLE COMMENT '限高 (米)',
    max_width DOUBLE COMMENT '限宽 (米)',
    max_weight DOUBLE COMMENT '限重 (吨)',
    max_axle_weight DOUBLE COMMENT '轴重限制 (吨)',
    min_length DOUBLE COMMENT '长度限制 (米)',
    restriction_type VARCHAR(50) COMMENT '限制类型',
    time_restriction VARCHAR(100) COMMENT '时间限制',
    geometry GEOMETRY COMMENT '道路几何',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (road_network_id) REFERENCES road_networks(id),
    INDEX idx_road_network (road_network_id),
    INDEX idx_osm_way (osm_way_id),
    INDEX idx_restriction_type (restriction_type)
) COMMENT='道路约束表';

-- 货车分析请求表
CREATE TABLE IF NOT EXISTS truck_analysis_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_name VARCHAR(255) COMMENT '请求名称',
    start_lat DOUBLE NOT NULL COMMENT '起点纬度',
    start_lon DOUBLE NOT NULL COMMENT '起点经度',
    end_lat DOUBLE NOT NULL COMMENT '终点纬度',
    end_lon DOUBLE NOT NULL COMMENT '终点经度',
    truck_length DOUBLE COMMENT '车长 (米)',
    truck_width DOUBLE COMMENT '车宽 (米)',
    truck_height DOUBLE COMMENT '车高 (米)',
    truck_weight DOUBLE COMMENT '总重 (吨)',
    truck_axle_weight DOUBLE COMMENT '轴重 (吨)',
    wheelbase DOUBLE COMMENT '轴距 (米)',
    route_geojson TEXT COMMENT '路线 GeoJSON',
    is_passable BOOLEAN DEFAULT TRUE COMMENT '是否可通过',
    violation_points TEXT COMMENT '禁行点 JSON',
    road_network_id BIGINT COMMENT '路网 ID',
    created_by BIGINT COMMENT '创建用户 ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (road_network_id) REFERENCES road_networks(id),
    FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_road_network (road_network_id),
    INDEX idx_created_by (created_by),
    INDEX idx_created_at (created_at)
) COMMENT='货车分析请求表';
