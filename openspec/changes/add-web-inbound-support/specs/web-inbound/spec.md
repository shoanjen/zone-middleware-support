# Spec Delta

## Purpose

提供 Web 入站的可选支撑能力：全局异常处理器（三类异常统一转 ZoneResult）与 Trace 入站 Filter（traceId/reqId 绑定与清理）。两者均为可选自动装配、默认关闭、显式让位上游自定义组件，消除每个上游应用重复手写的样板代码。

## ADDED Requirements

### Requirement: 全局异常处理器可选装配

本能力 SHALL 提供全局异常处理器（`@RestControllerAdvice`）的自动装配：默认 MUST 关闭，仅当上游显式开启（`zone.middleware.web.exception-advice.enabled=true`）时装配；装配后 SHALL 将 `ZoneBusinessException` 转换为携带原错误码与消息的失败返回体，将 `BindException`/`MethodArgumentNotValidException` 转换为 `BIZ-4001 PARAM_INVALID` 失败返回体（message 含字段级校验信息），将未捕获的 `Exception` 兜底转换为 `SYS-9999 INTERNAL_ERROR` 失败返回体（详情仅记录服务端日志，不向调用方泄露内部细节）；该处理器 MUST 以最低优先级运行，上游自定义 advice 存在时 MUST 让位上游。

#### Scenario: 默认关闭零行为

- **WHEN** 上游未开启 exception-advice 开关
- **THEN** 不装配异常处理器，未捕获异常行为与不引入本能力完全一致

#### Scenario: 业务异常转统一返回体

- **WHEN** 开启后 Controller 抛出 `ZoneBusinessException`（BIZ-4004 数据不存在）
- **THEN** 响应为携带 `BIZ-4004` 与原消息的失败 `ZoneResult`

#### Scenario: 参数校验异常含字段信息

- **WHEN** 开启后请求体校验失败（`MethodArgumentNotValidException`）
- **THEN** 响应为 `BIZ-4001` 失败返回体，message 包含具体字段校验信息

#### Scenario: 兜底异常不泄露内部细节

- **WHEN** 开启后 Controller 抛出非业务异常
- **THEN** 响应为 `SYS-9999` 失败返回体（通用提示），异常堆栈仅出现在服务端日志

#### Scenario: 让位上游自定义 advice

- **WHEN** 上游已自定义 `@RestControllerAdvice` 处理同一异常类型且开启本能力
- **THEN** 优先命中上游 advice，本能力处理器不接管

### Requirement: Trace 入站 Filter 可选装配

本能力 SHALL 提供 Trace 入站 Filter 的自动装配：默认 MUST 关闭，仅当上游显式开启（`zone.middleware.web.trace-filter.enabled=true`）时注册；开启后 SHALL 在请求入口绑定链路标识——请求头携带 `Zone-Trace-Id` 时以其为 traceId（透传串联上下游），缺失时生成新 traceId——并生成 reqId，请求处理完成后 MUST 清理链路上下文（防止线程复用串号）。

#### Scenario: 默认关闭零行为

- **WHEN** 上游未开启 trace-filter 开关
- **THEN** 不注册 Filter，链路标识绑定行为与不引入本能力完全一致

#### Scenario: 透传上游 traceId

- **WHEN** 开启后请求携带 `Zone-Trace-Id` 头
- **THEN** 服务端日志与响应侧链路标识使用该 traceId，reqId 为本次请求新生成

#### Scenario: 无头时生成新 traceId

- **WHEN** 开启后请求未携带 `Zone-Trace-Id` 头
- **THEN** 生成新 traceId 并绑定，后续日志可按其串联

#### Scenario: 请求结束清理上下文

- **WHEN** 请求处理完成（含异常）后线程被复用处理下一请求
- **THEN** 上一请求的 traceId/reqId 已被清理，新请求日志不受残留污染
