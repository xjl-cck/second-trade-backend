CREATE TABLE IF NOT EXISTS return_request (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    buyer_id BIGINT UNSIGNED NOT NULL COMMENT '买家ID',
    seller_id BIGINT UNSIGNED NOT NULL COMMENT '卖家ID',
    reason VARCHAR(255) NOT NULL COMMENT '退货原因',
    description VARCHAR(1000) DEFAULT NULL COMMENT '退货说明',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0待审核 1退货中 2已驳回 3退款完成',
    seller_remark VARCHAR(255) DEFAULT NULL COMMENT '卖家处理说明',
    approved_time DATETIME DEFAULT NULL COMMENT '同意时间',
    completed_time DATETIME DEFAULT NULL COMMENT '退款完成时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_return_request_order (order_id, is_deleted),
    KEY idx_return_request_buyer_id (buyer_id),
    KEY idx_return_request_seller_id (seller_id),
    KEY idx_return_request_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='退货申请表';

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

ALTER TABLE trade_order
    MODIFY COLUMN address_id BIGINT UNSIGNED DEFAULT NULL COMMENT '下单时选择的收货地址ID',
    MODIFY COLUMN receiver_name VARCHAR(32) DEFAULT NULL COMMENT '收件人快照',
    MODIFY COLUMN receiver_phone VARCHAR(20) DEFAULT NULL COMMENT '联系电话快照',
    MODIFY COLUMN receiver_address VARCHAR(255) DEFAULT NULL COMMENT '收货地址快照';

CREATE TABLE IF NOT EXISTS payment_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    order_no VARCHAR(64) NOT NULL COMMENT '订单号',
    out_trade_no VARCHAR(64) NOT NULL COMMENT '支付宝商户订单号，回调用它反查订单',
    trade_no VARCHAR(64) DEFAULT NULL COMMENT '支付宝交易号',
    channel VARCHAR(32) NOT NULL DEFAULT 'alipay' COMMENT '支付渠道',
    pay_method VARCHAR(32) NOT NULL DEFAULT 'page' COMMENT '支付方式 page=电脑网站支付',
    amount DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    buyer_id BIGINT UNSIGNED NOT NULL COMMENT '买家ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0待支付 1已支付 2已关闭 3支付异常待退款',
    paid_time DATETIME DEFAULT NULL COMMENT '支付成功时间',
    notify_time DATETIME DEFAULT NULL COMMENT '最近一次收到通知时间',
    last_error VARCHAR(500) DEFAULT NULL COMMENT '异常原因',
    raw_notify TEXT DEFAULT NULL COMMENT '最近一次通知原文，便于对账和排查',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_out_trade_no (out_trade_no),
    KEY idx_payment_order_no (order_no),
    KEY idx_payment_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='支付流水表';

CREATE TABLE IF NOT EXISTS payment_refund (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    order_no VARCHAR(64) NOT NULL COMMENT '订单号',
    out_trade_no VARCHAR(64) NOT NULL COMMENT '支付宝商户订单号',
    trade_no VARCHAR(64) DEFAULT NULL COMMENT '支付宝交易号',
    return_request_id BIGINT UNSIGNED NOT NULL COMMENT '退货申请ID',
    out_request_no VARCHAR(64) NOT NULL COMMENT '退款请求号，调用支付宝时用于幂等',
    amount DECIMAL(10,2) NOT NULL COMMENT '应退金额',
    refund_fee DECIMAL(10,2) DEFAULT NULL COMMENT '支付宝返回的实际退款金额',
    fund_change VARCHAR(4) DEFAULT NULL COMMENT '资金是否变动 Y/N',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0待退款 1退款成功 2退款失败',
    error VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    refund_time DATETIME DEFAULT NULL COMMENT '退款成功时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_refund_return_request (return_request_id),
    KEY idx_payment_refund_order_no (order_no),
    KEY idx_payment_refund_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='退款流水表';
