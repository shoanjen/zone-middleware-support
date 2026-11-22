# Proposal

## Why

接入方（zone-boot-template）第二批反哺清单：P1 三项能力缺口与 P2 五项手册欠账。P1 均为"每个上游重复手写"的样板——全局异常处理器（手写 3 个 @ExceptionHandler）、Trace 入站 Filter（手写 filter）、ZoneHttpTemplate 泛型反序列化缺 TypeReference 重载（返回 ZoneResult\<T\> 时 data 退化为 JSONObject）；P2 为手册欠账（DDD 分层映射、spring-tx 传递标注、sign 废弃标注、自定义错误码章节缺失）与 Redis 常用原语缺失（incr/ttl/expire/setIfAbsent）。

## What Changes

- **新模块 `zone-middleware-web`**（suite 自动纳入，按需可单独引入）：两个**可选**入站能力，均 `@ConditionalOnProperty` 默认关，suite 存量消费方零行为变化：
  - 全局异常处理器 `@RestControllerAdvice`：`ZoneBusinessException`→`ZoneResult.fail`、`BindException`/`MethodArgumentNotValidException`→`BIZ-4001 PARAM_INVALID`（含字段级消息）、兜底 `Exception`→`SYS-9999 INTERNAL_ERROR`；`@Order(LOWEST_PRECEDENCE)` 让位上游自定义 advice；开关 `zone.middleware.web.exception-advice.enabled`。
  - Trace 入站 Filter：读取 `Zone-Trace-Id` 请求头绑定 traceId（缺失生成）、生成 reqId、响应后 clear；开关 `zone.middleware.web.trace-filter.enabled`。
- **http-client**：`ZoneHttpTemplate` 补 fastjson2 `TypeReference<T>` 重载 ×4（get/post 各含带 headers 变体），支持 `ZoneResult<Order>` 级泛型反序列化。
- **redis-operations**：`ZoneRedisTemplate` 补计数/过期/锁原语 `incr` / `incrBy` / `ttl` / `expire` / `setIfAbsent`。
- **common-request**：`BaseRequest.sign` 已 `@Deprecated`（代码现状），规格与手册同步标注废弃、下版本移除。
- **手册欠账（P2）**：§3 补「多模块/DDD 分层接入建议」（suite→装配根、database/http→infrastructure、security→web、log→service/web、common→service、domain 禁引二方包）；§2 传递表标注 log 模块传递 spring-boot-starter 不含 spring-tx（应用层 `@Transactional` 需自引，来自实际踩坑）；§4.2 补自定义错误码扩展章节（实现 `ZoneError` 接口 + 上游码段约定：SYS-/BIZ- 内置段禁用，自定义业务码建议 `BIZ-7xxx`/`BIZ-9xxx` 等未占段）；§4.3 字段表标注 `sign` 废弃；§5.3 补入站 Filter 标准样例（同步指向 web 模块可选自动装配）；新模块章节并入第 2/3 节模块总览与接入方式。

## Capabilities

### New Capabilities

- `web-inbound`: Web 入站可选支撑——全局异常处理器（三类异常→统一返回体）与 Trace 入站 Filter（traceId/reqId 绑定与清理），均可选装配、默认关、让位上游自定义组件。

### Modified Capabilities

- `http-client`: 增加 `TypeReference<T>` 泛型反序列化重载，`ZoneResult<复杂泛型>` 场景 data 不再退化为 JSONObject。
- `redis-operations`: 增加计数、过期、简单互斥原语（incr/incrBy/ttl/expire/setIfAbsent）。
- `common-request`: `sign` 字段规格表述改为废弃兼容保留（新代码不得以其承载签名，下版本移除）。

## Impact

- 新增模块接线：根 pom（dependencyManagement + modules）、zone-middleware 聚合 pom（modules）、suite pom（依赖）、两模块 README/手册模块表。
- 受影响代码：新增 zone-middleware-web 模块（advice/filter/自动装配/测试）；ZoneHttpTemplate 加 4 重载；ZoneRedisTemplate 加 5 原语；无存量行为变更（新模块默认关、重载纯增量）。
- 受影响文档：接入手册（§2 传递表、§3 分层建议、§4.2/§4.3、§5.3、新模块章节及后续章节号顺延）、README 模块与配置总表、AGENTS.md 存根、总纲变更记录（apply 后登记）。
- 依赖新增：zone-middleware-web 引 spring-webmvc + jakarta.servlet-api（provided）；suite 消费方经传递获得，非 web 应用同样零影响（能力默认关、类经 @ConditionalOnClass 保护）。
- 端口/密钥/中间件无变动。
