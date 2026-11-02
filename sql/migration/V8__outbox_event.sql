CREATE TABLE IF NOT EXISTS outbox_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id VARCHAR(64) NOT NULL COMMENT '事件唯一ID，作为消息ID用于追踪和消费幂等',
    event_type VARCHAR(128) NOT NULL COMMENT '事件类型全类名，重投时反序列化使用',
    exchange_name VARCHAR(128) NOT NULL COMMENT '目标交换机',
    routing_key VARCHAR(128) NOT NULL COMMENT '路由键',
    payload MEDIUMTEXT NOT NULL COMMENT '事件内容 JSON',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0待投递 1投递中 2已投递 3投递失败',
    attempts INT NOT NULL DEFAULT 0 COMMENT '已尝试投递次数',
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次可投递时间',
    last_error VARCHAR(500) DEFAULT NULL COMMENT '最近一次失败原因',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_status_next (status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='事务消息出站表';
