# Proposal

## Why

zone-middleware-support 定位为 Zone 生态的中间件支撑二方库，但当前仅有 Maven 骨架（无任何 Java 代码），上游 Spring Boot 应用引入依赖后得不到任何能力；各上游应用仍在各自重复实现返回体、错误码、Redis/数据库/HTTP 样板与日志格式。同时现有 pom 与工作区规范冲突：使用 jackson、连接池双声明未收敛、混入 JPA/AOP 等本项目不会使用的依赖。

## What Changes

- 新增 zone-middleware-common 实现：`ZoneResult` 统一返回体、`ZoneError` 错误体系（`SystemError`/`BusinessError` 两个实现 + `ZoneBusinessException`）、通用工具类（客户端 IP 解析、浏览器指纹信息提取、分段随机字符串生成）、`BaseRequest` 基础请求载体（reqId/platform/ip/deviceId/os/version/timestamp/sign，platform 与 os 为字符串原样透传）与 `Platform`（web/pc/mobile/pad）、`OsType`（android/ios/windows/harmony/linux）解析枚举（供上层方法与应用使用）
- 新增 zone-middleware-redis 实现：Redis 五种数据结构（String/Hash/List/Set/ZSet）增删改查与批量查询/删除封装；`zone.middleware.redis.mode = LOCAL|CLOUD` 部署模式声明，CLOUD 套用更保守的预设值且上游可逐项覆盖
- 新增 zone-middleware-database 实现：数据源与连接池装配（HikariCP / Druid 可切换）、`LOCAL|CLOUD` 模式声明与预设；整合 MyBatis-Plus（分页插件）与 Flyway（迁移），**不引入 JPA**
- 新增 zone-middleware-log 实现：`ZoneLog` 统一日志静态门面，入参按「日志级别、类名、方法名、消息、可变参数」构建，消息支持 `{}` 占位符填充（字符串/JSON 字符串/对象多形态渲染、末位异常自动堆栈），DEBUG/INFO/WARN/ERROR 四级多重载，显式调用埋点，**不使用 AOP**；库内各模块日志埋点统一使用 ZoneLog
- 新增 zone-middleware-http 实现：`ZoneHttpTemplate` 通用 HTTP 模板（JDK 21 内置 HttpClient）：默认/自定义客户端配置、通用 GET/POST、自定义鉴权扩展点、连接复用与并发上限、失败重试与熔断保护
- **BREAKING**（仅影响本仓库骨架，库尚无外部消费方）：全部模块 JSON 处理由 jackson 替换为 fastjson2；移除未使用依赖（spring-boot-starter-web 于 common/http、spring-boot-starter-data-jpa、spring-boot-starter-aop、jedis、logstash-logback-encoder、embedded-redis、spring-boot-starter 与 commons-lang3 于 common、mysql-connector-java 于 database）；锁定 JDK 21+，不为 17 及以下版本提供兼容

## Capabilities

### New Capabilities

- `common-result`: 统一服务返回体 ZoneResult 的结构、构造与序列化契约
- `common-error`: 错误码体系 ZoneError（系统错误 SystemError / 业务错误 BusinessError）与业务异常 ZoneBusinessException
- `common-utils`: 客户端 IP 解析、浏览器指纹信息提取、分段随机字符串生成等通用工具
- `common-request`: 基础请求载体 BaseRequest（platform/os 字符串透传）与平台 Platform / 操作系统 OsType 解析枚举
- `redis-operations`: Redis 五种数据结构操作封装、批量查询/删除与本地/云端部署模式
- `database-access`: 数据源与连接池（HikariCP/Druid 可切换）装配、本地/云端模式与上游自定义配置
- `database-orm`: MyBatis-Plus 与 Flyway 整合（明确不含 JPA）
- `log-format`: 统一日志格式、级别门槛与显式埋点（无 AOP）
- `http-client`: 通用 HTTP 请求模板、自定义鉴权、重试与熔断

### Modified Capabilities

（无——项目尚无既有 specs）

## Impact

- **代码**：5 个模块全部首次落入 src/main/java 实现与测试；父 pom 与各模块 pom 依赖治理
- **依赖**：新增 fastjson2（统一 JSON）、UserAgentUtils（浏览器指纹 UA 解析，开源工具）；保留 mybatis-plus/druid/flyway；移除 jackson、spring-boot-starter-web（common/http 模块）、spring-boot-starter-data-jpa、spring-boot-starter-aop、jedis、logstash-logback-encoder、embedded-redis
- **构建**：JDK 21 强制（`maven.compiler.release=21`），Spring Boot 2.7.18 BOM 维持不变
- **上游应用**：新增 `zone.middleware.redis / db / log / http` 配置命名空间；引入本库后 JSON 处理统一到 fastjson2
- **运行形态**：本库不监听任何端口、不启动任何服务；共享 MySQL/Redis 由上游应用直连（独立 database / db index 隔离）
