-- 支付宝支付流水表
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
