ALTER TABLE order_review
    ADD COLUMN review_type TINYINT NOT NULL DEFAULT 0 COMMENT '评价类型 0首评 1追评' AFTER content,
    ADD COLUMN parent_id BIGINT UNSIGNED DEFAULT NULL COMMENT '首评ID，追评使用' AFTER review_type;

ALTER TABLE order_review
    DROP INDEX uk_order_review,
    ADD UNIQUE KEY uk_order_review (order_id, reviewer_id, review_type, is_deleted),
    ADD KEY idx_order_review_parent_id (parent_id);
