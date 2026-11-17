-- 保证“每个商品最多一张封面”。
-- 做法：加一个 STORED 生成列，只有 is_cover = 1 的行才有值（否则为 NULL），
-- 再对这一列建唯一索引，NULL 不参与唯一性判断，因此普通图片不受影响。
--
-- 执行前会先把历史脏数据收敛掉：
--   1) 同一商品多张封面时只保留 id 最小的一张；
--   2) 已逻辑删除的图片不再占用封面位。
-- 重复执行会因唯一索引已存在而报 1061，执行前先确认索引不存在。

UPDATE product_image pi
JOIN (
    SELECT product_id, MIN(id) AS keep_id
    FROM product_image
    WHERE is_cover = 1 AND is_deleted = 0
    GROUP BY product_id
) keep ON keep.product_id = pi.product_id
SET pi.is_cover = 0
WHERE pi.is_cover = 1 AND pi.is_deleted = 0 AND pi.id <> keep.keep_id;

UPDATE product_image
SET is_cover = 0
WHERE is_deleted = 1 AND is_cover = 1;

ALTER TABLE product_image
    ADD COLUMN cover_product_id BIGINT UNSIGNED GENERATED ALWAYS AS (
        IF(is_cover = 1, product_id, NULL)
    ) STORED COMMENT '封面归属商品ID：仅封面行有值，配合唯一索引保证每商品最多一张封面',
    ADD UNIQUE KEY uk_product_image_cover (cover_product_id);
