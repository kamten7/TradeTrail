-- 首次初始化 MySQL 数据卷时创建独立物流库。
CREATE DATABASE IF NOT EXISTS tradetrail_logistics CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON tradetrail_logistics.* TO 'tradetrail'@'%';
