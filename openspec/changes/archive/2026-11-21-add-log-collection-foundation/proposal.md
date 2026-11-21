# Proposal

## Why

轻量日志平台已在规划中（多应用采集、区分环境、按时间与埋点字段筛选），但当前 log 二方包只覆盖埋点输出：reqId 无处承载、环境字段不存在、输出为管道分隔纯文本——平台侧按字段筛选只能做脆弱的按序切分解析。平台本身暂不建设，但日志包必须先把"平台将来要筛的字段"生产出来，否则未来采集无源可采。

## What Changes

- **reqId 链路上下文**：`ZoneTraceContext` 在 traceId 之外增加 reqId 承载（线程上下文 set/get/clear），reqId 由调用方显式传入——本包不提供 id 生成器；reqId **MUST NOT** 定义协议头传播（各跳自治，串联职责归 traceId）
- **环境字段**：`ZoneLogSettings` 与配置增加 `env`（`zone.middleware.log.env`，回退 `spring.profiles.active`，再回退 `unknown`），进入设置快照与日志输出
- **BREAKING**（对文本行解析方）：TEXT 输出行扩段为 `应用名 | 环境 | traceId | reqId | 类名.方法名 | 消息`；段序固定，未配置/未设置的段按既有开关与缺席语义处理
- **JSON 行模式**：新增 `zone.middleware.log.format` 配置（`TEXT` 默认 / `JSON`）；JSON 模式每行输出一个完整 JSON 对象（time、level、appName、env、traceId、reqId、class、method、message、exception），time 与 level 改由 `ZoneLog` 自产（不再依赖底层 pattern 承载）
- 明确不做：采集 SPI/传输挂点（待平台传输方案定型后再议）、id 生成器、自定义业务 k-v 字段、AOP 埋点（维持既有规格禁令）

## Capabilities

### New Capabilities

（无——采集地基（结构化字段输出与链路上下文）仍属「统一格式输出日志」能力边界；采集/传输属未来平台，届时另立能力）

### Modified Capabilities

- `log-format`：输出行扩展 env/reqId 段与 JSON 行模式；traceId 上下文扩展为 traceId + reqId 双标识链路上下文

## Impact

- `zone-middleware-log`：`ZoneTraceContext`（新增 reqId API）、`ZoneLogSettings`（env 字段）、`ZoneLog`（行组装与 JSON 渲染）、`ZoneLogProperties`（env/format 配置）、`ZoneLogAutoConfiguration` 与 `ZoneLogInitEnvironmentPostProcessor`（设置应用链路）
- 新配置键：`zone.middleware.log.env`、`zone.middleware.log.format`
- 不新增任何第三方依赖；`ZoneConstants.HEADER_TRACE_ID`（common）保持为 traceId 出站传播常量的单一来源，本模块暂不引入 common 依赖（log 不做出站传播，无引用需求）
- README 模块能力、配置总表、包结构与示例同步更新
