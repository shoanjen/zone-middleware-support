# Tasks

## 1. database 模块：键面官方化

- [x] 1.1 ZoneDatabaseProperties 收缩：仅保留 `mode` 与 `mybatis` 段；删除 url/username/password/driverClassName/hikari/druid/flyway 字段及全部 resolved* 预设方法；同步删除 ZoneDatabasePropertiesTest 中对应用例
- [x] 1.2 新增官方前缀绑定类：`spring.datasource.hikari.*`（maximum-pool-size/minimum-idle/connection-timeout/leak-detection-threshold/keepalive-time）与 `spring.datasource.druid.*`（initial-size/min-idle/max-active/max-wait/keep-alive/filters）最小 POJO 绑定
- [x] 1.3 ZoneDataSourceAutoConfiguration 改造：注入官方 `DataSourceProperties` 取连接四项；按 `spring.datasource.type` 解析池类型（未设 → Hikari；Druid FQCN → Druid；不可解析 → 启动失败含原值与提示）；buildHikari/buildDruid 参数源切换为官方前缀绑定；`applyDruidFilters` 改读 `spring.datasource.druid.filters`（别名注册表校验逻辑不变）；url 缺失报错文案指向 `spring.datasource.url`
- [x] 1.4 ZoneFlywayConfiguration 收敛：保留 FlywayConfigurationCustomizer 仅对未显式配置的 `spring.flyway.locations/baseline-on-migrate` 套默认；删除 zone flyway 键分支；删除 ZoneFlywayEnablementEnvironmentPostProcessor 类与 spring.factories 注册
- [x] 1.5 新增 ZoneDbPresetEnvironmentPostProcessor：order `HIGHEST_PRECEDENCE + 20`，读 `zone.middleware.db.mode`，按模式注入官方键预设（LOCAL：hikari 连接超时 5s/泄漏检测 10s、druid max-wait 5s/keep-alive false；CLOUD：hikari 连接超时 15s/保活 120s/泄漏检测关、druid max-wait 15s/keep-alive true；初始/空闲/最大连接数两模式同为 2/2/8）`addLast` 注入；注册进 database 模块 spring.factories

## 2. redis 模块：委托官方装配

- [x] 2.1 ZoneRedisProperties 收缩：仅保留 `mode`；删除 host/port/password/database/ssl/connectTimeout/commandTimeout/keepAlive/pool 字段及 resolved* 方法；同步删 ZoneRedisPropertiesTest 对应用例
- [x] 2.2 ZoneRedisAutoConfiguration 改造：删除 zoneRedisConnectionFactory Bean 与 `@AutoConfigureBefore(RedisAutoConfiguration.class)`，改 `@AutoConfigureAfter`；StringRedisTemplate/ZoneRedisTemplate 依赖官方装配的 LettuceConnectionFactory；启动日志改基于官方 `RedisProperties` + mode 打印（关键字 `zone-redis enabled` 保留）
- [x] 2.3 新增 ZoneRedisPresetEnvironmentPostProcessor：order `HIGHEST_PRECEDENCE + 20`，按模式注入官方键预设（LOCAL：connect-timeout=2s、timeout=3s、lettuce.keep-alive=false；CLOUD：connect-timeout=10s、timeout=10s、lettuce.keep-alive=true；pool 8/8/0/2s 两模式相同）`addLast` 注入；注册进 redis 模块 spring.factories
- [x] 2.4 验证 Boot 3.5.6 官方键映射：`spring.data.redis.lettuce.keep-alive` 生效性专项验证；不生效则改用 `LettuceClientConfigurationBuilderCustomizer` 按 mode 补齐并在代码注释说明

## 3. 测试改造与新增

- [x] 3.1 全量替换测试键名：ZoneDataSourceConfigurationTest/ZoneFlywayIntegrationTest/ZoneMyBatisPlusIntegrationTest/redis 相关测试/ZoneDatabasePropertiesTest 中 zone 连接键 → 官方键（url/username/pool 参数等）
- [x] 3.2 新增 EEP 预设测试：CLOUD 未显式配置 → 预设生效；CLOUD 显式官方键配置 → 上游值赢；LOCAL → 无注入（db 与 redis 各一组）
- [x] 3.3 新增官方键行为测试：`spring.datasource.type` 切 Druid、不可解析 type 启动失败、`spring.datasource.druid.filters` 合法别名生效与未注册别名快速失败（改自现有 filters 用例）、`spring.flyway.enabled=false` 关迁移
- [x] 3.4 全量回归：`mvn clean install` 9 模块全绿，确认无 zone 连接键残留（全库 grep `zone.middleware.db.` 与 `zone.middleware.redis.` 仅剩 mode/mybatis）

## 4. 文档同步（本仓库内）

- [x] 4.1 接入手册重写：§3.2 配置义务表改官方键名（spring.datasource.url / security.sign.secret）；§5/§6/§7 配置表与示例改官方键 + mode 说明；§7.3 Druid 监控示例的 filters 键名同步；§11 properties 全量参考改官方键；§13 排错条目更新（url 缺失、redis 地址条目反转语义）
- [x] 4.2 手册新增「zone 键 → 官方键迁移对照表」（含 zone-boot-template 四个 yml 的 grep 自查命令），供消费方迁移
- [x] 4.3 README 配置总表同步官方键；项目 AGENTS.md 存根速览与总纲第五节 #17 行、第十节变更记录登记（apply 完成后）

## 5. 消费方迁移（本仓库外，独立执行）

- [x] 5.1 zone-boot-template：application.yml 与 application-dev/test/prod.yml 按 §4.2 对照表替换约 30 行 zone 连接键；`mode` 与 `zone.middleware.db.mybatis.*` 保留不动
- [x] 5.2 zone-boot-template 回归：`mvn clean package` + 启动验证（8212 正常监听、`zone-datasource init`/`zone-redis enabled`/`zone-log enabled` 关键字命中、`spring.datasource.url` 生效连接 zone_boot_template 库）
- [x] 5.3 消费方侧总纲登记：boot-template 项目存根/总纲变更记录补记键面迁移（在其项目变更流程内完成）
