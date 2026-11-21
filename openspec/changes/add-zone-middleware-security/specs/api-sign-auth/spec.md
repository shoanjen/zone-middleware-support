# Spec Delta

## Purpose

为 Zone 生态提供自研的接口签名鉴权能力:调用方按约定算法对请求参数计算 MD5 签名并经请求头携带,服务端以注解声明的方式对标注端点执行校验链(签名比对、时间窗、防重放、设备校验),覆盖 web 与客户端(pc/mobile/pad)两类接入策略,不依赖任何开源鉴权组件。

## ADDED Requirements

### Requirement: 签名串构造规范

接口签名 SHALL 按以下唯一规范构造待签名串:取请求顶层全部参与参数——BaseRequest 四字段(reqId、timestamp、platform、deviceId)与业务参数(POST 为 body 顶层 key-value,GET 为 query 参数 key-value);剔除值为 null 或空串的参数;按参数名 TreeMap 升序排列;以 `k1=v1&k2=v2` 形式连接;末尾追加 `&key=<secret>`;对该串计算 MD5 得到 32 位小写十六进制签名。嵌套对象或数组类型的值 MUST 以 fastjson2 序列化后的 JSON 字符串作为该参数的值参与拼串。

#### Scenario: 升序拼串

- **WHEN** 参与参数为 b=2、a=1、c=3 且均有值
- **THEN** 待签名串为 `a=1&b=2&c=3&key=<secret>`,签名等于该串的 MD5 32 位小写十六进制值

#### Scenario: 空值剔除

- **WHEN** 参与参数中某字段值为 null 或空串
- **THEN** 该字段不出现在待签名串中,其余字段正常参与

#### Scenario: 复杂值序列化

- **WHEN** 某业务参数为嵌套对象 `{"orderNo":"A1","items":[1,2]}`
- **THEN** 该参数以 fastjson2 序列化后的 JSON 字符串作为值参与拼串,其参数名正常参与升序排序;序列化结果确定且可重现(同输入同输出)

#### Scenario: 调用方与服务端一致

- **WHEN** 调用方按本规范对同一组参数与 secret 计算签名,服务端按同一规范重建待签名串
- **THEN** 两侧计算出的签名完全一致

### Requirement: 签名请求头承载

接口签名 MUST 经请求头 `Zone-Access-Token` 携带;服务端 SHALL 且仅 SHALL 以该请求头作为签名校验的唯一来源。缺少该请求头或其值为空的请求,对标注端点 MUST 拒绝。

#### Scenario: 有效签名通过

- **WHEN** 请求头 `Zone-Access-Token` 携带按规范计算的正确签名
- **THEN** 签名校验通过,请求进入业务处理

#### Scenario: 缺失请求头拒绝

- **WHEN** 请求未携带 `Zone-Access-Token` 请求头或其值为空
- **THEN** 服务端拒绝请求,返回鉴权失败结果,不进入业务处理

### Requirement: 注解声明接入

SHALL 提供 `@ZoneSignCheck` 注解声明需签名校验的端点:该注解 MUST 同时支持标注在 Controller 类与方法上,方法级声明优先于类级;仅被注解覆盖的端点执行校验链,未被注解覆盖的端点 MUST 不受拦截。

#### Scenario: 方法级注解生效

- **WHEN** 某接口方法标注 `@ZoneSignCheck` 而其所在类未标注
- **THEN** 该方法按校验链执行签名校验,同类未标注的其他方法不受拦截

#### Scenario: 类级注解生效

- **WHEN** 某 Controller 类标注 `@ZoneSignCheck` 而方法未单独标注
- **THEN** 该类全部接口方法均执行签名校验

#### Scenario: 方法级优先

- **WHEN** 类与方法同时存在不同标注且方法标注 `@ZoneSignCheck`
- **THEN** 以方法级声明为准,该方法执行签名校验

### Requirement: 平台策略分流

服务端 SHALL 按请求的 platform 字段分流校验策略:platform 为 `web` 时执行 web 策略,deviceId 不要求提供;platform 为 `pc`、`mobile`、`pad` 时执行客户端策略,deviceId MUST 非空且仅含字母数字下划线连字符;platform 为协议四值之外、空或缺失的请求 MUST 拒绝。web 与客户端策略 MUST 共用同一个 secret。

