ALTER TABLE trade_order
    ADD COLUMN quantity INT NOT NULL DEFAULT 1 COMMENT '购买数量' AFTER product_id;
