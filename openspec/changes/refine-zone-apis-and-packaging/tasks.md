# Tasks

## 1. common：JsonUtil 与 ZoneResult

- [x] 1.1 实现 `JsonUtil`（toJson / fromJson(Class) / fromJson(TypeReference)，null 容错），验证 JsonUtilTest 通过（往返、泛型、null 容错）
- [x] 1.2 `ZoneResult` 成功码改 `200`、新增 `ok(data, message)`、`toJson` 收口 JsonUtil，验证 ZoneResultTest 更新后通过（200 断言 + 自定义成功消息用例）
- [x] 1.3 `SystemError` 移除 `CIRCUIT_OPEN`（全库零引用后删除），验证 ZoneErrorTest 通过

## 2. redis：迁包与序列化收口

- [x] 2.1 `ZoneRedisTemplate` 迁至 `service` 包，私有 fromJson 委托 JsonUtil（保留模板非空约束），AutoConfiguration 更新 import，两个集成测试同步迁包，验证 redis 模块测试通过

## 3. http：熔断移除与迁包

- [x] 3.1 删除 `ZoneCircuitBreaker`、`ZoneHttpProperties.CircuitBreaker`、模板内熔断链路与 `circuitBreakerState()`，body 序列化/响应解析收口 JsonUtil，验证编译通过
- [x] 3.2 `ZoneHttpTemplate` 迁至 `client` 包，AutoConfiguration 与测试同步（Retry 测试删除熔断用例并更名 ZoneRetryTest），验证 http 模块测试通过

## 4. security：签名比对埋点

- [x] 4.1 `ZoneSignCheckInterceptor` 签名比对前新增 debug 三要素埋点（token/参与参数/计算签名），验证新增测试通过（ListAppender 断言三要素出现）

## 5. 文档与全局验证

- [x] 5.1 同步 README 与接入手册（code=200、ok(data,message)、JsonUtil、熔断迁移、包结构、§14 迁移表），验证文档示例与实现一致
- [x] 5.2 全量 `mvn clean install` 全绿 + `openspec validate --strict` 通过 + doclint 抽查
