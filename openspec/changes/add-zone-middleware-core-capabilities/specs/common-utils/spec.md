# Spec Delta

## Purpose

提供上游应用高频使用的通用工具能力：客户端 IP 解析、浏览器指纹信息提取与标识生成、可分段的随机字符串生成。

## ADDED Requirements

### Requirement: 客户端 IP 解析

`ZoneIpUtils` SHALL 按主流代理头顺序（X-Forwarded-For、X-Real-IP、Proxy-Client-IP、WL-Proxy-Client-IP 等）解析客户端真实 IP：取第一个非空且非 `unknown` 的值（多级代理取第一段并去除空白）；所有代理头均无有效值时 MUST 回退到 `remoteAddr`。

#### Scenario: 多级代理链

- **WHEN** 请求头 `X-Forwarded-For` 为 `"203.0.113.5, 10.0.0.1"`
- **THEN** 解析结果为 `203.0.113.5`

#### Scenario: 无代理头

- **WHEN** 请求不含任何代理头且 `remoteAddr` 为 `192.168.1.10`
- **THEN** 解析结果为 `192.168.1.10`

#### Scenario: 跳过无效值

- **WHEN** `X-Forwarded-For` 为 `"unknown"`，`X-Real-IP` 为 `198.51.100.7`
- **THEN** 解析结果为 `198.51.100.7`

### Requirement: 浏览器指纹信息提取

`ZoneBrowserUtils` SHALL 基于开源 User-Agent 解析能力从请求中提取浏览器名称、浏览器版本、操作系统、设备类型等指纹信息，并以结构化对象返回。

#### Scenario: 解析常见浏览器

- **WHEN** 以 Chrome 浏览器的 User-Agent 字符串调用解析方法
- **THEN** 返回对象的浏览器名称为 Chrome、浏览器版本为主版本号、操作系统与设备类型非空

#### Scenario: 空 UA 容错

- **WHEN** 以空或 null 的 User-Agent 调用解析方法
- **THEN** 返回对象的各字段为 `Unknown`/`unknown` 占位值而不抛出异常

### Requirement: 浏览器指纹标识

`ZoneBrowserUtils` SHALL 基于请求头集合（至少 User-Agent、Accept、Accept-Language）生成稳定的指纹标识：同一请求头集合 MUST 生成相同标识，不同集合 SHOULD 生成不同标识。

#### Scenario: 同头同指纹

- **WHEN** 以完全相同的请求头集合生成两次指纹标识
- **THEN** 两次结果完全一致

#### Scenario: 变头变指纹

- **WHEN** User-Agent 不同、其余头相同时生成指纹标识
- **THEN** 结果与原标识不同

### Requirement: 分段随机字符串生成

`ZoneRandomUtils` SHALL 支持指定总长度、连接符与分段长度生成分段随机字符串：每段为固定分段长度的字母数字随机串，段间以连接符连接；连接符为空时 MUST 生成不分段的等长随机串。

#### Scenario: 指定连接符与分段长度

- **WHEN** 以总长度 23、连接符 `-`、分段长度 5 生成随机字符串
- **THEN** 结果总长度为 23，由字母数字组成，段与段之间恰好以 `-` 连接且每段不超过 5 个字符

#### Scenario: 无连接符

- **WHEN** 以总长度 16、空连接符生成随机字符串
- **THEN** 结果为 16 位不含连接符的字母数字串

#### Scenario: 非法参数

- **WHEN** 总长度或分段长度为 0 或负数
- **THEN** 抛出 `IllegalArgumentException`

### Requirement: 安全随机源

随机字符串生成 MUST 使用密码学安全的随机源（`SecureRandom` 或等价实现），默认字符集为大小写字母与数字。

#### Scenario: 字符集约束

- **WHEN** 生成任意随机字符串
- **THEN** 每个字符都在 `[a-zA-Z0-9]` 范围内
