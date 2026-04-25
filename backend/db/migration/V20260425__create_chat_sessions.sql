-- AI 聊天会话表
CREATE TABLE IF NOT EXISTS chat_sessions (
    id VARCHAR(64) PRIMARY KEY COMMENT '会话 ID（前端生成的 UUID）',
    title VARCHAR(255) NOT NULL COMMENT '会话标题',
    messages TEXT COMMENT '消息列表（JSON 数组）',
    device_id VARCHAR(64) COMMENT '设备标识',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_device_id (device_id),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 聊天会话表';
