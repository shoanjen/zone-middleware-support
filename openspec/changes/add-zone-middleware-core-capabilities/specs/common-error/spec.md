# Spec Delta

## Purpose

建立统一的错误码体系，将错误区分为系统错误与通用业务数据错误，为异常处理与错误上报提供稳定契约。

## ADDED Requirements

### Requirement: 顶层错误接口

`ZoneError` SHALL 作为顶层错误接口，定义 `getCode()` 与 `getMessage()` 两个方法；`SystemError`（系统错误）与 `BusinessError`（通用业务数据错误）SHALL 是它的两个子类型实现。

#### Scenario: 系统错误实现

- **WHEN** 取任一 `SystemError` 枚举项
- **THEN** 其 `getCode()` 返回系统错误码、`getMessage()` 返回错误描述，且可赋值给 `ZoneError`

#### Scenario: 业务错误实现

- **WHEN** 取任一 `BusinessError` 枚举项
- **THEN** 其 `getCode()` 返回业务错误码、`getMessage()` 返回错误描述，且可赋值给 `ZoneError`

### Requirement: 错误码段区分

`SystemError` 与 `BusinessError` 的错误码 MUST 使用不同前缀码段（`SYS-` 与 `BIZ-`），使调用方无需知道具体枚举即可区分错误类别。

#### Scenario: 按前缀区分错误类别

- **WHEN** 遍历两个枚举的全部错误码
- **THEN** `SystemError` 全部以 `SYS-` 开头，`BusinessError` 全部以 `BIZ-` 开头，两码段互不重叠

### Requirement: 业务异常

`ZoneBusinessException` SHALL 携带一个 `ZoneError` 并可通过 `getError()` 取回；构造时可覆盖默认描述，`getMessage()` MUST 返回覆盖后的描述（未覆盖时返回错误对象默认描述）。

#### Scenario: 抛出并捕获

- **WHEN** 以 `SystemError` 构造并抛出 `ZoneBusinessException` 后捕获
- **THEN** `getError()` 返回原 `ZoneError`，`getMessage()` 返回该错误默认描述

#### Scenario: 覆盖异常消息

- **WHEN** 以 `BusinessError` 与自定义消息构造 `ZoneBusinessException`
- **THEN** `getError().getCode()` 为该业务错误码，`getMessage()` 为自定义消息
