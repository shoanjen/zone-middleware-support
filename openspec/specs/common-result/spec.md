# common-result Specification

## Purpose

为所有引入本库的 Zone 上游应用提供统一的服务返回体结构，使调用方可以按固定契约解析任何 Zone 服务的响应。

## Requirements

### Requirement: 统一返回体结构

`ZoneResult` SHALL 提供 `success`（布尔）、`code`（字符串）、`message`（字符串）、`data`（泛型）、`timestamp`（毫秒时间戳）五个字段；`success` 与 `code` 的语义 MUST 保持一致：成功时 `success=true` 且 `code` 为成功码，失败时 `success=false` 且 `code` 为错误码。

#### Scenario: 成功构造

- **WHEN** 调用成功工厂方法并传入数据
- **THEN** 返回对象 `success=true`、`code` 为成功码、`message` 为成功描述、`data` 为传入数据、`timestamp` 为当前时间戳

#### Scenario: 失败构造

- **WHEN** 调用失败工厂方法并传入一个错误对象
- **THEN** 返回对象 `success=false`、`code`/`message` 分别取自该错误对象的错误码与错误描述

### Requirement: 静态工厂方法

`ZoneResult` SHALL 提供 `ok()`、`ok(data)`、`fail(ZoneError)`、`fail(ZoneError, message)` 静态工厂；`fail` 的第二个参数 MUST 覆盖错误对象的默认描述作为 `message`。

#### Scenario: 覆盖失败消息

- **WHEN** 以错误对象和自定义消息调用 `fail`
- **THEN** 返回对象 `code` 取自错误对象、`message` 为自定义消息

#### Scenario: 无数据成功

- **WHEN** 调用 `ok()` 不传数据
- **THEN** 返回对象 `success=true` 且 `data` 为 null

### Requirement: fastjson2 序列化

`ZoneResult` 的 JSON 序列化与反序列化 MUST 基于 fastjson2 完成，字段名与结构在序列化往返中保持稳定，`data` 可按指定目标类型还原。

#### Scenario: 序列化往返

- **WHEN** 将携带 POJO 数据的 `ZoneResult` 序列化为 JSON 字符串再反序列化为 `ZoneResult<T>`
- **THEN** `success`、`code`、`message`、`timestamp` 与 `data` 内容与原对象一致
