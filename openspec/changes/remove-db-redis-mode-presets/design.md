# Design

## 背景与目标

彻底移除 LOCAL/CLOUD 部署模式预设层：`mode` 键、两个预设注入 EEP、按 mode 派生的 keep-alive 双路径装配全部删除；池参数、超时时间等完全交还给 Spring Boot 官方键（显式配置生效、未配置即官方默认）。http 模块三个与官方 `spring.http.client.*` 重复的键一并迁移。目标态：本仓库配置面 = 官方键 + 少量无官方对应的行为键（`zone.middleware.db.mybatis.*`、`zone.middleware.http.max-concurrent-requests` / `retry.*`、web/security/log 全部键）。

## 关键决策

### D1 预设层整体删除而非保留 mode（沿用既有「一次性断裂」兼容策略）

mode 的唯一价值是选择两套预设值；预设的载体是官方键注入（EEP addLast 最低优先级）。删除注入器后 mode 无消费者。与上一轮相同：SNAPSHOT 期一次性断裂，不做双读过渡，消费方随迁移对照表调整 yml。

### D2 keep-alive 双路径配置类删除，不改为 zone 行为键

`ZoneRedisKeepAliveAutoConfiguration`（Boot 3.4+ 路径）与 `ZoneRedisLegacyKeepAliveConfiguration`（3.1–3.3 路径）的唯一输入是 `parsedMode() == CLOUD`，无独立配置键。若为其新造 `zone.middleware.redis.keep-alive` 键，等于在已声明「zone 键不承载连接语义」的能力上重新引入方言。需要 TCP keepalive 的上游自行声明 customizer Bean（接入手册给 3.4+ 与 3.1–3.3 两套配方，代码摘自被删实现）。

### D3 池/超时默认值回归官方，不再代码兜底

EEP 删除后：lettuce 未配置 `spring.data.redis.lettuce.pool.*` 即不启用连接池（官方语义）；Druid initial/min/max 回归 Druid 默认；Hikari 超时回归 HikariCP 默认（连接超时 30s、无泄漏检测）。接入手册「生产建议配置」小节给出推荐官方键清单（替代原 CLOUD 预设的保守值：连接/命令超时 10s、池 max-active 8、hikari connection-timeout 15s、druid max-wait 15s 等）。

### D4 http 官方键采用自建绑定类承接

新增 `ZoneOfficialHttpClientProperties`（仿 `ZoneOfficialDataSourceProperties` 模式：自建最小 POJO 绑定 `spring.http.client` 前缀，字段 connectTimeout / readTimeout / redirects），不依赖 Boot 内部 `ClientHttpRequestFactorySettings` 类型（规避多版本 API 隔离问题）。`ZoneHttpTemplate` 底层仍是裸 JDK HttpClient：

- `connect-timeout` → `HttpClient.newBuilder().connectTimeout(...)`（client 级）
- `read-timeout` → 每请求 `HttpRequest.timeout(...)`（JDK HttpClient 无 client 级读超时，维持现状每请求施加；Boot 官方键对该键在 JDK factory 上同为每请求语义，映射一致）
- `redirects` → `never` → `Redirect.NEVER`，`follow-when-possible` → `Redirect.NORMAL`；官方无 `ALWAYS` 对应，该能力舍弃（手册注明：确需无条件重定向的上游自行声明 HttpClient Bean）
- 未配置兜底：connect 5s / read 10s / NORMAL（防请求无限悬挂，属行为件；与原 zone 默认一致，不构成行为变化）
- `max-concurrent-requests`、`retry.*` 无官方对应（并发上限属 Sentinel/resilience4j 领域、Spring Retry 非 Boot 配置面），保留 zone 键

### D5 redis 启动日志保留但瘦身

`zone-redis enabled` 关键字保留（既有验证约定），日志项去掉 mode 与 keepAlive，保留 host:port / database / connectTimeout / timeout / poolMaxActive（来自官方 `RedisProperties`）。database 侧启动日志同步去掉 mode 项，保留 poolType / url。

### D6 database 键面清零：mybatis 两键一并删除

`zone.middleware.db.mybatis.pagination-enabled`（开关键）与 `mapper-package`（扫描键）均无独立存在价值：

- 分页：`PaginationInnerInterceptor` 仅在查询携带 IPage 参数时生效，不使用分页的上游完全无感——默认装配无副作用，开关键属不必要逻辑；需要自定义的上游声明自己的 `MybatisPlusInterceptor` Bean（`@ConditionalOnMissingBean` 让位），语义与「想用就引入」一致。
- 扫描：官方 MP starter 已自动扫描主应用包（自动配置包）下的 `@Mapper` 接口；跨包场景标准做法是上游 `@MapperScan`。zone 的 `ZoneMapperScannerRegistrar`（读 Environment 注册 MapperScannerConfigurer）是官方能力的换皮，删除。
- `ZoneDatabaseProperties` 随之整类删除（唯一键段即 mybatis），database 模块 `zone.middleware.db.*` 键面清零；`ZoneMyBatisPlusConfiguration` 去掉 properties 装配与扫描 Registrar。
- 集成测试验证路径调整：`ApplicationContextRunner` 无自动配置包（官方自动扫描不生效），Mapper 用例改走标准 `@MapperScan` 用户配置——与真实上游跨包用法一致。

## 兼容性

- mode 键删除后残留 yml 被 Boot 宽松绑定静默忽略，无启动错误；但池/超时默认值行为有变（见 D3），手册迁移对照表逐条列明。
- `ZoneRedisTemplate` / `StringRedisTemplate` Bean 名称与类型不变，security 模块 `@AutoConfigureAfter(ZoneRedisAutoConfiguration.class)` 链路不变，上游 Java 代码零改动。

## 风险与对策

| 风险 | 对策 |
|---|---|
| 上游从「池默认开启」跌到「无池」引发连接数突增 | 手册迁移对照表第一条显式提醒；生产建议清单给出池官方键 |
| keep-alive 删除后云端长连接被中间设备掐断 | 手册给 customizer Bean 配方（两套 Boot 版本路径） |
| `spring.http.client.*` 与 Boot 自身 RestClient 工厂配置同键共存 | 语义一致（同一键面同一意图），自建绑定独立实例不冲突；database 模块已有同模式先例 |
| ApplicationContextRunner 不执行 EEP | 预设测试随 EEP 删除，官方键测试不受影响 |
