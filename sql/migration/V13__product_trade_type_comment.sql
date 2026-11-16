-- 交易方式/交付方式的注释还是旧口径，与前端和业务实现（ProductServiceImpl.validateTradeLocation）不一致。
-- 这里只改注释，不改类型和默认值，可重复执行。
ALTER TABLE product
    MODIFY COLUMN exchange_type TINYINT NOT NULL DEFAULT 0 COMMENT '交易方式 0一口价 1可交换',
    MODIFY COLUMN delivery_method TINYINT NOT NULL DEFAULT 0 COMMENT '交付方式 0线下自提 1当面交易 2卖家配送';
