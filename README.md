# zone-middleware-support

Zone 中间件支撑二方库（Maven 多模块项目）。为 Zone 生态的 Spring Boot（JDK 21+）应用提供统一返回体、错误码体系、通用工具类、Redis/数据库操作封装、统一日志格式与 HTTP 客户端模板。

## 项目概览
- GroupId: com.zone.middleware
- ArtifactId: zone-middleware-support
- Version: 1.0.0-SNAPSHOT
- Packaging: pom（父项目）
- JDK 要求: 21（只支撑 21 及以上，不做低版本兼容）
- Spring Boot: 2.7.18 BOM
- JSON: 统一 fastjson2（**不引入 jackson**，全模块依赖树无 jackson 构件）
- 包名前缀: com.zone.middleware

## 模块与能力

| 模块 | 能力 |
|---|---|
| zone-middleware-common | `ZoneResult` 统一返回体；`ZoneError` 错误体系（`SystemError`/`BusinessError`）+ `ZoneBusinessException`；工具类：`ZoneIpUtils`（客户端 IP）、`ZoneBrowserUtils`（浏览器指纹）、`ZoneRandomUtils`（分段随机字符串）；`BaseRequest` 基础请求载体 + `Platform`/`OsType` 枚举 |
| zone-middleware-log | `ZoneLog` 统一日志静态门面（{} 占位符 + Object... 变参，无 AOP），`ZoneTraceContext` traceId 上下口；库内各模块埋点统一使用 ZoneLog |
| zone-middleware-redis | `ZoneRedisTemplate`：String/Hash/List/Set/ZSet 增删改查 + 批量；LOCAL/CLOUD 模式预设；Lettuce 连接池 |
| zone-middleware-database | 数据源 + HikariCP/Druid 双连接池切换；MyBatis-Plus（分页插件、mapper 扫描）；Flyway 迁移；**不含 JPA** |
| zone-middleware-http | `ZoneHttpTemplate`：JDK 21 内置 HttpClient；GET/POST、自定义鉴权、并发上限、重试、熔断 |

## 包结构

```
com.zone.middleware
├── common                                 # zone-middleware-common
│   ├── result      ZoneResult
│   ├── error       ZoneError / SystemError / BusinessError
│   ├── exception   ZoneBusinessException
│   ├── entity      ZoneBrowserInfo
│   ├── enums       Platform / OsType
│   ├── request     BaseRequest
│   └── util        ZoneIpUtils / ZoneBrowserUtils / ZoneRandomUtils
├── log                                    # zone-middleware-log
│   ├── (root)      ZoneLog / ZoneLogLevel / ZoneLogSettings / ZoneTraceContext
│   └── config      ZoneLogProperties / ZoneLogAutoConfiguration / ZoneLogInitEnvironmentPostProcessor
├── redis                                  # zone-middleware-redis
│   ├── (root)      ZoneRedisTemplate
│   └── config      ZoneRedisProperties / ZoneRedisAutoConfiguration
├── database                               # zone-middleware-database
│   └── config      ZoneDatabaseProperties / ZoneDataSourceAutoConfiguration
│                   / ZoneMyBatisPlusConfiguration / ZoneFlywayConfiguration
│                   / ZoneFlywayEnablementEnvironmentPostProcessor
└── http                                   # zone-middleware-http
    ├── (root)      ZoneHttpTemplate / ZoneCircuitBreaker
    ├── config      ZoneHttpProperties / ZoneHttpAutoConfiguration
    └── interceptor ZoneAuthInterceptor
```

约定：模块门面 API（模板/门面类）在模块根包；装配与属性类在 `config`；SPI 扩展点在 `interceptor`；
跨模块公共 POJO 在 `entity`，异常在 `exception`。全模块使用 Lombok（`optional`，不传递给上游）。

注释规范：所有方法必须携带 Javadoc——一句话功能描述 + `@param`（含泛型 `<T>`）/ `@return` / `@throws`
齐全，枚举常量与字段语义注释；工具类与私有方法同样覆盖。提交前可用
`mvn -pl <模块> -Ddoclint=all javadoc:javadoc` 校验（手写代码应零 warning）。

## 构建与安装

```bash
mvn clean install
```

各模块安装到本地 Maven 仓库后即可被上游应用引用。

## 上游接入示例

