# Design

## Context

仓库现状为纯 Maven 骨架：父 pom（Spring Boot 2.7.18 BOM、`release=21`）+ 5 个空模块，pom 中存在与工作区规范冲突的依赖（jackson、双连接池、JPA、AOP、Jedis、logstash-encoder）。本设计给出全部能力的实现方式。见 proposal.md - Why。

## Goals / Non-Goals

**Goals**

- 6 个模块全部落地可用实现 + 单元测试；上游引依赖 + `zone.middleware.*` 配置即用
- 全链路无 jackson；JSON 统一 fastjson2
- Redis / DB 的 LOCAL|CLOUD 模式预设机制，可逐项覆盖
- DB 双连接池（Hikari/Druid）切换 + MyBatis-Plus + Flyway；HTTP 重试 + 熔断

**Non-Goals**

- 不支持 JDK < 21；不升级 Spring Boot 3.x（上游生态仍在 2.7）
- 不做 JPA、不做日志采集/传输、不做 HTTP 熔断集群共享状态
- 不提供集群/哨兵 Redis（单机连接，后续按需扩展）

## Decisions

### D1. JSON：fastjson2 2.0.53（父 pom dependencyManagement 统一版本）

- 所有模块序列化/反序列化统一走 `com.alibaba.fastjson2:fastjson2`
- 替换点：common（ZoneResult）、redis（值序列化器）、http（请求/响应体）
- 附带清理：log 模块移除 `logstash-logback-encoder`（传递 jackson），统一格式用 logback pattern 实现

### D2. 模块依赖收敛（各 pom 只保留实际使用）

| 模块 | 依赖调整 |
|---|---|
| common | 纯 Java 库：仅 fastjson2、`jakarta.servlet:jakarta.servlet-api`（provided）、UserAgentUtils；**移除** spring-boot-starter、commons-lang3（均未使用） |
| log | 保留 starter；**新增** fastjson2（业务数据序列化）；slf4j/logback 由 starter 传递 |
| redis | 保留 starter-data-redis、commons-pool2、fastjson2；**移除** jedis、jackson、embedded-redis、common（未使用）；**新增** 依赖 log（库内埋点统一 ZoneLog） |
| database | 保留 starter-jdbc、mybatis-plus-boot-starter 3.5.3.1、HikariCP、`com.alibaba:druid` 1.2.18（不用 starter，手工装配避免其自动配置干扰）、flyway-core（BOM 8.5.13）、h2(test)；**移除** starter-data-jpa、mysql-connector-java（驱动由上游自备，避免锁定驱动版本）；**新增** 依赖 common、log |
| http | **移除** starter-web（JDK HttpClient 不需要）；保留 starter；**新增** fastjson2、依赖 common、log |

- junit4 声明全部移除，统一用 starter-test 自带的 JUnit 5
- 自动装配注册用 `META-INF/spring.factories`（Boot 2.7 经典方式）

### D3. common：返回体 / 错误体系 / 工具类

- `ZoneResult<T>`：`success/code/message/data/timestamp`；`code` 为字符串，成功码常量 `SUCCESS`；静态工厂 `ok()/ok(data)/fail(ZoneError)/fail(ZoneError, message)`
- `ZoneError` 接口（`getCode()/getMessage()`）；`SystemError` 枚举码段 `SYS-`（CONFIG/DB/REDIS/HTTP/CIRCUIT_OPEN/SERVICE_UNAVAILABLE/INTERNAL），`BusinessError` 枚举码段 `BIZ-`（PARAM_INVALID/UNAUTHORIZED/FORBIDDEN/DATA_NOT_FOUND/DATA_CONFLICT/BUSINESS_FAILED）
- `ZoneBusinessException extends RuntimeException`：持 `ZoneError`，可覆盖消息
- `ZoneIpUtils.getClientIp(request)`：按 XFF→X-Real-IP→Proxy-Client-IP→WL-Proxy-Client-IP 顺序，跳过空/`unknown`，XFF 取第一段 trim，兜底 `remoteAddr`
- `ZoneBrowserUtils`：UA 解析用开源 `eu.bitwalker:UserAgentUtils:1.21`（轻量、无 jackson 传递；备选 yauaa 过重）；指纹标识 = UA+Accept+Accept-Language 规范化拼接后 SHA-256 十六进制
- `ZoneRandomUtils.randomString(length, connector, segmentLength)`：SecureRandom、字符集 `[a-zA-Z0-9]`；连接符为空则不分段；末段截断到总长；非法参数抛 IAE

