ALTER TABLE trade_order
    ADD COLUMN idempotency_key VARCHAR(64) DEFAULT NULL COMMENT '订单创建幂等请求号' AFTER product_id,
    ADD UNIQUE KEY uk_trade_order_buyer_idempotency (buyer_id, idempotency_key);
