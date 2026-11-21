# http-client Specification

## Purpose

让上游应用通过统一模板发起 HTTP GET/POST 请求，内置连接复用与超时控制，支持自定义鉴权、失败重试与熔断保护。

## Requirements

### Requirement: 客户端配置

`ZoneHttpTemplate` 的底层客户端 SHALL 提供默认配置（连接超时、读超时、重定向策略、并发上限），全部参数 MUST 可由上游应用通过 `zone.middleware.http.*` 自定义覆盖。

#### Scenario: 默认配置可用

- **WHEN** 上游不做任何配置直接使用模板发起 GET 请求
- **THEN** 请求按默认超时与重定向策略成功返回

#### Scenario: 自定义超时生效

- **WHEN** 上游配置自定义读超时后发起会长时间无响应的请求
- **THEN** 请求在配置的超时值附近失败而非默认值

### Requirement: 通用 GET 请求

SHALL 提供通用 GET 方法：接受 URL、可选请求头，响应体 MUST 按 fastjson2 反序列化为调用方指定的目标类型（含 `ZoneResult` 泛型）。

#### Scenario: GET 反序列化

- **WHEN** 服务端返回 JSON 对象并以对应类型发起 GET
- **THEN** 返回目标类型的实例且字段值一致

### Requirement: 通用 POST 请求

SHALL 提供通用 POST 方法：请求体由 fastjson2 序列化为 JSON 发送（Content-Type 为 application/json），响应按目标类型反序列化。

#### Scenario: POST JSON 往返

- **WHEN** 以 POJO 请求体发起 POST 且服务端原样回显
- **THEN** 返回实例与请求体等值

### Requirement: 自定义鉴权

SHALL 提供鉴权扩展点：上游实现鉴权接口（对请求的统一处理，如注入认证头）并注册后，模板发起的每个请求 MUST 经过该鉴权处理。

#### Scenario: 注入认证头

- **WHEN** 注册了添加 `Authorization` 头的鉴权实现后发起 GET
- **THEN** 服务端收到的请求携带该认证头

### Requirement: 失败重试

SHALL 支持可配置重试：最大尝试次数、退避初始值与倍数、可重试状态码集合；网络异常与可重试状态码触发重试，默认关闭重试，重试耗尽后 MUST 抛出携带系统错误码的业务异常。

#### Scenario: 状态码触发重试

- **WHEN** 配置重试后服务端前两次返回 503、第三次返回 200
- **THEN** 调用方最终获得成功结果且请求共发起三次

#### Scenario: 重试耗尽抛错

- **WHEN** 配置最大尝试次数为 2 且服务端持续返回 503
- **THEN** 抛出 `ZoneBusinessException`，其错误码为 HTTP 相关系统错误码

#### Scenario: 默认不重试

- **WHEN** 未开启重试且服务端返回 503
- **THEN** 仅发起一次请求即抛出异常

### Requirement: 熔断保护

SHALL 支持可配置熔断：连续失败达到阈值后进入 OPEN 状态拒绝请求（快速失败并抛出携带系统错误码的异常），经过冷却时间进入 HALF_OPEN 放行探测请求，探测成功恢复 CLOSED、失败重回 OPEN；默认关闭熔断。

#### Scenario: 达到阈值熔断

- **WHEN** 开启熔断且连续失败次数达到阈值后再发起请求
- **THEN** 请求不再到达服务端，直接快速失败

#### Scenario: 冷却后探测恢复

- **WHEN** 熔断 OPEN 且冷却时间已过后发起请求且服务端恢复正常
- **THEN** 请求被放行并成功，熔断状态恢复为 CLOSED

#### Scenario: 默认不熔断

- **WHEN** 未开启熔断且持续失败
- **THEN** 每次请求仍真实发出，不出现快速失败

### Requirement: 非成功状态码错误语义

响应为非 2xx 状态码且不在重试范围（或重试耗尽）时，模板 MUST 抛出 `ZoneBusinessException`，错误码为 HTTP 系统错误码且消息包含状态码。

#### Scenario: 4xx 抛错

- **WHEN** 服务端返回 404
- **THEN** 抛出异常且消息中包含 404

### Requirement: 连接复用与并发上限

底层客户端 MUST 复用 HTTP 连接（keep-alive）并支持并发请求数上限配置；达到上限的并发请求 MUST 等待而非立即失败。

#### Scenario: 并发不超限均成功

- **WHEN** 以并发上限 4 同时发起 6 个请求且服务端均正常响应
- **THEN** 6 个请求全部成功，任意时刻在途请求不超过上限
