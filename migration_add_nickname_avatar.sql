-- ============================================================
-- 为已有 smart_checkin 数据库添加微信用户信息字段
-- 执行方式: mysql -u root -p smart_checkin < migration_add_nickname_avatar.sql
-- ============================================================

USE `smart_checkin`;

ALTER TABLE `users`
    ADD COLUMN `nick_name` VARCHAR(100) COMMENT '微信昵称' AFTER `openid`,
    ADD COLUMN `avatar_url` VARCHAR(500) COMMENT '微信头像URL' AFTER `nick_name`,
    MODIFY COLUMN `real_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '真实姓名';
