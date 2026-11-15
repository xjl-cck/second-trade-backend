-- 超时关单扫描：WHERE status = 0 AND create_time < ?
-- 关单补偿扫描：WHERE status = 4 AND update_time < ?
-- 只有单列 idx_trade_order_status 时，命中状态后仍要逐行回表比较时间，数据量上来会变成大量随机 IO。
-- 复合索引把"状态 + 时间范围"一次定位，两个扫描都只扫目标行。
-- 注意：MySQL 的 ADD INDEX 不支持 IF NOT EXISTS，重复执行会报 1061，执行前先确认索引不存在。
ALTER TABLE trade_order
    ADD INDEX idx_trade_order_status_create (status, create_time),
    ADD INDEX idx_trade_order_status_update (status, update_time);
