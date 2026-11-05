ALTER TABLE sys_user
    DROP INDEX idx_user_campus_id,
    DROP COLUMN campus_id;

ALTER TABLE product
    DROP INDEX idx_product_campus_id,
    DROP COLUMN campus_id;
