-- ============================================================
-- 智能打卡小程序 — 数据库 DDL
-- 数据库：smart_checkin  |  字符集：utf8mb4  |  引擎：InnoDB
-- ============================================================

CREATE DATABASE IF NOT EXISTS `smart_checkin`
    DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;
USE `smart_checkin`;

CREATE TABLE `users` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    `openid` VARCHAR(64) NOT NULL COMMENT '微信OpenID',
    `nick_name` VARCHAR(100) COMMENT '微信昵称',
    `avatar_url` VARCHAR(500) COMMENT '微信头像URL',
    `real_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '真实姓名',
    `gender` VARCHAR(10) COMMENT '性别：男/女，花名册匹配用',
    `id_card` VARCHAR(200) NOT NULL COMMENT '身份证号（AES加密）',
    `face_image_url` VARCHAR(500) COMMENT '人脸照片URL',
    `phone` VARCHAR(20) COMMENT '手机号',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX `uk_openid` (`openid`),
    INDEX `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE `activities` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '活动ID',
    `name` VARCHAR(100) NOT NULL COMMENT '活动名称',
    `start_date` DATE NOT NULL,
    `end_date` DATE NOT NULL,
    `check_in_start_time` TIME NOT NULL,
    `check_in_end_time` TIME NOT NULL,
    `frequency_type` ENUM('daily','custom') DEFAULT 'daily',
    `frequency_days` JSON COMMENT '[1,2,3,4,5,6,7]',
    `check_in_type` ENUM('location','face','combo') NOT NULL,
    `location_lat` DECIMAL(10,7),
    `location_lng` DECIMAL(10,7),
    `location_radius` INT DEFAULT 500 COMMENT '米',
    `invite_code` VARCHAR(10) NOT NULL COMMENT '6位邀请码',
    `invite_link` VARCHAR(200),
    `invite_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '邀请码是否有效：1有效 0失效',
    `creator_id` BIGINT NOT NULL,
    `status` ENUM('active','closed','archived') DEFAULT 'active',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX `uk_invite_code` (`invite_code`),
    INDEX `idx_creator` (`creator_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_date_range` (`start_date`, `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动表';

CREATE TABLE `activity_members` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `activity_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `joined_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `status` ENUM('active','left') DEFAULT 'active',
    UNIQUE KEY `uk_activity_user` (`activity_id`, `user_id`),
    INDEX `idx_activity` (`activity_id`),
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动参与者表';

CREATE TABLE `activity_managers` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `activity_id` BIGINT NOT NULL,
    `user_id` BIGINT COMMENT '可为空',
    `name` VARCHAR(50) NOT NULL,
    `phone` VARCHAR(20) NOT NULL,
    `role` ENUM('creator','manager') DEFAULT 'manager',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_activity` (`activity_id`),
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动负责人表';

CREATE TABLE `check_ins` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `activity_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `check_in_date` DATE NOT NULL,
    `check_in_time` TIME NOT NULL,
    `status` ENUM('normal','late','abnormal') NOT NULL,
    `type` ENUM('location','face','combo') NOT NULL,
    `location_lat` DECIMAL(10,7),
    `location_lng` DECIMAL(10,7),
    `face_image_url` VARCHAR(500),
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_activity_user_date` (`activity_id`, `user_id`, `check_in_date`),
    INDEX `idx_user_date` (`user_id`, `check_in_date`),
    INDEX `idx_activity_date` (`activity_id`, `check_in_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='打卡记录表';

CREATE TABLE `leaves` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `activity_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL COMMENT '申请人',
    `start_date` DATE NOT NULL,
    `end_date` DATE NOT NULL,
    `destination` VARCHAR(200),
    `reason` VARCHAR(500),
    `emergency_contact` VARCHAR(20) NOT NULL,
    `approver_id` BIGINT NOT NULL COMMENT '审批人',
    `status` ENUM('pending','approved','rejected') DEFAULT 'pending',
    `reject_reason` VARCHAR(200),
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_activity_user` (`activity_id`, `user_id`),
    INDEX `idx_approver` (`approver_id`, `status`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='请假记录表';

CREATE TABLE `roster` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `activity_id` BIGINT NOT NULL,
    `name` VARCHAR(50) NOT NULL,
    `gender` VARCHAR(10) NOT NULL,
    `phone` VARCHAR(20),
    `matched_user_id` BIGINT,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_activity` (`activity_id`),
    INDEX `idx_matched` (`matched_user_id`),
    INDEX `idx_name_gender` (`name`, `gender`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='花名册表';

CREATE TABLE `user_subscriptions` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `template_id` VARCHAR(100) NOT NULL COMMENT '订阅消息模板ID',
    `status` ENUM('accepted','rejected','ban') DEFAULT 'accepted' COMMENT '授权状态',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_template` (`user_id`, `template_id`),
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订阅消息授权表';
