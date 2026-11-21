-- 默认迁移路径演示：随模块测试资源提供
CREATE TABLE zone_fly_test (
    id   INT PRIMARY KEY,
    name VARCHAR(64)
);
INSERT INTO zone_fly_test (id, name) VALUES (1, 'zone-init');