#### Scenario: web 策略无 deviceId

- **WHEN** platform 为 `web` 且请求不携带 deviceId
- **THEN** 设备校验通过(其余校验链正常执行)

#### Scenario: 客户端策略缺 deviceId 拒绝

- **WHEN** platform 为 `mobile` 且 deviceId 为空或缺失
- **THEN** 服务端拒绝请求,返回鉴权失败结果

#### Scenario: 客户端 deviceId 非法字符拒绝

- **WHEN** platform 为 `pc` 且 deviceId 为 `dev@ice!`
- **THEN** 服务端拒绝请求,返回鉴权失败结果

#### Scenario: 未知平台拒绝

- **WHEN** platform 为 `watch`、空串或缺失
- **THEN** 服务端拒绝请求,返回鉴权失败结果

### Requirement: 时间窗防重放

服务端 SHALL 校验请求 timestamp(Epoch 毫秒)与服务器当前时间的时间差:超出可配置时间窗(默认 5 分钟)的请求 MUST 拒绝;timestamp 缺失或非数字 MUST 拒绝。

#### Scenario: 窗口内通过

- **WHEN** 请求 timestamp 距服务器当前时间 3 分钟
- **THEN** 时间窗校验通过

#### Scenario: 窗口外拒绝

- **WHEN** 请求 timestamp 距服务器当前时间 10 分钟
- **THEN** 服务端拒绝请求,返回鉴权失败结果

#### Scenario: 时间窗可配置

- **WHEN** 上游将时间窗配置为 60 秒后,以距当前时间 2 分钟的 timestamp 发起请求
- **THEN** 服务端拒绝该请求

### Requirement: reqId nonce 防重放

服务端 SHALL 以 reqId 作为 nonce 防重放:同一 reqId 在时间窗时长内重复出现的请求 MUST 拒绝;reqId 缺失或为空 MUST 拒绝。nonce 记录 MUST 以时间窗时长为生存期自动过期。存储不可用(Redis 不可达)时,标注端点 MUST 拒绝请求而非放行(fail-closed)。

#### Scenario: 首次请求通过

- **WHEN** 某合法请求的 reqId 在时间窗内首次出现
- **THEN** 防重放校验通过

#### Scenario: 重放拒绝

- **WHEN** 同一请求(相同 reqId 与签名)在时间窗内被原样重放
- **THEN** 服务端拒绝重放请求,返回鉴权失败结果

#### Scenario: nonce 过期释放

- **WHEN** 距该 reqId 首次出现超过时间窗时长后再次携带同一 reqId 请求
- **THEN** 该 reqId 可重新通过防重放校验

#### Scenario: 存储不可用拒绝

- **WHEN** nonce 存储不可达时发起签名校验请求
- **THEN** 服务端拒绝请求,返回鉴权失败结果

### Requirement: 校验失败统一拒绝

校验链任一环节(签名比对、平台策略、时间窗、nonce、请求头缺失)失败时,服务端 MUST 拒绝请求并返回统一的鉴权失败结果,不得进入业务处理;签名比对 MUST 使用常量时间比较以防时序侧信道。

#### Scenario: 签名不符拒绝

- **WHEN** 请求头携带的签名与服务端重建计算的签名不一致
- **THEN** 服务端拒绝请求,返回统一的鉴权失败结果

#### Scenario: 篡改参数拒绝

- **WHEN** 请求参数在签名计算后被篡改(如金额字段改值)
- **THEN** 服务端重建签名与请求头签名不一致,拒绝请求

### Requirement: 签名客户端工具

SHALL 提供与上述规范完全一致的客户端签名工具:按参数集合与 secret 计算签名的方法,以及生成 reqId 的方法——reqId MUST 为 32 位字母数字随机串且不含连接符。服务端校验与客户端工具 MUST 共用同一套拼串与摘要实现,保证两侧结果一致。

#### Scenario: reqId 生成格式

- **WHEN** 调用 reqId 生成方法
- **THEN** 返回 32 位字母数字随机串,不含 `-` 等连接符,两次生成结果不同

#### Scenario: 工具签名与校验一致

- **WHEN** 以客户端工具对某参数集合计算签名,并按服务端同规范重建校验
- **THEN** 校验通过
