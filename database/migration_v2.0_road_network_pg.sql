-- 货车通过性分析系统 - 数据库迁移脚本 (PostgreSQL)
-- 版本：v2.0
-- 日期：2026-04-13

-- 启用 PostGIS 扩展
CREATE EXTENSION IF NOT EXISTS postgis;

-- 路网数据表
CREATE TABLE IF NOT EXISTS road_networks (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    name VARCHAR(255) NOT NULL,
    region VARCHAR(255),
    min_lat DOUBLE PRECISION,
    min_lon DOUBLE PRECISION,
    max_lat DOUBLE PRECISION,
    max_lon DOUBLE PRECISION,
    coverage_area GEOMETRY,
    geojson_path VARCHAR(500),
    total_roads INTEGER DEFAULT 0,
    download_date TIMESTAMP,
    downloaded_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (downloaded_by) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_road_networks_region ON road_networks(region);
CREATE INDEX IF NOT EXISTS idx_road_networks_download_date ON road_networks(download_date);

-- 道路约束表
CREATE TABLE IF NOT EXISTS road_constraints (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    road_network_id BIGINT,
    osm_way_id VARCHAR(50),
    road_name VARCHAR(255),
    max_height DOUBLE PRECISION,
    max_width DOUBLE PRECISION,
    max_weight DOUBLE PRECISION,
    max_axle_weight DOUBLE PRECISION,
    min_length DOUBLE PRECISION,
    restriction_type VARCHAR(50),
    time_restriction VARCHAR(100),
    geometry GEOMETRY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (road_network_id) REFERENCES road_networks(id)
);

CREATE INDEX IF NOT EXISTS idx_road_constraints_network ON road_constraints(road_network_id);
CREATE INDEX IF NOT EXISTS idx_road_constraints_osm_way ON road_constraints(osm_way_id);
CREATE INDEX IF NOT EXISTS idx_road_constraints_restriction ON road_constraints(restriction_type);

-- 货车分析请求表
CREATE TABLE IF NOT EXISTS truck_analysis_requests (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    request_name VARCHAR(255),
    start_lat DOUBLE PRECISION NOT NULL,
    start_lon DOUBLE PRECISION NOT NULL,
    end_lat DOUBLE PRECISION NOT NULL,
    end_lon DOUBLE PRECISION NOT NULL,
    truck_length DOUBLE PRECISION,
    truck_width DOUBLE PRECISION,
    truck_height DOUBLE PRECISION,
    truck_weight DOUBLE PRECISION,
    truck_axle_weight DOUBLE PRECISION,
    wheelbase DOUBLE PRECISION,
    route_geojson TEXT,
    is_passable BOOLEAN DEFAULT TRUE,
    violation_points TEXT,
    road_network_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (road_network_id) REFERENCES road_networks(id),
    FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_truck_analysis_network ON truck_analysis_requests(road_network_id);
CREATE INDEX IF NOT EXISTS idx_truck_analysis_user ON truck_analysis_requests(created_by);
CREATE INDEX IF NOT EXISTS idx_truck_analysis_created_at ON truck_analysis_requests(created_at);

-- 创建触发器更新 updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_road_networks_updated_at BEFORE UPDATE ON road_networks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_road_constraints_updated_at BEFORE UPDATE ON road_constraints
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_truck_analysis_requests_updated_at BEFORE UPDATE ON truck_analysis_requests
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
