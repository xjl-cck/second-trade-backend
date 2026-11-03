-- 支付宝退款流水表
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