```xml
<dependency>
    <groupId>com.zone.middleware</groupId>
    <artifactId>zone-middleware-common</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<!-- 按需引入 -->
<dependency>
    <groupId>com.zone.middleware</groupId>
    <artifactId>zone-middleware-redis</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.zone.middleware</groupId>
    <artifactId>zone-middleware-database</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.zone.middleware</groupId>
    <artifactId>zone-middleware-log</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.zone.middleware</groupId>
    <artifactId>zone-middleware-http</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## 配置项总表（zone.middleware.*）

### redis（zone-middleware-redis）

| 键 | 默认 | 说明 |
|---|---|---|
| zone.middleware.redis.mode | LOCAL | 部署模式 `LOCAL / CLOUD`；CLOUD 套用更保守预设（连接/命令超时 10s、TCP 保活开），启动日志打印模式 |
| zone.middleware.redis.host / port | 127.0.0.1 / 6379 | 连接地址（共享本地实例直连即可，用独立 database 隔离） |
| zone.middleware.redis.password | — | 密码（无密码留空） |
| zone.middleware.redis.database | 0 | db index（与其他应用隔离用） |
| zone.middleware.redis.ssl | false | 是否 TLS（云端按需开启） |
| zone.middleware.redis.connect-timeout | LOCAL 2s / CLOUD 10s | 连接超时（显式配置覆盖预设） |
| zone.middleware.redis.command-timeout | LOCAL 3s / CLOUD 10s | 命令超时 |
| zone.middleware.redis.keep-alive | LOCAL false / CLOUD true | TCP 保活 |
| zone.middleware.redis.pool.max-active / max-idle / min-idle / max-wait | 8 / 8 / 0 / 2s | 连接池参数 |

```java
@Autowired ZoneRedisTemplate redis;
redis.set("user:1", user, Duration.ofMinutes(5));
User u = redis.get("user:1", User.class);
List<User> users = redis.mGet(List.of("user:1", "user:2"), User.class);
redis.deleteKeys(List.of("user:1", "user:2"));
```

值以 fastjson2 JSON 字符串存储（redis-cli 可直接阅读），读取时按指定类型还原。

### db（zone-middleware-database）

| 键 | 默认 | 说明 |
|---|---|---|
| zone.middleware.db.mode | LOCAL | `LOCAL / CLOUD`：LOCAL 默认开启 Hikari 泄漏检测(10s)便于开发；CLOUD 用更长获取超时(15s)、连接保活(120s)、关闭泄漏检测 |
| zone.middleware.db.pool-type | HIKARI | `HIKARI / DRUID` 双连接池切换 |
| zone.middleware.db.url / username / password / driver-class-name | —（url 必填） | 连接信息，缺失启动失败并报 CONFIG_ERROR |
| zone.middleware.db.hikari.maximum-pool-size / minimum-idle / connection-timeout / leak-detection-threshold / keepalive-time | 预设见上 | Hikari 池参数（显式配置覆盖预设） |
| zone.middleware.db.druid.initial-size / min-idle / max-active / max-wait / keep-alive | 2 / 2 / 8 / LOCAL 5s·CLOUD 15s / CLOUD true | Druid 池参数 |
| zone.middleware.db.mybatis.pagination-enabled | true | MyBatis-Plus 分页插件（方言自动识别） |
| zone.middleware.db.mybatis.mapper-package | — | 配置后自动扫描注册 Mapper |
| zone.middleware.db.flyway.enabled | true | 关闭后不执行迁移（联动短路 spring.flyway） |
| zone.middleware.db.flyway.locations | classpath:db/migration | 迁移脚本路径（逗号分隔） |
| zone.middleware.db.flyway.baseline-on-migrate | false（zone 默认） | 对已有库基线化 |

```java
// Mapper 直接继承 BaseMapper 即可；分页开箱即用
Page<ZoneUser> page = userMapper.selectPage(new Page<>(1, 10), null);
```

说明：事务由 Spring Boot 基于 zone 装配的数据源自动生效（`@Transactional` 正常使用）；
Druid 监控页属于应用层决策，本库不代为注册，需要时上游自行开启。

### log（zone-middleware-log）

| 键 | 默认 | 说明 |
|---|---|---|
| zone.middleware.log.app-name | spring.application.name 回退 unknown | 日志中的应用名段 |
| zone.middleware.log.level | INFO | 最低输出级别（DEBUG/INFO/WARN/ERROR） |
| zone.middleware.log.include-trace-id | true | 是否输出 traceId 段 |
| zone.middleware.log.include-app-name | true | 是否输出应用名段 |

```java
// 入参顺序：日志级别(方法名) → 类名、方法名、消息（{} 占位符）、变参；彻底告别 + 拼接
ZoneLog.info("OrderService", "createOrder", "订单{}创建成功,金额{}", orderId, amount);

// 业务数据三形态：JSON 字符串透传 / 对象自动 JSON / 多变量按序填充
ZoneLog.info("OrderService", "createOrder", "用户数据: {}", "{\"orderId\":123}");
Map<String, Object> biz = Map.of("orderId", 123, "amount", 99.9);
ZoneLog.info("OrderService", "createOrder", "订单{}创建成功", biz);

// 末位异常自动输出堆栈；或使用显式异常重载
ZoneLog.error("OrderService", "save", "订单{}保存失败", orderId, exception);
ZoneLog.error("OrderService", "save", "订单{}保存失败", exception, orderId);

// 显式级别
ZoneLog.log(ZoneLogLevel.WARN, "OrderService", "createOrder", "库存不足,订单{}", orderId);

