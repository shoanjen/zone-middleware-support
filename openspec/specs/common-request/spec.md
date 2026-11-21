# common-request Specification

## Purpose

为上游应用提供统一的基础请求载体 BaseRequest，携带请求标识、平台、IP、设备、系统、版本、时间戳与签名字段，供接口层统一鉴权、审计与防重放；平台与操作系统枚举作为上层应用的解析工具。

## Requirements

### Requirement: 基础请求字段

`BaseRequest` SHALL 提供八个字段：reqId（请求唯一标识）、platform（平台原始字符串）、ip（客户端 IP）、deviceId（设备标识）、os（操作系统原始字符串）、version（接口版本号）、timestamp（请求时间戳，Epoch 毫秒）、sign（接口签名）；本类 MUST 可被上游请求 DTO 继承使用。

#### Scenario: 字段读写完整

- **WHEN** 对 `BaseRequest` 的八个字段逐一赋值后读取
- **THEN** 每个字段均返回所赋的值

#### Scenario: 子类继承往返

- **WHEN** 上游定义继承 `BaseRequest` 的请求 DTO 并做 fastjson2 序列化再反序列化
- **THEN** 基类八个字段与子类自有字段均完整保留

### Requirement: platform 与 os 字符串透传

platform 与 os 字段 MUST 以字符串承载并原样透传：序列化往返后未知值与大小写形态 MUST 保持不变；载体层 MUST NOT 做枚举强转、丢弃或规范化未知值。

#### Scenario: 未知值原样保留

- **WHEN** 以 platform 为 "Web"、os 为 "macOS" 的 JSON 反序列化为 `BaseRequest`
- **THEN** getPlatform 返回 "Web"、getOs 返回 "macOS"，原文不被修改

### Requirement: 平台枚举解析工具

`Platform` 枚举 SHALL 包含 WEB / PC / MOBILE / PAD 四个取值（协议标识 web / pc / mobile / pad），作为上层应用的解析工具：`of` 方法 MUST 支持大小写不敏感解析，null、空串或未知值返回 null 而不抛异常。

#### Scenario: 上层解析原始字符串

- **WHEN** 请求的 platform 为 "web"，上层调用 `Platform.of(request.getPlatform())`
- **THEN** 返回 WEB

#### Scenario: 未知值解析为 null

- **WHEN** 以 "watch" 或 null 调用 `of`
- **THEN** 返回 null（由上层对未知值自行决策）

### Requirement: 操作系统枚举解析工具

`OsType` 枚举 SHALL 包含 ANDROID / IOS / WINDOWS / HARMONY / LINUX 五个取值（协议标识 android / ios / windows / harmony / linux），作为上层应用的解析工具：`of` 方法 MUST 支持大小写不敏感解析，null、空串或未知值返回 null 而不抛异常。

#### Scenario: 上层解析原始字符串

- **WHEN** 请求的 os 为 "harmony"，上层调用 `OsType.of(request.getOs())`
- **THEN** 返回 HARMONY

#### Scenario: 未知值解析为 null

- **WHEN** 以 "macos" 或 null 调用 `of`
- **THEN** 返回 null
