-- ============================================================
-- 智能打卡小程序 — v2 功能补齐迁移脚本
-- 目的：补齐功能清单中缺失的能力
--   1) users.gender          → 花名册按「姓名+性别」精确匹配（清单 R-02）
--   2) activities.invite_enabled → 邀请码失效/激活真实生效（清单 IC-01/IC-02）
--   3) user_subscriptions    → 订阅消息授权（清单 N-01/N-02/N-03）
-- 执行方式：
--   mysql -u root -p smart_checkin < migration_v2_features.sql
-- 注意：本脚本只针对 v1 库执行一次；全新安装请直接用更新后的 smart_checkin.sql
-- ============================================================

USE `smart_checkin`;

-- 1. users 增加 gender
ALTER TABLE `users`
    ADD COLUMN `gender` VARCHAR(10) NULL COMMENT '性别：男/女，花名册匹配用' AFTER `real_name`;

-- 2. activities 增加 invite_enabled
ALTER TABLE `activities`
    ADD COLUMN `invite_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '邀请码是否有效：1有效 0失效' AFTER `invite_link`;

-- 3. 订阅消息授权表
CREATE TABLE IF NOT EXISTS `user_subscriptions` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `template_id` VARCHAR(100) NOT NULL COMMENT '订阅消息模板ID',
    `status` ENUM('accepted','rejected','ban') DEFAULT 'accepted' COMMENT '授权状态',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_template` (`user_id`, `template_id`),
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订阅消息授权表';

-- 4. 存量数据回填
-- 注意：v1 的创建活动逻辑**从来没有写过 invite_link**，所以老数据里 invite_link 为 NULL
-- 并不代表"邀请码被失效过"。如果按 NULL 判失效，会把所有历史活动的邀请码一并禁掉。
-- 因此这里统一置为有效，并补生成邀请链接。
UPDATE `activities` SET `invite_enabled` = 1 WHERE `invite_enabled` IS NULL;

UPDATE `activities`
SET `invite_link` = CONCAT('https://checkin.example.com/join/', `invite_code`)
WHERE `invite_link` IS NULL;
