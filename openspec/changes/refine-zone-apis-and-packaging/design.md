# Design

## Context

见 proposal.md - Why。当前 5 模块（common/redis/database/log/http + security/web）已全量落地并有上游模板项目接入；本次为上游反馈的四项 API 精修 + 包结构整理，全部为 SNAPSHOT 期破坏性变更（无发版负担）。

## Goals / Non-Goals

**Goals**：ZoneResult 成功码 200 + 自定义成功消息；JsonUtil 生态收口；http 熔断全链移除（让位 Sentinel）；签名比对三要素 debug 埋点；redis/http 模板类迁入语义子包。

**Non-Goals**：不引入 Sentinel 依赖（由上游自选熔断框架）；不改失败码段语义（SYS-/BIZ- 前缀体系保留）；不动 security 既有校验链顺序。

## Decisions

### D1. ZoneResult 成功码 = "200"（保持 String 类型）

- `SUCCESS_CODE` 常量值 "SUCCESS" → "200"（HTTP 习惯语义，调用方可直接按 code 判成功）
- code 字段保持 String：失败码为 `SYS-xxxx`/`BIZ-xxxx` 异常码，强转 int 会撕裂类型语义
- 新增 `ok(T data, String message)` 重载；`ok()`/`ok(data)` 消息默认「成功」不变

### D2. JsonUtil（common.util，fastjson2 底座）

- 三个入口：`toJson(Object)`、`fromJson(String, Class<T>)`、`fromJson(String, TypeReference<T>)`；null/空一律容错返回 null
- 收口范围：`ZoneResult.toJson`、`ZoneRedisTemplate` 私有序列化（保留模板自身的非空约束语义，内部委托 JsonUtil）、`ZoneHttpTemplate`（body 序列化 + 响应解析）、`ZoneSignUtils.convertValue`
- ZoneLog 的对象渲染不收口：log 模块不依赖 common（保持模块独立），其 fastjson2 直连属渲染实现细节

### D3. http 熔断全链移除

- 删除：`ZoneCircuitBreaker` 类、`ZoneHttpProperties.CircuitBreaker` 嵌套配置、模板内 tryAcquire/record 链路与 `circuitBreakerState()`、`SystemError.CIRCUIT_OPEN`
- 保留：可配置重试（退避/状态码/IO 重试）——与熔断解耦，仍由模板承载
- 迁移路径：上游接入 Sentinel 以资源名包裹模板调用；手册 §14 登记迁移条目

### D4. 签名比对三要素 debug 埋点

- 位置：`ZoneSignCheckInterceptor.verifySign` 第 7 步，计算 `expectedSign` 与归一 `actualSign` 之后、`MessageDigest.isEqual` 比对之前
- 级别：debug（含凭证形态与业务参数，默认 INFO 门槛不输出；排障时上游临时调 `zone.middleware.log.level=debug`）
- 内容：`签名比对: zone-access-token={}, 服务端参与参数={}, 服务端计算签名={}`——参数 Map 经 ZoneLog 对象渲染为 JSON

### D5. 包结构（迁移后根包清空）

- `ZoneRedisTemplate` → `com.zone.middleware.redis.service`；两个集成测试同步迁入 service 包（与主类同包，无需 import）
- `ZoneHttpTemplate` → `com.zone.middleware.http.client`；模板类三个测试（Template/TypeReference/Retry）同步迁入 client 包，AutoConfiguration 与根包测试补 import
- redis/http 模块根包自此清空（目录随文件迁移消失）；其余模块包结构已合理（security 的 annotation/config/context/sign/token/web、web 的 advice/config/filter）

## Risks / Trade-offs

- [SUCCESS_CODE 值变更] → 上游若按 `code=="SUCCESS"` 判等需改为 `"200"` 或 `isSuccess()`；SNAPSHOT 期未发版，手册 §14 登记
- [熔断移除] → 已开启 circuit-breaker 的上游失去快速失败保护；迁移窗口期建议先接 Sentinel 再升级本版本（宽松绑定忽略未知配置，不会启动失败）
- [包路径变更] → 上游 import 路径更新；fastjson2 序列化行为不变，仅编译期影响
- [debug 埋点含参数与凭证形态] → debug 级别默认关闭；文档明示排障用途，不建议生产长期开启

## Migration Plan

1. 上游自查 `SUCCESS` 字面量判等 → 改 `isSuccess()`（推荐）或 `"200"`
2. 上游熔断配置（如有）→ 接入 Sentinel 资源规则后删除 `zone.middleware.http.circuit-breaker.*`
3. 上游 import 更新：`redis.service.ZoneRedisTemplate`、`http.client.ZoneHttpTemplate`
4. 回滚：本包未发版，revert 提交即可

## Open Questions

（无）
