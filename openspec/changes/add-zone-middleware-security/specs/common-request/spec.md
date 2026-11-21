# Spec Delta

## MODIFIED Requirements

### Requirement: 基础请求字段

`BaseRequest` SHALL 提供八个字段:reqId(请求唯一标识)、platform(平台原始字符串)、ip(客户端 IP)、deviceId(设备标识)、os(操作系统原始字符串)、version(接口版本号)、timestamp(请求时间戳,Epoch 毫秒)、sign(接口签名,已废弃);本类 MUST 可被上游请求 DTO 继承使用。接口签名的承载位置变更为请求头 `Zone-Access-Token`:上游调用方 MUST 经该请求头携带签名,服务端 MUST NOT 以 `BaseRequest.sign` 字段作为签名校验来源;该字段仅为存量序列化兼容保留,新代码 MUST NOT 以其承载签名。

#### Scenario: 字段读写完整

- **WHEN** 对 `BaseRequest` 的八个字段逐一赋值后读取
- **THEN** 每个字段均返回所赋的值

#### Scenario: 子类继承往返

- **WHEN** 上游定义继承 `BaseRequest` 的请求 DTO 并做 fastjson2 序列化再反序列化
- **THEN** 基类八个字段与子类自有字段均完整保留

#### Scenario: 请求头承载签名不依赖字段

- **WHEN** 调用方 sign 字段为 null、仅经请求头 `Zone-Access-Token` 携带签名发起请求
- **THEN** 签名校验正常执行,请求进入业务处理

#### Scenario: 存量字段值不被校验消费

- **WHEN** 存量调用方仍在 `BaseRequest.sign` 字段中携带签名值(请求头缺失或与字段值不一致)
- **THEN** 服务端校验仅以请求头为准,字段值不参与校验
