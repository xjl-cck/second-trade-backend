-- 商品索引定时对账按 update_time 做范围扫描，没有索引会退化成全表扫。
-- 注意：MySQL 的 ADD INDEX 不支持 IF NOT EXISTS，重复执行会报 1061，执行前先确认索引不存在。
ALTER TABLE product
    ADD INDEX idx_product_update_time (update_time);
