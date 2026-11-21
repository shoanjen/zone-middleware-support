# Spec Delta

## Purpose

让引入本库的应用以统一格式输出日志，级别与字段在 Zone 生态内一致，且无需 AOP 切面即可完成埋点。

## ADDED Requirements

### Requirement: 统一日志门面

`ZoneLog` SHALL 提供静态日志方法，入参按「日志级别、类名、方法名、消息、可变参数」顺序构建；消息 MUST 支持 `{}` 占位符并由可变参数按出现顺序填充。`debug`/`info`/`warn`/`error` 各提供变参方法；`error` 与通用 `log` 另提供显式携带 `Throwable` 的重载。

#### Scenario: 四级输出

- **WHEN** 分别以 debug/info/warn/error 四个级别调用 `ZoneLog`
- **THEN** 每条日志以对应级别写入，消息内容完整

#### Scenario: 显式级别

- **WHEN** 调用 `log` 传入 WARN 级别与占位符消息
- **THEN** 日志以 WARN 级别输出且占位符已填充

### Requirement: 占位符渲染

消息中的 `{}` SHALL 由可变参数按出现顺序填充：字符串参数原样输出（JSON 字符串直接透传）；数值、布尔与字符按字面值输出；其他对象以 fastjson2 JSON 输出；null 输出 `null`；多于占位符的参数 MUST 被忽略。

#### Scenario: 占位符按序填充

- **WHEN** 以消息 `订单{}创建成功,金额{}` 与参数（订单号, 金额）调用
- **THEN** 消息按顺序渲染为包含两个参数值的完整文本

#### Scenario: JSON 字符串透传

- **WHEN** 以 JSON 字符串作为占位符参数
- **THEN** 该 JSON 文本原样出现在消息中

#### Scenario: 对象参数 JSON 渲染

- **WHEN** 以 Map 或 POJO 作为占位符参数
- **THEN** 该参数以 fastjson2 JSON 文本渲染进消息

#### Scenario: 多余参数忽略

- **WHEN** 可变参数个数多于消息中的占位符个数
- **THEN** 未被消费的参数不出现在日志中且不报错

### Requirement: 末位异常自动识别

变参末位为 `Throwable` 且未被占位符消费时 SHALL 作为异常堆栈输出而非渲染进消息；显式 `Throwable` 重载 MUST 无条件输出堆栈且不影响变参填充占位符。

#### Scenario: 末位异常输出堆栈

- **WHEN** 以消息 `订单{}保存失败` 与参数（订单号, 异常）调用 error
- **THEN** 消息渲染为含订单号的文本，且日志附带该异常堆栈

#### Scenario: 显式异常重载

- **WHEN** 调用携带显式 `Throwable` 与变参的 error 重载
- **THEN** 日志附带异常堆栈且变参正常填充占位符

### Requirement: 统一输出格式

（时间与级别由底层日志框架输出）`ZoneLog` 输出的每条日志行 SHALL 包含且按以下顺序排列：应用名、traceId、类名.方法名、消息（占位符已填充）；应用名与 traceId 的包含 MUST 可通过配置开关。

#### Scenario: 完整格式

- **WHEN** 以默认配置输出一条 info 日志
- **THEN** 日志行中应用名、traceId、类名.方法名、消息依次出现且顺序固定

#### Scenario: 关闭 traceId 输出

- **WHEN** 配置关闭 traceId 后输出日志
- **THEN** 日志行不包含 traceId 段，其余字段保持

### Requirement: 级别门槛控制

上游应用 SHALL 通过 `zone.middleware.log.level` 配置最低输出级别（默认 INFO）；低于门槛的日志方法调用 MUST 不产生任何输出。

#### Scenario: 默认门槛拦截 debug

- **WHEN** 默认配置下调用 debug 级别输出
- **THEN** 不产生日志输出

#### Scenario: 调低门槛

- **WHEN** 配置 `level: DEBUG` 后调用 debug 输出
- **THEN** debug 日志正常输出

### Requirement: 无 AOP 埋点

日志埋点 MUST 仅通过调用方显式调用 `ZoneLog` API 完成，本模块 MUST NOT 依赖 AOP 切面（不得引入 AOP 相关依赖）。

#### Scenario: 显式调用埋点

- **WHEN** 上游代码在方法内显式调用 `ZoneLog.info`
- **THEN** 该处产生日志，且无需任何切面配置或注解

#### Scenario: 无 AOP 依赖

- **WHEN** 解析本模块依赖树
- **THEN** 不存在 `spring-boot-starter-aop` 或 `aspectjweaver` 构件

### Requirement: traceId 上下文

本模块 SHALL 提供 traceId 的设置、获取与清理工具，基于线程级上下文承载；设置后同线程内所有 `ZoneLog` 输出 MUST 携带该 traceId。

#### Scenario: 同线程携带

- **WHEN** 设置 traceId 后在同线程输出日志
- **THEN** 日志行携带该 traceId

#### Scenario: 清理后不携带

- **WHEN** 清理 traceId 后输出日志
- **THEN** 日志行 traceId 段为空占位或不存在