ZoneTraceContext.setTraceId("t-20261117-001"); // 入口设置，出口 clear()
// 输出行：时间 | 级别 | 应用名 | traceId | OrderService.createOrder | 订单123创建成功,金额99.9
```

占位符渲染规则：字符串原样（JSON 透传）、数值/布尔/字符字面值、其他对象 fastjson2 JSON、
null→`null`；多于占位符的参数忽略；末位未消费的 Throwable 自动作为堆栈（SLF4J 惯例）。
埋点为显式调用，无 AOP、无切面依赖；设置在环境准备阶段即生效（先于一切 Bean 初始化日志）。

### http（zone-middleware-http）

| 键 | 默认 | 说明 |
|---|---|---|
| zone.middleware.http.connect-timeout | 5s | 连接超时 |
| zone.middleware.http.read-timeout | 10s | 单请求读超时 |
| zone.middleware.http.redirect | NORMAL | 重定向策略 NONE/NORMAL/ALWAYS |
| zone.middleware.http.max-concurrent-requests | 64 | 并发上限（信号量，超出等待） |
| zone.middleware.http.retry.enabled | false | 失败重试开关 |
| zone.middleware.http.retry.max-attempts | 3 | 最大尝试次数（含首次） |
| zone.middleware.http.retry.initial-backoff / backoff-multiplier / max-backoff | 500ms / 2.0 / 10s | 指数退避 |
| zone.middleware.http.retry.retryable-statuses | 429,502,503,504 | 可重试状态码 |
| zone.middleware.http.retry.retry-on-io-error | true | 网络异常是否重试 |
| zone.middleware.http.circuit-breaker.enabled | false | 熔断开关 |
| zone.middleware.http.circuit-breaker.failure-threshold | 5 | 连续失败阈值（达到即 OPEN） |
| zone.middleware.http.circuit-breaker.open-wait-duration | 30s | OPEN 冷却时长（过后 HALF_OPEN 探测） |

```java
@Autowired ZoneHttpTemplate http;

// 鉴权：实现 ZoneAuthInterceptor 并注册为 Bean，模板自动应用
@Bean
ZoneAuthInterceptor tokenAuth() {
    return builder -> builder.header("Authorization", "Bearer xxx");
}

User user = http.get("https://api.example.com/user/1", User.class);
ZoneResult<Order> result = http.post(baseUrl + "/order", orderReq, ZoneResult.class);
```

失败语义：非 2xx（重试耗尽或不可重试）、IO 异常、熔断 OPEN 均抛
`ZoneBusinessException`（错误码 `SYS-3001 HTTP_ERROR` / `SYS-3002 CIRCUIT_OPEN`，消息含状态码）。

## 错误码、返回体与基础请求（common）

```java
ZoneResult.ok(data);                            // success=true, code=SUCCESS
ZoneResult.fail(BusinessError.DATA_NOT_FOUND);  // success=false, code=BIZ-4004
ZoneResult.fail(SystemError.CONFIG_ERROR, "缺少 redis 配置");

throw new ZoneBusinessException(BusinessError.PARAM_INVALID, "订单号不能为空");

// 请求 DTO 继承 BaseRequest，统一携带 reqId/platform/ip/deviceId/os/version/timestamp/sign
// platform 与 os 为字符串透传（未知值、大小写形态原样保留）；枚举是上层解析工具：
public class OrderQueryRequest extends BaseRequest {
    private String orderNo;
}
Platform platform = Platform.of(request.getPlatform()); // "web" → WEB；未知值 → null
OsType os = OsType.of(request.getOs());                 // "harmony" → HARMONY
```

- SystemError（SYS- 前缀）：CONFIG_ERROR / DB_ERROR / REDIS_ERROR / HTTP_ERROR / CIRCUIT_OPEN / SERVICE_UNAVAILABLE / INTERNAL_ERROR
- BusinessError（BIZ- 前缀）：PARAM_INVALID / UNAUTHORIZED / FORBIDDEN / DATA_NOT_FOUND / DATA_CONFLICT / BUSINESS_FAILED

## 注意事项
- 本库为二方库，不监听任何端口、不启动任何服务；共享 MySQL/Redis 由上游应用直连并以独立 database / db index 隔离。
- MySQL 驱动由上游应用自行引入（如 `com.mysql:mysql-connector-j`），本库不锁定驱动版本；测试使用 H2。
- 引入本库后 JSON 处理统一到 fastjson2；请勿在本库模块中混入 jackson 依赖。
- Flyway 版本随 Boot 2.7.18 BOM（8.5.13）；上游如遇 MySQL 新版本兼容问题可自行覆盖 flyway 版本。
- 浏览器 UA 解析基于开源 UserAgentUtils（eu.bitwalker），已隔离在 `ZoneBrowserUtils` 单点，后续可替换。
- 本项目前身为 shoan-platform-support，2026-11-15 更名为 zone-middleware-support（Maven 坐标与包前缀同步变更）。
