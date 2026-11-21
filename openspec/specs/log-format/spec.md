# log-format Specification

## Purpose

让引入本库的应用以统一格式输出日志，级别与字段在 Zone 生态内一致，且无需 AOP 切面即可完成埋点。

## Requirements


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

`ZoneLog` 输出 SHALL 支持两种行格式：TEXT（默认）与 JSON。TEXT 模式下（时间与级别仍由底层日志框架输出）每条日志行 SHALL 由「开关开启且值非空」的段按固定顺序组成：应用名、环境、traceId、reqId、类名.方法名、消息（占位符已填充）；应用名与 traceId 段 MUST 可通过配置开关裁剪；开关开启但值缺席、或无开关但值缺席的段 MUST 跳过且不产生空段占位。

#### Scenario: 完整格式

- **WHEN** 默认配置且 traceId、reqId 均已设置时输出一条 info 日志
- **THEN** 日志行中应用名、环境、traceId、reqId、类名.方法名、消息依次出现且顺序固定

#### Scenario: 关闭 traceId 输出

- **WHEN** 配置关闭 traceId 后输出日志
- **THEN** 日志行不包含 traceId 段，其余字段保持

#### Scenario: 缺席段跳过

- **WHEN** traceId 与 reqId 均未设置时输出日志
- **THEN** 日志行不含这两段，其余段保持固定顺序且无空占位

#### Scenario: 环境段始终存在

- **WHEN** 以任意配置输出日志
- **THEN** 日志行始终包含环境段（未配置环境时为 unknown）

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

本模块 SHALL 提供链路双标识的线程级上下文工具：traceId（上下游串联标识）与 reqId（单请求标识），两者 SHALL 支持设置、获取与清理，清理 MUST 同时作用于双标识；设置后同线程内所有 `ZoneLog` 输出 MUST 携带对应标识。reqId MUST 由调用方显式传入，本模块 MUST NOT 提供 id 生成器；本模块 MUST NOT 为 reqId 定义协议头传播常量（跨服务串联职责归 traceId，其出站传播头约定由 common 能力提供）。

#### Scenario: 同线程携带

- **WHEN** 设置 traceId 后在同线程输出日志
- **THEN** 日志行携带该 traceId

#### Scenario: 清理后不携带

- **WHEN** 清理上下文后输出日志
- **THEN** 日志行 traceId 段为空占位或不存在

#### Scenario: reqId 显式设置后携带

- **WHEN** 调用方显式设置 reqId 后在同线程输出日志
- **THEN** 日志行携带该 reqId

#### Scenario: reqId 未设置

- **WHEN** 未设置 reqId 时输出日志
- **THEN** TEXT 行无 reqId 段，JSON 行 reqId 字段为 null

#### Scenario: 一次性清理双标识

- **WHEN** 调用清理后再次获取两个标识
- **THEN** traceId 与 reqId 均不可获取

#### Scenario: reqId 无协议头定义

- **WHEN** 检查本模块公开 API、配置项与常量面
- **THEN** 不存在 reqId 的协议头传播定义

### Requirement: 环境字段

上游应用 SHALL 可通过 `zone.middleware.log.env` 配置环境标识；未配置时 MUST 回退 `spring.profiles.active`，仍未命中时 MUST 回退 `unknown`。环境标识 SHALL 进入运行时设置快照并随每条日志输出（TEXT 环境段 / JSON env 字段）。

#### Scenario: 显式配置生效

- **WHEN** 配置 `env: prod` 后输出日志
- **THEN** 日志携带环境标识 prod

#### Scenario: 回退应用配置

- **WHEN** 未配置 env 但 `spring.profiles.active` 为 dev
- **THEN** 日志携带环境标识 dev

#### Scenario: 双回退占位

- **WHEN** env 与 `spring.profiles.active` 均未配置
- **THEN** 日志携带环境标识 unknown

### Requirement: JSON 行模式

上游应用 SHALL 可通过 `zone.middleware.log.format` 选择行格式，取值 `TEXT`（默认）或 `JSON`，非法取值 MUST 导致启动失败。JSON 模式下 `ZoneLog` 每次输出 SHALL 产出一个完整合法的单行 JSON 对象，字段固定为 time（Epoch 毫秒）、level（级别名）、appName、env、traceId、reqId、class、method、message、exception；time 与 level MUST 由本能力自产；未设置的标识字段 MUST 以 null 占位而非缺席；携带异常时 exception 字段 MUST 非空且包含异常类名与消息。

#### Scenario: JSON 行合法可解析

- **WHEN** JSON 模式下输出一条 info 日志
- **THEN** 该行可被 JSON 解析为包含全部固定字段的对象

#### Scenario: 标识字段 null 占位

- **WHEN** traceId 与 reqId 均未设置时以 JSON 模式输出
- **THEN** 解析结果中 traceId 与 reqId 字段存在且为 null

#### Scenario: 携带异常

- **WHEN** JSON 模式下以显式 Throwable 输出 error
- **THEN** exception 字段非空且包含该异常的类名与消息

#### Scenario: 非法格式值启动失败

- **WHEN** 配置 format 为不识别的值后启动
- **THEN** 应用启动失败，错误信息包含非法值与合法取值提示