### D4. redis：Lettuce + 模式预设 + ZoneRedisTemplate

- `ZoneRedisProperties`（`zone.middleware.redis`）：`mode/host/port/password/database/ssl/connect-timeout/command-timeout/keep-alive/pool.*`；字段可空 = 未配置，`resolved*()` 方法按 mode 填预设
- 预设：LOCAL {connect 2s, command 3s}；CLOUD {connect 10s, command 10s, keepalive on, ssl 默认仍关（云端 TLS 由上游按需开）}
- 装配：`LettuceConnectionFactory`（commons-pool2 开启连接池）+ key 用 String 序列化 + value 用 fastjson2 序列化器
- `ZoneRedisTemplate`：String/Hash/List/Set/ZSet 增删改查 + `mGet(keys, type)` / `delete(keys)` / `hDel(key, fields...)`，读取方法带 `Class<T>` 目标类型
- 测试：预设解析与序列化为纯单测；读写操作测试连接共享实例 `localhost:6379`（db 15 隔离，`assumeTrue(ping)` 不可达时跳过）

### D5. database：双连接池切换 + 预设 + MyBatis-Plus + Flyway

- **自动配置排序（关键）**：Boot 自动配置按类名字母序评估，`com.zone...` 排在 `com.baomidou...`、`org.springframework...` 之后，而 MybatisPlus/事务管理器的装配带 `@ConditionalOnSingleCandidate(DataSource)`、Spring 的 DataSource/Redis 自动装配带 `@ConditionalOnMissingBean`——若不干预，它们会先于 zone 配置被评估而回退或用错默认值。因此 `ZoneDataSourceAutoConfiguration` 显式 `@AutoConfigureBefore({DataSourceAutoConfiguration, DataSourceTransactionManagerAutoConfiguration, MybatisPlusAutoConfiguration})`，`ZoneRedisAutoConfiguration` 显式 `@AutoConfigureBefore(RedisAutoConfiguration)`，其余 zone 配置 `@AutoConfigureAfter(ZoneDataSourceAutoConfiguration)`

- `ZoneDatabaseProperties`（`zone.middleware.db`）：`mode/pool-type/url/username/password/driver-class-name/hikari.*/druid.*/mybatis.*/flyway.*`
- 预设（可覆盖）：LOCAL {Hikari leakDetection=10s 便于开发，Druid maxWait=5s}；CLOUD {Hikari connectionTimeout=15s、keepalive=120s、leakDetection=0，Druid maxWait=15s、keepAlive=true}；两池均 validationQuery=SELECT 1
- `ZoneDataSourceAutoConfiguration` 手工构建 HikariDataSource / DruidDataSource（不依赖各家 starter 自动装配，行为可控），Boot 事务自动装配照常生效
- MyBatis-Plus：`MybatisPlusInterceptor` + `PaginationInnerInterceptor` 默认装配（`mybatis.pagination-enabled` 可关）；`mybatis.mapper-package` 非空时 `@MapperScan`
- Flyway：注册 `FlywayConfigurationCustomizer`，对未显式配置的项套用 zone 默认（locations=classpath:db/migration、baselineOnMigrate=true），`flyway.enabled=false` 关闭（Boot 自动装配复用同一 DataSource）
- 不引 JPA；依赖树校验进 verify 步骤

### D6. log：ZoneLog 静态门面（无 AOP）+ {} 占位符 + MDC traceId

- 输出行 = `时间 | 级别`（logback pattern）+ ZoneLog 组装的 `应用名 | traceId | 类名.方法名 | 消息（占位符已填充）`
- `ZoneLog`：静态门面，入参「级别、类名、方法名、消息、Object... 变参」；消息支持 SLF4J 风格 `{}` 占位符，彻底避免字符串 `+` 拼接；类名同时作为 SLF4J logger 名
- 占位符渲染：字符串原样（JSON 字符串透传）、数值/布尔/字符字面值、其他对象 fastjson2 JSON、null→"null"；多余参数忽略；末位未消费 Throwable 自动作为堆栈（SLF4J 惯例），error/log 另有显式 Throwable 重载（显式重载在调用点优先匹配）
- 7 个入口：debug/info/warn/error（变参）+ error/log（显式 Throwable + 变参）+ log（显式级别 + 变参）；**移除上一版「业务数据独立参数」重载**——它与占位符变参在全 String 调用点存在静默歧义（javac 会把 `("订单{}处理", "OID-1")` 解析成 bizData+message 而非占位符填充）
- `ZoneLogSettings` 快照（appName 回退 spring.application.name、门槛默认 INFO、include 开关）+ 双通道应用：`ZoneLogInitEnvironmentPostProcessor`（环境阶段，先于一切 Bean 日志）+ 自动装配幂等兑底重放；traceId 用 MDC key `traceId`，`ZoneTraceContext` set/get/clear
- 不引 AOP；测试用 Logback ListAppender 断言格式
- 库内埋点统一走 ZoneLog 占位符风格（log/redis/database 自动装配、http 重试耗尽与熔断状态转换），redis/database/http 模块因此依赖 log 模块

