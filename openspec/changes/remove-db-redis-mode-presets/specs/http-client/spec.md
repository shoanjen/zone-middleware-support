# Spec Delta

## Purpose

http 客户端配置键面与 Spring Boot 官方 `spring.http.client.*` 对齐：连接超时/读超时/重定向策略改由官方键承载（未配置时代码兜底默认），并发上限与重试无官方对应、保留 zone 键。

## MODIFIED Requirements

### Requirement: 客户端配置

`ZoneHttpTemplate` 底层客户端的连接超时、读超时与重定向策略 SHALL 由 Spring Boot 官方键 `spring.http.client.connect-timeout` / `read-timeout` / `redirects` 承载；未配置时 MUST 兜底默认值（连接超时 5s、读超时 10s、重定向 follow-when-possible）。并发上限 SHALL 由 `zone.middleware.http.max-concurrent-requests` 承载，重试策略 SHALL 由 `zone.middleware.http.retry.*` 承载（均无官方对应键）。

#### Scenario: 默认配置可用

- **WHEN** 上游不做任何配置直接使用模板发起 GET 请求
- **THEN** 请求按兜底默认超时（5s/10s）与 follow-when-possible 重定向策略成功返回

#### Scenario: 自定义超时生效

- **WHEN** 上游通过 `spring.http.client.read-timeout` 配置自定义读超时后发起会长时间无响应的请求
- **THEN** 请求在配置的超时值附近失败而非兜底默认值

#### Scenario: 重定向策略官方键生效

- **WHEN** 上游配置 `spring.http.client.redirects: never`
- **THEN** 底层 HttpClient 不跟随重定向，302 响应原样返回
