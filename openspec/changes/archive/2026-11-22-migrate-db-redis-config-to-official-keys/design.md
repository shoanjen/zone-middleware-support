# Design

## 背景与目标

删除 redis/database 模块的配置键转译层：上游配置面 100% 对齐 Spring Boot 官方键（`spring.data.redis.*` / `spring.datasource.*`），本库只保留三类东西——**行为件**（ZoneRedisTemplate JSON 语义、Druid filter 校验、统一启动日志、url 缺失快速失败）、**预设机制**（LOCAL/CLOUD 模式）、**装配顺序控制**。目标是：官方文档/AI/IDE 经验直接可用，手册不再维护转译表，官方配置面长尾不再需要逐项透出。

## 架构选型

### 方案 D1：保留手工装配，键面官方化（database 模块采用）

ZoneDataSourceAutoConfiguration 手工装配逻辑保留（@AutoConfigureBefore + @ConditionalOnMissingBean），仅更换绑定源：连接信息注入 Spring 官方 `DataSourceProperties`（绑定 `spring.datasource.*`），池参数以官方键名为前缀自建绑定类。

- 优点：Druid filter 预校验、快速失败、统一启动日志等已验证行为零丢失；不引入 druid-spring-boot-3-starter（其自动装配语义接管与装配顺序魔法是本库明确回避的）；改动集中在绑定层，回归面小。
- 缺点：官方键空间下仍存在自建装配（但这是"行为件"而非"转译件"，属本库价值所在）。

### 方案 D2：全委托官方装配（redis 模块采用）

`LettuceConnectionFactory` 不再由本库构建——官方 `RedisAutoConfiguration` 对 host/port/password/database/ssl/connect-timeout/timeout/pool 参数均有原生键，Boot 3.3+ 原生支持 `spring.data.redis.lettuce.keep-alive`。ZoneRedisAutoConfiguration 收缩为模板 Bean 装配（@AutoConfigureAfter 官方装配），ZoneRedisProperties 仅保留 `mode`。

- 原自建工厂逐项对照：连接参数 → `spring.data.redis.*`；SocketOptions connectTimeout → 官方 connect-timeout 键；keepAlive → `spring.data.redis.lettuce.keep-alive`；commandTimeout → `spring.data.redis.timeout`；连接池 → `spring.data.redis.lettuce.pool.*`（blockWhenExhausted 官方默认即 true）。无能力损失。
- 实施时需逐一验证官方键在 Boot 3.5.6 的映射生效（尤其 lettuce.keep-alive），见任务清单。

### 未采用：database 也走 D2（druid 委托官方 starter）

druid 侧官方路径需引入 druid-spring-boot-3-starter，其自动装配将接管数据源创建与 filters 处理（对未知 filter 别名同样是静默跳过，昨日新增的预校验会失效），且装配顺序与 zone 现有 @AutoConfigureBefore 链路需要重新验证，回归风险与收益不成比例。若日后 module 需要扩展更多 druid 长尾参数，再评估切官方 starter。

## 键面映射总表

| zone 键（删除） | 官方替代键 |
|---|---|
| `zone.middleware.db.url/username/password/driver-class-name` | `spring.datasource.url/username/password/driver-class-name` |
| `zone.middleware.db.pool-type` | `spring.datasource.type`（Druid 填 FQCN；缺省 HikariCP） |
| `zone.middleware.db.hikari.*` | `spring.datasource.hikari.*` |
| `zone.middleware.db.druid.initial-size/min-idle/max-active/max-wait/keep-alive/filters` | `spring.datasource.druid.*`（键名与 druid-starter 生态一致） |
| `zone.middleware.db.flyway.enabled/locations/baseline-on-migrate` | `spring.flyway.enabled/locations/baseline-on-migrate` |
| `zone.middleware.redis.host/port/password/database/ssl` | `spring.data.redis.host/port/password/database/ssl` |
| `zone.middleware.redis.connect-timeout` | `spring.data.redis.connect-timeout` |
| `zone.middleware.redis.command-timeout` | `spring.data.redis.timeout` |
| `zone.middleware.redis.keep-alive` | `spring.data.redis.lettuce.keep-alive` |
| `zone.middleware.redis.pool.max-active/max-idle/min-idle/max-wait` | `spring.data.redis.lettuce.pool.max-active/max-idle/min-idle/max-wait` |