### D7. http：JDK HttpClient + 自研重试/熔断 + 鉴权 SPI

- 底层 `java.net.http.HttpClient`（JDK 21 自带，零新增依赖，HTTP/2 与 keep-alive 连接复用）
- `ZoneHttpProperties`（`zone.middleware.http`）：connect-timeout 5s / read-timeout 10s / follow-redirects NORMAL / max-concurrent-requests 64（Semaphore 信号量承载并发上限）
- 重试（默认关）：max-attempts 3、backoff 500ms 起步 ×2 封顶 10s、可重试状态码 {429,502,503,504}、IOException 可重试；指数退避 `Thread.sleep`
- 熔断（默认关）：自研 `ZoneCircuitBreaker`，连续失败阈值（默认 5）→ OPEN，冷却 30s → HALF_OPEN 放行 1 个探测，成功 → CLOSED / 失败 → OPEN；状态机无锁（synchronized），单实例内存态
- 鉴权 SPI：`ZoneAuthInterceptor.apply(HttpRequest.Builder)`，注册为 Spring Bean 即被模板收集应用
- 错误语义：非 2xx / IO 失败 / 重试耗尽 / 熔断 OPEN → `ZoneBusinessException(SystemError.HTTP_ERROR | CIRCUIT_OPEN)`；消息含状态码
- 测试：JDK 内置 `com.sun.net.httpserver.HttpServer` 起本地端点（端口由系统分配，不占登记端口），覆盖重试、熔断、鉴权、GET/POST、超时

### D8. 配置命名空间统一

`zone.middleware.redis / db / log / http` 四个前缀，所有属性带元数据（`additional-spring-configuration-metadata` 暂不生成，README 记录全量配置表）。

### D9. common：BaseRequest 基础请求载体（2026-11-21 追加，同日修正字段类型）

- 位置：`request` 包（BaseRequest，`@Data` + Serializable）；`enums` 包（Platform / OsType）
- **platform 与 os 为 String 原样透传**：载体处于传输边界，保留客户端原文（审计/排障/协议演进），未知值与大小写形态不丢失、不抛错；枚举强转是业务层决策——上层显式 `Platform.of(request.getPlatform())` / `OsType.of(...)` 规约，未知值拿到 null 自行处理
- Platform：WEB/PC/MOBILE/PAD（值 web/pc/mobile/pad）；OsType：ANDROID/IOS/WINDOWS/HARMONY/LINUX；均带小写 value + `of()` 大小写不敏感容错解析
- timestamp 用包装 Long（可区分「未提供」与 0）；reqId/platform/ip/deviceId/os/version/sign 均 String

## Risks / Trade-offs

- [JDK21 + Boot 2.7.18 非官方支持组合] → 编译期没问题（`release=21`），运行期风险主要在 CGLIB/反射代理（本库未用）；README 注明上游如遇问题优先查代理相关配置
- [UserAgentUtils 2017 年后停更，新 UA 解析可能退化] → 已隔离在 `ZoneBrowserUtils` 单点，后续可无痛换 yauaa
- [Flyway 8.5.13（BOM 版本）对 MySQL 8.4 的版本识别] → 连接与建表无碍；如上游遇兼容问题可自行覆盖 flyway 版本（README 注明）
- [自研熔断为单实例内存态，不支持集群共享] → 二方库定位下可接受，后续如需集群策略再引入 resilience4j
- [shared Redis 测试依赖本机实例] → assumeTrue 跳过，不阻塞构建
- [Druid 手工装配绕过其 starter 的监控页] → 监控 Servlet 属于应用层决策，本库不代为注册（README 说明如何自开）

## Migration Plan

- 本库无外部消费方，无迁移负担；仓库内仅需 pom 调整 + 新增源码
- 回滚：revert 提交即可（纯新增 + pom 修改）

## Open Questions

（无）
