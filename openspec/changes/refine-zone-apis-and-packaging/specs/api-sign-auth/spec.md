# Spec Delta

## ADDED Requirements

### Requirement: 签名比对调试日志

拦截器在签名比对环节 SHALL 以 `ZoneLog` debug 级别输出签名计算三要素：传入的 `Zone-Access-Token` 值、服务端参与计算签名的请求参数、服务端计算出的签名值；因包含凭证形态与业务参数，MUST 采用 debug 级别（默认 INFO 门槛下不输出，仅排障时开启）。

#### Scenario: 签名比对输出三要素

- **WHEN** 以 debug 级别门槛开启后发起一次携带签名头（含错误签名）的请求
- **THEN** 服务端日志输出包含请求头传入的 token 值、参与参数与服务端计算签名
