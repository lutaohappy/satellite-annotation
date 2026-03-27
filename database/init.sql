-- 创建数据库
CREATE DATABASE satellite_annotation;

-- 启用 PostGIS 扩展
\c satellite_annotation
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- 创建用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_USER',
    email VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建标注表
CREATE TABLE IF NOT EXISTS annotations (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    geometry TEXT,
    properties TEXT,
    category VARCHAR(100),
    symbol_id VARCHAR(255),
    user_id BIGINT REFERENCES users(id),
    project_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建项目表
CREATE TABLE IF NOT EXISTS projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    user_id BIGINT REFERENCES users(id) NOT NULL,
    crs VARCHAR(50) DEFAULT 'EPSG:3857',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建符号表
CREATE TABLE IF NOT EXISTS symbols (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    content TEXT,
    file_path VARCHAR(500),
    size INTEGER,
    user_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_annotations_user_id ON annotations(user_id);
CREATE INDEX idx_annotations_project_id ON annotations(project_id);
CREATE INDEX idx_annotations_category ON annotations(category);
CREATE INDEX idx_projects_user_id ON projects(user_id);
CREATE INDEX idx_symbols_category ON symbols(category);
CREATE INDEX idx_symbols_user_id ON symbols(user_id);

-- 插入默认管理员用户 (密码：admin123)
INSERT INTO users (username, password, role, email) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iDJ9yqQSqH6QEZMk7y4.xYT6qZKG', 'ROLE_ADMIN', 'admin@example.com'),
('user', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iDJ9yqQSqH6QEZMk7y4.xYT6qZKG', 'ROLE_USER', 'user@example.com');
