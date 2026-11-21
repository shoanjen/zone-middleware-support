# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
