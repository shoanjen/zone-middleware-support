# Proposal: remove-db-redis-mode-presets

## Why

上一轮去方言化把连接/池/超时键迁移到了 Spring Boot 官方键，但残留了 LOCAL/CLOUD 部署模式预设层（`zone.middleware.db.mode` / `zone.middleware.redis.mode` + 两个 EnvironmentPostProcessor 注入器 + 按模式派生的 keep-alive 双路径装配）。这层预设遮蔽了官方默认值语义、增加上游认知与维护成本（两套默认值记忆负担），且是本仓库仅存的非官方配置概念。同时 http 模块的 `connect-timeout` / `read-timeout` / `redirect` 三键与 Boot 3.4+ 官方 `spring.http.client.*` 键面重复，属同一类方言残留。

## What Changes

- **BREAKING** redis：删除 `zone.middleware.redis.mode` 键与 `ZoneRedisProperties`、`ZoneRedisPresetEnvironmentPostProcessor`（含 spring.factories 注册）、`ZoneRedisKeepAliveAutoConfiguration` / `ZoneRedisLegacyKeepAliveConfiguration`（keep-alive 唯一驱动是 mode==CLOUD，随之删除）；超时/池参数全部由官方键显式配置，未配置即官方默认——lettuce 连接池不再默认开启、TCP keepalive 不再自动开启；模板 Bean 装配与 `zone-redis enabled` 启动日志保留（日志去掉 mode/keepAlive 项）。
- **BREAKING** database：删除 `zone.middleware.db.mode` 与 `ZoneDbPresetEnvironmentPostProcessor`（含 spring.factories 注册）；池参数缺省回归 HikariCP / Druid 官方默认值；Druid filter 别名快速失败校验、url 缺失快速失败、`zone-hikari/zone-druid` 池名等行为件不变。
- **BREAKING** database 键面清零：删除 `zone.middleware.db.mybatis.pagination-enabled`（分页拦截器默认装配、无开关键，上游自定义 `MybatisPlusInterceptor` Bean 即接管）与 `zone.middleware.db.mybatis.mapper-package`（Mapper 扫描回归官方行为：主应用包下 `@Mapper` 自动扫描，跨包场景上游 `@MapperScan`）；`ZoneDatabaseProperties` 整类删除，`zone.middleware.db.*` 无任何键。
- **BREAKING** http：`zone.middleware.http.connect-timeout` / `read-timeout` / `redirect` 迁移到官方键 `spring.http.client.connect-timeout` / `read-timeout` / `redirects`（官方 redirects 仅 `never` / `follow-when-possible` 两值，JDK `ALWAYS` 能力舍弃）；未配置时代码兜底 5s / 10s 默认；`max-concurrent-requests` 与 `retry.*` 无官方对应，保留 zone 键。
- 文档同步：README 与接入手册删除 LOCAL/CLOUD 预设表与 keep-alive 描述、http 配置表改官方键、新增 zone 键 → 官方键/删除 迁移对照表（含三条行为变化说明）；AGENTS.md 存根备注与根总纲变更记录回填。

## Capabilities

### Modified Capabilities

- `redis-operations`：删除「部署模式声明」「云端预设与逐项覆盖」两条需求；连接与池参数完全由官方键承载，无部署形态预设。
- `database-access`：删除「数据库部署模式声明」「云端预设与逐项覆盖」两条需求；连接池类型切换、上游自定义连接配置、Druid filter 校验等需求不变。
- `database-orm`：修改「MyBatis-Plus 开箱整合」「分页插件默认装配」两条需求——扫描回归官方 @Mapper/@MapperScan 行为、分页默认装配无开关键；`zone.middleware.db.*` 键面清零。
- `http-client`：修改「客户端配置」需求——连接超时/读超时/重定向策略改由官方键承载，并发上限与重试保留 zone 键。

## Impact

- 代码：redis / database / http 三模块的配置类与自动装配；删除 5 个主源码类、2 份 spring.factories；新增 1 个官方键绑定类。
- 测试：删除 3 个预设/mode 测试类，改造 3 个既有测试类的相关用例。
- 文档：README、docs/接入手册.md、AGENTS.md（存根与根总纲）。
- 消费方（本仓库外）：zone-boot-template 等上游需按迁移对照表调整 yml（mode 键删除后 Boot 宽松绑定静默忽略，但池/超时默认值回归官方默认，行为有变）。
- 依赖：无新增、无删除（commons-pool2 保留，供官方池键启用连接池）。
