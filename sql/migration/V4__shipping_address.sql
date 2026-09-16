CREATE TABLE shipping_address (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    receiver_name VARCHAR(32) NOT NULL COMMENT '收件人',
    receiver_phone VARCHAR(20) NOT NULL COMMENT '联系电话',
    province VARCHAR(32) NOT NULL COMMENT '省份',
    city VARCHAR(32) NOT NULL COMMENT '城市',
    district VARCHAR(32) NOT NULL COMMENT '区县',
    detail VARCHAR(128) NOT NULL COMMENT '详细地址',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认 0否 1是',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_shipping_address_user_id (user_id),
    KEY idx_shipping_address_default (user_id, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='收货地址表';

ALTER TABLE trade_order
    ADD COLUMN address_id BIGINT UNSIGNED DEFAULT NULL COMMENT '下单时选择的收货地址ID' AFTER idempotency_key,
    ADD COLUMN receiver_name VARCHAR(32) DEFAULT NULL COMMENT '收件人快照' AFTER address_id,
    ADD COLUMN receiver_phone VARCHAR(20) DEFAULT NULL COMMENT '联系电话快照' AFTER receiver_name,
    ADD COLUMN receiver_address VARCHAR(255) DEFAULT NULL COMMENT '收货地址快照' AFTER receiver_phone;
