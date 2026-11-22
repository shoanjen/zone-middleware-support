# Spec Delta

## Purpose

BaseRequest 的 sign 字段在代码中已 `@Deprecated`（签名校验由 `@ZoneSignCheck` + `Zone-Access-Token` 请求头承担），本增量将规格表述同步为废弃兼容保留，明确新代码不得以其承载签名、下版本移除。

## MODIFIED Requirements

### Requirement: 基础请求字段

`BaseRequest` SHALL 提供八个字段：reqId（请求唯一标识）、platform（平台原始字符串）、ip（客户端 IP）、deviceId（设备标识）、os（操作系统原始字符串）、version（接口版本号）、timestamp（请求时间戳，Epoch 毫秒）、sign（**已废弃，兼容保留**）；其中 sign MUST 标注废弃——新代码 MUST NOT 以其承载签名（签名校验由 `@ZoneSignCheck` 注解与 `Zone-Access-Token` 请求头承担），本字段仅为兼容存量报文保留，MUST 在后续版本移除；本类 MUST 可被上游请求 DTO 继承使用。

#### Scenario: 字段读写完整

- **WHEN** 对 `BaseRequest` 的八个字段逐一赋值后读取
- **THEN** 每个字段均返回所赋的值

#### Scenario: 子类继承往返

- **WHEN** 上游定义继承 `BaseRequest` 的请求 DTO 并做 fastjson2 序列化再反序列化
- **THEN** 基类八个字段与子类自有字段均完整保留

#### Scenario: sign 字段废弃语义

- **WHEN** 存量报文仍携带 sign 字段
- **THEN** 反序列化兼容保留该字段；新代码调用签名校验走 `Zone-Access-Token` 请求头，不依赖 sign