**保留的 zone 键**（均为行为键，无官方对应）：`zone.middleware.db.mode`、`zone.middleware.redis.mode`（部署模式预设）、`zone.middleware.db.mybatis.pagination-enabled / mapper-package`（MP 官方 starter 无此行为）、log/security/http/common 全部键。

## 预设注入机制

新增 `ZoneDbPresetEnvironmentPostProcessor`（database 模块）与 `ZoneRedisPresetEnvironmentPostProcessor`（redis 模块），注册进各自 spring.factories：

1. **执行顺序**：`Ordered.HIGHEST_PRECEDENCE + 20`（沿用 log EEP 约定，晚于 ConfigData 加载的 +10，此时用户 application.yml 已可见）。
2. **读取** `zone.middleware.db.mode` / `zone.middleware.redis.mode`，解析失败按现有行为留给装配期报错（EEP 不抛）。
3. **注入**：将 LOCAL/CLOUD 预设值以官方键写入 `MapPropertySource` 并 `addLast`——位于所有用户配置源之后（最低优先级），实现"显式配置总是赢、未配置项才吃预设"，且环境变量/系统属性仍高于预设（12-factor 语义正确）。
4. **注入面**：db 侧同时注入 hikari 与 druid 两组预设（未被选中的池键无人消费，无害）；redis 侧注入 connect-timeout/timeout/lettuce.keep-alive/lettuce.pool.*。addLast 优先级机制天然实现"未显式配置才生效"，无需逐键探测。
5. **LOCAL 与 CLOUD 各注入一套预设**（保持现状行为不变——官方默认值并非 LOCAL 语义：Hikari 官方连接超时 30s ≠ 现状 LOCAL 5s、泄漏检测官方默认关 ≠ 现状 LOCAL 开 10s；Druid 初始/空闲连接官方默认 0/1 ≠ 现状 2/2；redis 官方超时默认 60s ≠ 现状 LOCAL 2s/3s）。CLOUD 套保守值（db：连接超时 15s、保活 120s、泄漏检测关；druid max-wait 15s、keep-alive true；redis：连接/命令超时 10s、TCP 保活开）；LOCAL 套开发值（db：连接超时 5s、泄漏检测 10s；druid max-wait 5s、keep-alive false；redis：连接超时 2s、命令超时 3s、保活关）；初始/空闲/最大连接数等与模式无关的默认值两模式相同。

## 装配变更要点

**database**：
- `ZoneDataSourceAutoConfiguration`：注入官方 `DataSourceProperties`（url/username/password/driverClassName）；`spring.datasource.type` 解析池类型（未设或解析为 Hikari → Hikari；Druid FQCN → Druid；其他不可解析值启动失败）；`buildHikari/buildDruid` 参数改从官方前缀绑定类读取（`@ConfigurationProperties(prefix="spring.datasource.hikari")` / `(prefix="spring.datasource.druid")` 自建最小 POJO，字段名对齐官方语义）；`applyDruidFilters` 改读 `spring.datasource.druid.filters`，别名注册表校验逻辑不变；url 缺失快速失败文案改为指向 `spring.datasource.url`。
- `ZoneDatabaseProperties`：仅保留 `mode` 与 `mybatis` 段，删除连接/druid/hikari/flyway 字段与 resolved* 预设方法（预设移入 EEP）。
- `ZoneFlywayConfiguration`：保留 `FlywayConfigurationCustomizer` 仅对未显式配置的 `spring.flyway.locations/baseline-on-migrate` 套默认值；删除 zone flyway 键分支与 `ZoneFlywayEnablementEnvironmentPostProcessor`（及其 spring.factories 注册）；`flyway-mysql` 依赖保留。

**redis**：
- `ZoneRedisAutoConfiguration`：删除 `zoneRedisConnectionFactory` Bean 与 `@AutoConfigureBefore`，改 `@AutoConfigureAfter(RedisAutoConfiguration.class)`；`zoneStringRedisTemplate`/`zoneRedisTemplate` 依赖官方装配的 `LettuceConnectionFactory`；启动日志改为基于 `RedisProperties`（官方绑定类）与 mode 打印生效值（关键字 `zone-redis enabled` 保留，§12 验证表不失效）。
- `ZoneRedisProperties`：仅保留 `mode`；删除连接与池字段及 resolved* 方法。
- 新增 spring.factories 注册 `ZoneRedisPresetEnvironmentPostProcessor`。

