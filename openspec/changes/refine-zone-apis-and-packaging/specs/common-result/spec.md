# Spec Delta

## MODIFIED Requirements

### Requirement: 统一返回体结构

`ZoneResult` SHALL 提供 `success`（布尔）、`code`（字符串）、`message`（字符串）、`data`（泛型）、`timestamp`（毫秒时间戳）五个字段；`success` 与 `code` 的语义 MUST 保持一致：成功时 `success=true` 且 `code` 为成功码 `200`，失败时 `success=false` 且 `code` 为失败异常码（取自错误对象）。

#### Scenario: 成功构造

- **WHEN** 调用成功工厂方法并传入数据
- **THEN** 返回对象 `success=true`、`code` 为 `200`、`message` 为成功描述、`data` 为传入数据、`timestamp` 为当前时间戳

#### Scenario: 失败构造

- **WHEN** 调用失败工厂方法并传入一个错误对象
- **THEN** 返回对象 `success=false`、`code`/`message` 分别取自该错误对象的错误码与错误描述

### Requirement: 静态工厂方法

`ZoneResult` SHALL 提供 `ok()`、`ok(data)`、`ok(data, message)`、`fail(ZoneError)`、`fail(ZoneError, message)` 静态工厂；`ok` 的 `message` 参数 MUST 覆盖默认成功描述，`fail` 的第二个参数 MUST 覆盖错误对象的默认描述作为 `message`。

#### Scenario: 自定义成功消息

- **WHEN** 以数据和自定义消息调用 `ok`
- **THEN** 返回对象 `success=true`、`code` 为 `200`、`message` 为自定义消息

#### Scenario: 覆盖失败消息

- **WHEN** 以错误对象和自定义消息调用 `fail`
- **THEN** 返回对象 `code` 取自错误对象、`message` 为自定义消息

#### Scenario: 无数据成功

- **WHEN** 调用 `ok()` 不传数据
- **THEN** 返回对象 `success=true` 且 `data` 为 null
