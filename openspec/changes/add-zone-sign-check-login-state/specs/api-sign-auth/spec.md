# Spec Delta

## Purpose

完善接口签名鉴权能力的注解接入面:`@ZoneSignCheck` 属性化,支持按端点覆盖请求时间窗与声明强制登录,使签名校验与登录态校验在同一校验链内以统一语义完成。

## MODIFIED Requirements

### Requirement: 注解声明接入

SHALL 提供 `@ZoneSignCheck` 注解声明需签名校验的端点:该注解 MUST 同时支持标注在 Controller 类与方法上,方法级声明优先于类级(经合并注解查找,支持业务以 `@ZoneSignCheck` 为元注解的组合注解);仅被注解覆盖的端点执行校验链,未被注解覆盖的端点 MUST 不受拦截。注解 MUST 提供两个带缺省值的属性:`timestampWindowMillis`(long,毫秒,0 为缺省,表示继承全局时间窗配置)与 `requireLogin`(boolean,缺省 false);缺省属性下端点行为 MUST 与无属性标注完全一致。

#### Scenario: 方法级注解生效

- **WHEN** 某接口方法标注 `@ZoneSignCheck` 而其所在类未标注
- **THEN** 该方法按校验链执行签名校验,同类未标注的其他方法不受拦截

#### Scenario: 类级注解生效

- **WHEN** 某 Controller 类标注 `@ZoneSignCheck` 而方法未单独标注
- **THEN** 该类全部接口方法均执行签名校验

#### Scenario: 方法级优先

- **WHEN** 类与方法同时存在不同标注且方法标注 `@ZoneSignCheck`
- **THEN** 以方法级声明为准,该方法执行签名校验

#### Scenario: 组合注解生效

- **WHEN** 业务自定义注解以 `@ZoneSignCheck` 为元注解,并将该自定义注解标注在某接口方法上
- **THEN** 该方法视同标注 `@ZoneSignCheck`,按校验链执行

#### Scenario: 缺省属性零行为变化

- **WHEN** 存量端点仅以无属性形态标注 `@ZoneSignCheck`
- **THEN** 校验行为与属性化之前完全一致:时间窗取全局配置,不执行登录态校验

### Requirement: 时间窗防重放

服务端 SHALL 校验请求 timestamp(Epoch 毫秒)与服务器当前时间的时间差:超出该端点有效时间窗的请求 MUST 拒绝;timestamp 缺失或非数字 MUST 拒绝。「有效时间窗」SHALL 定义为:注解 `timestampWindowMillis` 属性为正值时以其毫秒值为准;为缺省值 0 时取全局配置(默认 5 分钟)。注解窗口值为负时属配置错误,该端点 MUST 拒绝请求(fail-closed)并记录服务端错误日志。

#### Scenario: 窗口内通过

- **WHEN** 请求 timestamp 距服务器当前时间 3 分钟
- **THEN** 时间窗校验通过

#### Scenario: 窗口外拒绝

- **WHEN** 请求 timestamp 距服务器当前时间 10 分钟
- **THEN** 服务端拒绝请求,返回鉴权失败结果

#### Scenario: 时间窗可配置

- **WHEN** 上游将时间窗配置为 60 秒后,以距当前时间 2 分钟的 timestamp 发起请求
- **THEN** 服务端拒绝该请求

#### Scenario: 注解窗口覆盖全局

- **WHEN** 某端点注解声明 `timestampWindowMillis = 10000`(10 秒),请求以距当前时间 1 分钟(在全局 5 分钟窗口内)的 timestamp 发起
- **THEN** 服务端拒绝该请求(以注解窗口为准)

#### Scenario: 注解窗口继承全局

- **WHEN** 某端点注解未声明 `timestampWindowMillis`(缺省 0)
- **THEN** 该端点有效时间窗等于全局配置值

#### Scenario: 注解窗口负值拒绝

- **WHEN** 某端点注解声明 `timestampWindowMillis = -1`(负值,配置错误)后发起签名合法的请求
- **THEN** 服务端拒绝该请求,并记录服务端错误日志

### Requirement: reqId nonce 防重放

服务端 SHALL 以 reqId 作为 nonce 防重放:同一 reqId 在有效时间窗时长内重复出现的请求 MUST 拒绝;reqId 缺失或为空 MUST 拒绝。nonce 记录 MUST 以该端点有效时间窗时长为生存期自动过期。登录态校验(requireLogin=true)失败发生在 nonce 消费之前,此类请求 MUST 不消耗对应 reqId 的 nonce。存储不可用(Redis 不可达)时,标注端点 MUST 拒绝请求而非放行(fail-closed)。

#### Scenario: 首次请求通过

- **WHEN** 某合法请求的 reqId 在时间窗内首次出现
- **THEN** 防重放校验通过

#### Scenario: 重放拒绝

- **WHEN** 同一请求(相同 reqId 与签名)在时间窗内被原样重放
- **THEN** 服务端拒绝重放请求,返回鉴权失败结果

#### Scenario: nonce 过期释放

- **WHEN** 距该 reqId 首次出现超过时间窗时长后再次携带同一 reqId 请求
- **THEN** 该 reqId 可重新通过防重放校验

#### Scenario: nonce 生存期跟随注解窗口

- **WHEN** 某端点注解声明 `timestampWindowMillis = 10000`(10 秒)且请求通过签名校验
- **THEN** 该 reqId 的 nonce 记录生存期为 10 秒(而非全局配置值)

#### Scenario: 登录失败不消耗 nonce

- **WHEN** 端点声明 `requireLogin = true`,请求签名正确但未携带登录令牌,随后以同 reqId 补齐令牌重试
- **THEN** 首次请求被拒绝,重试请求防重放校验通过(reqId 未被首次失败请求占用)

#### Scenario: 存储不可用拒绝

- **WHEN** nonce 存储不可达时发起签名校验请求
- **THEN** 服务端拒绝请求,返回鉴权失败结果
