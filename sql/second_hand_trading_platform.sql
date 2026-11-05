-- Second-hand trading platform schema
-- MySQL 8.x, InnoDB, utf8mb4

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS outbox_event;
DROP TABLE IF EXISTS order_review;
DROP TABLE IF EXISTS return_request;
DROP TABLE IF EXISTS report;
DROP TABLE IF EXISTS chat_message;
DROP TABLE IF EXISTS chat_session;
DROP TABLE IF EXISTS favorite;
DROP TABLE IF EXISTS product_image;
DROP TABLE IF EXISTS trade_order;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS campus;

CREATE TABLE campus (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name VARCHAR(64) NOT NULL COMMENT '校园名称',
    city VARCHAR(64) DEFAULT NULL COMMENT '所在城市',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_campus_name (name),
    KEY idx_campus_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='校园表';

CREATE TABLE sys_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(64) NOT NULL COMMENT '登录账号',
    password VARCHAR(255) NOT NULL COMMENT '密码',
    nickname VARCHAR(64) NOT NULL COMMENT '昵称',
    phone VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    avatar VARCHAR(255) DEFAULT NULL COMMENT '头像',
    student_no VARCHAR(32) DEFAULT NULL COMMENT '学号',
    gender TINYINT DEFAULT 0 COMMENT '性别 0未知 1男 2女',
    major VARCHAR(64) DEFAULT NULL COMMENT '专业',
    grade VARCHAR(32) DEFAULT NULL COMMENT '年级',
    role TINYINT NOT NULL DEFAULT 0 COMMENT '角色 0学生 1管理员',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1正常 2封禁',
    last_login_time DATETIME DEFAULT NULL COMMENT '最后登录时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_phone (phone),
    UNIQUE KEY uk_user_student_no (student_no),
    KEY idx_user_status (status),
    KEY idx_user_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

CREATE TABLE category (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父级ID',
    name VARCHAR(64) NOT NULL COMMENT '分类名称',
    icon VARCHAR(255) DEFAULT NULL COMMENT '分类图标',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序值',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_category_parent_id (parent_id),
    KEY idx_category_status (status),
    KEY idx_category_sort (sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品分类表';

CREATE TABLE product (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    seller_id BIGINT UNSIGNED NOT NULL COMMENT '发布人ID',
    category_id BIGINT UNSIGNED NOT NULL COMMENT '分类ID',
    title VARCHAR(128) NOT NULL COMMENT '商品标题',
    price DECIMAL(10, 2) NOT NULL COMMENT '售价',
    stock INT NOT NULL DEFAULT 1 COMMENT '库存数量',
    original_price DECIMAL(10, 2) DEFAULT NULL COMMENT '原价',
    description TEXT COMMENT '商品描述',
    cover_image VARCHAR(255) DEFAULT NULL COMMENT '封面图',
    exchange_type TINYINT NOT NULL DEFAULT 0 COMMENT '交易方式 0自提 1邮寄 2均可',
    delivery_method TINYINT NOT NULL DEFAULT 0 COMMENT '交付方式 0当面交易 1送货上门 2快递',
    location VARCHAR(128) DEFAULT NULL COMMENT '交易地点',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0草稿 1待审核 2上架 3下架 4已售出',
    browse_count INT NOT NULL DEFAULT 0 COMMENT '浏览数',
    favorite_count INT NOT NULL DEFAULT 0 COMMENT '收藏数',
    contact_phone VARCHAR(20) DEFAULT NULL COMMENT '联系手机号',
    audit_reason VARCHAR(255) DEFAULT NULL COMMENT '审核/下架原因',
    published_time DATETIME DEFAULT NULL COMMENT '发布时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_product_seller_id (seller_id),
    KEY idx_product_category_id (category_id),
    KEY idx_product_status (status),
    KEY idx_product_created_time (create_time),
    KEY idx_product_price (price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品表';

CREATE TABLE product_image (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    product_id BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    url VARCHAR(255) NOT NULL COMMENT '图片地址',
    is_cover TINYINT NOT NULL DEFAULT 0 COMMENT '是否封面 0否 1是',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序值',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_product_image_product_id (product_id),
    KEY idx_product_image_sort (sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品图片表';

CREATE TABLE favorite (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    product_id BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorite_user_product (user_id, product_id, is_deleted),
    KEY idx_favorite_user_id (user_id),
    KEY idx_favorite_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='收藏表';

CREATE TABLE chat_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    buyer_id BIGINT UNSIGNED NOT NULL COMMENT '买家ID',
    seller_id BIGINT UNSIGNED NOT NULL COMMENT '卖家ID',
    product_id BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    quantity INT NOT NULL DEFAULT 1 COMMENT '购买数量',
    idempotency_key VARCHAR(64) DEFAULT NULL COMMENT '订单创建幂等请求号',
    last_message VARCHAR(255) DEFAULT NULL COMMENT '最后一条消息',
    last_message_time DATETIME DEFAULT NULL COMMENT '最后消息时间',
    unread_count INT NOT NULL DEFAULT 0 COMMENT '未读数',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_session (buyer_id, seller_id, product_id, is_deleted),
    KEY idx_chat_session_buyer_id (buyer_id),
    KEY idx_chat_session_seller_id (seller_id),
    KEY idx_chat_session_product_id (product_id),
    KEY idx_chat_session_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='聊天会话表';

CREATE TABLE chat_message (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id BIGINT UNSIGNED NOT NULL COMMENT '会话ID',
    sender_id BIGINT UNSIGNED NOT NULL COMMENT '发送人ID',
    receiver_id BIGINT UNSIGNED NOT NULL COMMENT '接收人ID',
    msg_type TINYINT NOT NULL DEFAULT 0 COMMENT '消息类型 0文本 1图片 2系统',
    content TEXT NOT NULL COMMENT '消息内容',
    read_status TINYINT NOT NULL DEFAULT 0 COMMENT '阅读状态 0未读 1已读',
    send_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_chat_message_session_id (session_id),
    KEY idx_chat_message_receiver_id (receiver_id),
    KEY idx_chat_message_read_status (read_status),
    KEY idx_chat_message_send_time (send_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='聊天消息表';

CREATE TABLE shipping_address (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    receiver_name VARCHAR(32) NOT NULL COMMENT '收件人',
    receiver_phone VARCHAR(20) NOT NULL COMMENT '联系电话',
    province VARCHAR(32) NOT NULL COMMENT '省份',
    city VARCHAR(32) NOT NULL COMMENT '城市',
    district VARCHAR(32) NOT NULL COMMENT '区县',
    detail VARCHAR(128) NOT NULL COMMENT '详细地址',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认 0否 1是',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_shipping_address_user_id (user_id),
    KEY idx_shipping_address_default (user_id, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='收货地址表';

CREATE TABLE trade_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no VARCHAR(64) NOT NULL COMMENT '订单号',
    buyer_id BIGINT UNSIGNED NOT NULL COMMENT '买家ID',
    seller_id BIGINT UNSIGNED NOT NULL COMMENT '卖家ID',
    product_id BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    idempotency_key VARCHAR(64) DEFAULT NULL COMMENT '订单创建幂等请求号',
    address_id BIGINT UNSIGNED DEFAULT NULL COMMENT '下单时选择的收货地址ID',
    receiver_name VARCHAR(32) DEFAULT NULL COMMENT '收件人快照',
    receiver_phone VARCHAR(20) DEFAULT NULL COMMENT '联系电话快照',
    receiver_address VARCHAR(255) DEFAULT NULL COMMENT '收货地址快照',
    amount DECIMAL(10, 2) NOT NULL COMMENT '成交金额',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0待确认 1已成交 2已取消 3已完成',
    close_reason VARCHAR(255) DEFAULT NULL COMMENT '取消/关闭原因',
    deal_time DATETIME DEFAULT NULL COMMENT '成交时间',
    finished_time DATETIME DEFAULT NULL COMMENT '完成时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_order_no (order_no),
    UNIQUE KEY uk_trade_order_buyer_idempotency (buyer_id, idempotency_key),
    KEY idx_trade_order_buyer_id (buyer_id),
    KEY idx_trade_order_seller_id (seller_id),
    KEY idx_trade_order_product_id (product_id),
    KEY idx_trade_order_status (status),
    KEY idx_trade_order_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单表';

CREATE TABLE return_request (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    buyer_id BIGINT UNSIGNED NOT NULL COMMENT '买家ID',
    seller_id BIGINT UNSIGNED NOT NULL COMMENT '卖家ID',
    reason VARCHAR(255) NOT NULL COMMENT '退货原因',
    description VARCHAR(1000) DEFAULT NULL COMMENT '退货说明',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0待审核 1退货中 2已驳回 3退款完成',
    seller_remark VARCHAR(255) DEFAULT NULL COMMENT '卖家处理说明',
    approved_time DATETIME DEFAULT NULL COMMENT '同意时间',
    completed_time DATETIME DEFAULT NULL COMMENT '退款完成时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_return_request_order (order_id, is_deleted),
    KEY idx_return_request_buyer_id (buyer_id),
    KEY idx_return_request_seller_id (seller_id),
    KEY idx_return_request_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='退货申请表';

CREATE TABLE order_review (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    reviewer_id BIGINT UNSIGNED NOT NULL COMMENT '评价人ID',
    reviewee_id BIGINT UNSIGNED NOT NULL COMMENT '被评价人ID',
    rating TINYINT NOT NULL COMMENT '评分 1-5',
    content VARCHAR(500) DEFAULT NULL COMMENT '评价内容',
    review_type TINYINT NOT NULL DEFAULT 0 COMMENT '评价类型 0首评 1追评',
    parent_id BIGINT UNSIGNED DEFAULT NULL COMMENT '首评ID，追评使用',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0隐藏 1显示',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_review (order_id, reviewer_id, review_type, is_deleted),
    KEY idx_order_review_order_id (order_id),
    KEY idx_order_review_reviewer_id (reviewer_id),
    KEY idx_order_review_reviewee_id (reviewee_id),
    KEY idx_order_review_status (status),
    KEY idx_order_review_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单评价表';

CREATE TABLE report (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    reporter_id BIGINT UNSIGNED NOT NULL COMMENT '举报人ID',
    target_type VARCHAR(32) NOT NULL COMMENT '举报对象类型 product user message review order',
    target_id BIGINT UNSIGNED NOT NULL COMMENT '举报对象ID',
    reason VARCHAR(128) NOT NULL COMMENT '举报原因',
    detail VARCHAR(500) DEFAULT NULL COMMENT '举报详情',
    evidence_urls VARCHAR(1000) DEFAULT NULL COMMENT '证据图片，逗号分隔',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0待处理 1已受理 2已驳回 3已关闭',
    handler_id BIGINT UNSIGNED DEFAULT NULL COMMENT '处理人ID',
    handle_result VARCHAR(500) DEFAULT NULL COMMENT '处理结果',
    handle_time DATETIME DEFAULT NULL COMMENT '处理时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_report_reporter_id (reporter_id),
    KEY idx_report_target_type_target_id (target_type, target_id),
    KEY idx_report_status (status),
    KEY idx_report_handler_id (handler_id),
    KEY idx_report_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='举报表';

CREATE TABLE outbox_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id VARCHAR(64) NOT NULL COMMENT '事件唯一ID，作为消息ID用于追踪和消费幂等',
    event_type VARCHAR(128) NOT NULL COMMENT '事件类型全类名，重投时反序列化使用',
    exchange_name VARCHAR(128) NOT NULL COMMENT '目标交换机',
    routing_key VARCHAR(128) NOT NULL COMMENT '路由键',
    payload MEDIUMTEXT NOT NULL COMMENT '事件内容 JSON',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0待投递 1投递中 2已投递 3投递失败',
    attempts INT NOT NULL DEFAULT 0 COMMENT '已尝试投递次数',
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次可投递时间',
    last_error VARCHAR(500) DEFAULT NULL COMMENT '最近一次失败原因',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_status_next (status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='事务消息出站表';

SET FOREIGN_KEY_CHECKS = 1;
