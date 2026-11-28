# Proposal

## Why

上游接入实践（2026-11-27 反馈）暴露四处待完善：统一返回体成功码语义不直观（`SUCCESS` 字符串，应为 HTTP 习惯的 `200`）且成功场景无法携带自定义 message；fastjson2 序列化逻辑散落在 redis/http/security 等模块各自实现（ZoneRedisTemplate 私有序列化方法），缺生态级统一收口；http 模块自研内存态熔断与主流熔断框架职责重叠，应让位 Sentinel 等专业方案；签名校验失败排障时缺少服务端签名计算过程的可见性。

## What Changes

- **BREAKING** common：`ZoneResult` 成功码由 `SUCCESS` 改为 `200`；新增 `ok(data, message)` 重载（成功响应支持自定义消息）；失败码保持取自错误对象异常码
- common：新增 `JsonUtil` 通用 JSON 工具（`toJson` / `fromJson(Class)` / `fromJson(TypeReference)`，null 容错），作为生态 JSON 序列化统一收口；`ZoneResult.toJson`、redis/http/security 模块的序列化调用迁移至 JsonUtil
- **BREAKING** http：移除 `ZoneCircuitBreaker` 全链（类、`zone.middleware.http.circuit-breaker.*` 配置、模板内熔断判定、`SYS-3002 CIRCUIT_OPEN` 错误码、`circuitBreakerState()`）；熔断诉求由上游接入 Sentinel 等专业熔断框架承接
- security：`ZoneSignCheckInterceptor` 签名比对环节以 `ZoneLog` debug 级输出三要素——传入的 `Zone-Access-Token` 值、服务端参与计算签名的请求参数、服务端计算出的签名值
- **BREAKING** 包结构调整：`ZoneRedisTemplate` 迁至 `com.zone.middleware.redis.service`、`ZoneHttpTemplate` 迁至 `com.zone.middleware.http.client`；迁移后 redis/http 模块根包清空

## Capabilities

### New Capabilities

- `json-serialization`: 生态统一 JSON 工具 JsonUtil（fastjson2 底座，null 容错，Class/TypeReference 反序列化）

### Modified Capabilities

- `common-result`: 成功码改为 200；新增 ok(data, message) 成功自定义消息重载
- `http-client`: 移除「熔断保护」需求（让位 Sentinel，含迁移路径）
- `api-sign-auth`: 新增签名比对调试日志需求（token / 参与参数 / 计算结果三要素）

## Impact

- **代码**：common（JsonUtil、ZoneResult、SystemError 移除 CIRCUIT_OPEN）、redis（模板迁包 + 序列化收口）、http（熔断全链移除 + 模板迁包）、security（签名比对埋点）
- **上游影响（破坏性，SNAPSHOT 期）**：`ZoneResult.SUCCESS_CODE` 值变更（"SUCCESS"→"200"）；`zone.middleware.http.circuit-breaker.*` 配置失效（Boot 宽松绑定忽略未知项不报错）；`ZoneRedisTemplate` / `ZoneHttpTemplate` 包路径变更；`circuitBreakerState()` / `SystemError.CIRCUIT_OPEN` 移除
- **文档**：README 与接入手册全量同步（含 §14 迁移对照表新增条目）
