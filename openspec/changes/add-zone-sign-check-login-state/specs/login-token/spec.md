# Spec Delta

## Purpose

扩展登录令牌能力的消费面:登录态校验由注解声明进入服务端校验链,校验通过后向业务层提供无类型侵入的用户信息上下文取用方式。

## ADDED Requirements

### Requirement: 注解强制登录与用户上下文

SHALL 支持经 `@ZoneSignCheck(requireLogin = true)` 声明端点强制登录:该类端点在签名校验通过后、防重放校验执行前,MUST 校验登录态——从请求头 `Zone-Auth-Token` 取令牌并解析,令牌缺失、无效、过期或已撤销时 MUST 拒绝请求并返回与签名失败一致的统一鉴权失败结果(响应不区分具体环节)。校验通过时 MUST 将登录上下文(令牌串、userId、用户信息原始 JSON 串)写入当前请求的 request attribute,并提供静态门面取用:按业务类型解析用户信息、取 userId 与令牌串;非 Web 线程或上下文不存在时门面 MUST 返回 null 而不抛出异常。userId SHALL 取用户信息 JSON 的 `userId` 字段,字段缺失时 MUST 不阻断请求(userId 透传 null)。

#### Scenario: 强制登录通过并取用用户信息

- **WHEN** 端点声明 `requireLogin = true`,请求签名正确且请求头 `Zone-Auth-Token` 携带合法令牌
- **THEN** 请求进入业务处理,业务代码经静态门面可取到 userId 并能按业务类型解析出签发时保存的用户信息

#### Scenario: 缺失登录令牌拒绝

- **WHEN** 端点声明 `requireLogin = true`,请求签名正确但未携带 `Zone-Auth-Token` 请求头
- **THEN** 服务端拒绝请求,返回统一鉴权失败结果,不进入业务处理

#### Scenario: 无效登录令牌拒绝

- **WHEN** 端点声明 `requireLogin = true`,请求头携带的令牌已被撤销或不存在
- **THEN** 服务端拒绝请求,返回统一鉴权失败结果,不进入业务处理

#### Scenario: 滑动续期语义保持

- **WHEN** 端点声明 `requireLogin = true` 且开启滑动续期,携带合法令牌的请求通过登录态校验
- **THEN** 该令牌生存期重置为完整 TTL(与 API 解析路径一致)

#### Scenario: 上下文缺失返回 null

- **WHEN** 业务代码在未声明 `requireLogin` 的端点或非 Web 线程中调用静态门面
- **THEN** 返回 null,不抛出异常

#### Scenario: userId 字段缺失不阻断

- **WHEN** 签发令牌时保存的用户信息 JSON 不含 `userId` 字段,请求通过强制登录校验
- **THEN** 请求正常进入业务处理,门面取 userId 返回 null,用户信息类型解析不受影响