## 兼容性决策

- **一次性断裂，不做双读过渡**：zone 键与官方键双读 = 两个真源，配置排查成本高于方言本身；当前仅 zone-boot-template 一个消费方且在同一工作区，BREAKING 窗口可控。不提供弃用告警期。
- spring.factories / AutoConfiguration.imports 注册面变化不涉及对外 API；`ZoneRedisTemplate`/`ZoneHttpTemplate` 等 Bean 名称与类型不变，上游代码零改动（只动 yml）。

## 测试策略

- 全量替换测试中的 zone 连接键为官方键（ZoneDataSourceConfigurationTest/ZoneRedis 相关/ZoneDatabasePropertiesTest/ZoneFlywayIntegrationTest/ZoneMyBatisPlusIntegrationTest）。
- 新增 EEP 预设测试：CLOUD 未显式配置 → 官方键生效预设值；CLOUD 显式配置 → 上游值赢；LOCAL → 无注入。
- 新增官方键绑定测试：spring.datasource.type 切 Druid、不可解析 type 启动失败、spring.datasource.druid.filters 生效与未注册别名快速失败、spring.flyway.enabled=false 关迁移。
- redis 骑官方工厂回归：官方键装配下 ZoneRedisTemplate 读写 + CLOUD 预设 + lettuce.keep-alive 映射验证（Boot 3.5.6）。

## 文档与消费方迁移

- 接入手册：§3.2 配置义务表改官方键名（spring.datasource.url / security.sign.secret）；§5/§6/§7 的配置表与示例改为官方键 + mode 说明；§11 properties 全量参考同步；§13 排错条目反转（"Redis 连的还是默认地址"改为"检查是否写成了 zone 键"）；新增 zone 键 → 官方键迁移对照表（即 design 键面映射总表）。
- README 配置总表同步。
- **消费方迁移（本仓库外）**：zone-boot-template application.yml + 三个 profile 按对照表替换（约 30 行），完成后 `mvn clean package` + 启动验证（`zone-datasource init`/`zone-redis enabled` 关键字命中、8212 端口正常）；此步骤随本变更的迁移指引在 boot-template 项目侧执行，不在本仓库 apply 范围内。

## 风险与对策

| 风险 | 对策 |
|---|---|
| EEP addLast 时序与 ConfigData 加载顺序 | EEP order 取 +20（晚于 ConfigData 的 +10），测试覆盖"显式配置覆盖预设"；若发现同 order 竞争，下调至 +30 |
| 预设注入与 spec LOCAL 语义冲突（实施中发现：官方默认值并非 LOCAL 语义） | 已修正本节：LOCAL/CLOUD 各注入一套预设保持现状行为，spec 不变 |
| Boot 3.5.6 官方 lettuce.keep-alive 键映射存疑 | **实施中证实并升级为双路径**：该键在 Boot 3.5.6 不存在（RedisProperties.Lettuce 无 keepAlive 字段），且 Boot 3.4+ 专用定制器 `LettuceClientOptionsBuilderCustomizer` 在 3.3.5 会因类缺失导致配置类内省崩溃（zone-boot-template SB 3.3.5 实测）——最终落地为：3.4+ 走 `LettuceClientOptionsBuilderCustomizer`（类级条件隔离）；3.1–3.3 走 `LettuceClientConfigurationBuilderCustomizer` 复刻官方非 SSL 独立 ClientOptions（纯 builder + socketOptions(connectTimeout) + timeoutOptions(enabled)）并附加 keepAlive；SSL 开启时 legacy 路径跳过（避免丢失 SslOptions），已在手册声明 |
| `spring.data.redis.ssl` 旧布尔键在 Boot 3.1+ 是对象键（ssl.enabled） | 文档与消费方迁移统一使用 `ssl.enabled`，消费方 yml 直接删除 `ssl: false`（缺省即关） |
| spring.datasource.hikari/druid 前缀自建绑定与 Boot 内部绑定冲突 | 自建 POJO 是独立绑定，不与官方 DataSourceProperties 子绑定共享实例；测试覆盖 |
| 双池预设键同时注入造成误解 | 注入源命名清晰（zone-db-presets）；未被选中的键无消费者，行为无副作用 |
| zone-boot-template 迁移遗漏 profile 文件 | 对照表明确列出四个 yml；迁移指引含 grep 自查命令 |
