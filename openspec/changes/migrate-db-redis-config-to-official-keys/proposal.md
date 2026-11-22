# Proposal

## Why

redis/database 模块当前以 `zone.middleware.db.*` / `zone.middleware.redis.*` 自定义键转译 Spring Boot 官方配置键（`spring.datasource.*` / `spring.data.redis.*`）。转译层是纯换皮：官方文档、IDE 提示、社区与 AI 经验全部失效，手册转译表成为第二份会腐化的源码（2026-11-21 接入反馈四项中的 druid filters 缺口即直接症状），且每新增一个官方能力都需手工透出，长尾无终点。自定义配置键的正当理由是"治理"（平台团队管控多团队应用的中间件配置），本工作区 owner 与消费者为同一人，治理价值为零。当前全工作区仅 zone-boot-template 一个消费方（约 30 行键引用），拆除转译层处于历史最低成本时点；每新增一个消费方成本线性上涨。

## What Changes

- **BREAKING** redis 模块删除全部连接配置键（`zone.middleware.redis.host/port/password/database/ssl/connect-timeout/command-timeout/keep-alive/pool.*`），连接配置改读 Spring Boot 官方键 `spring.data.redis.*`；`LettuceConnectionFactory` 不再由本库自建，改由官方 `RedisAutoConfiguration` 装配，`ZoneRedisTemplate` 直接骑官方工厂（JSON 值语义不变）。
- **BREAKING** database 模块连接与池参数键删除（`zone.middleware.db.url/username/password/driver-class-name`、`druid.initial-size/min-idle/max-active/max-wait/keep-alive`、`hikari.*`），改读官方键 `spring.datasource.*` 与 `spring.datasource.hikari.*` / `spring.datasource.druid.*`（druid 键名对齐 druid-starter 生态约定）；手工装配保留（装配顺序、Druid filter 别名预校验、统一启动日志均为行为件）。
- **BREAKING** `zone.middleware.db.pool-type` 删除，连接池切换改用官方键 `spring.datasource.type`（缺省 HikariCP，Druid 显式声明 FQCN）。
- **BREAKING** `zone.middleware.db.flyway.*` 删除，Flyway 迁移控制改用官方键 `spring.flyway.*`，`ZoneFlywayEnablementEnvironmentPostProcessor` 短路机制随之删除。
- LOCAL/CLOUD 模式预设保留为唯一的 zone 行为键（`zone.middleware.db.mode` / `zone.middleware.redis.mode` 不变），预设值改由 `EnvironmentPostProcessor` 以低优先级注入官方键默认值（显式配置总是优先）。
- `zone.middleware.db.mybatis.pagination-enabled / mapper-package` 保留（分页拦截器自动装配与 Mapper 自动扫描为官方 MP starter 没有的行为件）。
- 接入手册、README 同步去方言化：删除 redis/db 两大节转译键表，改写为官方键 + 预设说明（手册净变薄）。
- 存量消费方 zone-boot-template 的 yml 迁移不在本仓库 apply 范围内（openspec 仓库边界约束），随本变更产出迁移对照指引，在其项目侧另行执行。

## Capabilities

### New Capabilities

（无——本变更是既有能力的键面替换，不引入新系统行为。）

### Modified Capabilities

- `database-access`: 连接信息与池参数来源从 `zone.middleware.db.*` 改为官方键；连接池切换键改为 `spring.datasource.type`；新增 Druid filter 别名预校验的规格化表述（该行为 v1.57 已实现但从未入规格）。
- `database-orm`: Flyway 迁移控制键从 `zone.middleware.db.flyway.*` 改为官方 `spring.flyway.*`。
- `redis-operations`: 连接配置来源从 `zone.middleware.redis.*` 改为官方 `spring.data.redis.*`，工厂装配来源改为官方自动装配。

## Impact

- 受影响代码：`zone-middleware-redis`（ZoneRedisAutoConfiguration/ZoneRedisProperties 大幅收缩）、`zone-middleware-database`（ZoneDatabaseProperties 键面替换、ZoneDataSourceAutoConfiguration 绑定源更换、ZoneFlywayEnablementEnvironmentPostProcessor 删除、新增预设 EEP）、两个模块全部测试。
- 受影响消费方：zone-boot-template（application.yml + 三个 profile，约 30 行键引用；本仓库外，另行迁移）。
- 受影响文档：docs/接入手册.md（§5–7、§11、§13 重写）、README.md 配置总表、本项目 AGENTS.md 存根与总纲变更记录（apply 完成后登记）。
- 依赖无新增：不引入 druid-spring-boot-3-starter 等官方 starter，仍为裸 druid 手工装配（避免 starter 自动装配语义接管带来的回归面）。
- 端口/密钥/中间件无变动。
