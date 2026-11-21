# Design

## Context

log 模块现状（基线规格 log-format 已归档）：`ZoneLog` 静态门面 + `ZoneLogSettings` 不可变快照（appName/level/includeTraceId/includeAppName）+ `ZoneTraceContext`（MDC 单键 traceId）+ 双装配链（EPP 环境阶段早期应用 + `SmartInitializingSingleton` 幂等重放）。输出为管道分隔 TEXT 行，time/level 由底层 logback pattern 输出。本模块不依赖 zone-middleware-common。动机见 proposal.md。

## Goals / Non-Goals

**Goals:**
- 链路上下文升级为 traceId + reqId 双标识（MDC 承载），API 显式传入
- env 进入设置快照与输出（配置回退链）
- TEXT 行扩段（环境/reqId），缺席段跳过
- JSON 行模式：每行一个完整 JSON 对象，字段固定齐备，为未来轻量平台的零解析采集做准备

**Non-Goals:**
- 不实现采集/传输/存储/查询（平台本体另立 change）
- 不定义采集 SPI 或 sink 挂点
- 不提供 id 生成器（traceId/reqId 均由调用方显式传入）
- 不引入 AOP、不引 zone-middleware-common 依赖

## Decisions

### D1：reqId 仅线程上下文承载，调用方显式传入（用户决策）
无生成器、无协议头常量。串联职责归 traceId（出站传播头 `Zone-Trace-Id` 已由 common 的 `ZoneConstants.HEADER_TRACE_ID` 承担，未来 web/auth 二方包引用）。备选「包内提供生成器（复用 ZoneRandomUtils）」被否：生成策略属接入方决策（客户端 reqId 还要参与幂等/签名），库内生成会造成两套来源。

### D2：MDC 双键 + 一次性 clear
`ZoneTraceContext` 新增 `MDC_KEY_REQ_ID = "reqId"`，`setReqId/getReqId`；`clear()` 同时移除双键；新增 `bind(traceId, reqId)` 组合入口供未来 filter 一行接入。备选「reqId 独立类」被否：双标识同生命周期同线程绑定，单类内聚最低成本。

### D3：TEXT 缺席段跳过（**BREAKING**：对文本解析方）
段组成规则统一为「开关开启且值非空」。现有行为 traceId 未设置时产生空段（`a |  | Class.m`），本次一并规约为跳过——否则 reqId 大概率缺席（可选上下文），空段会频繁出现。JSON 模式才是平台解析路径，TEXT 保留人类可读优先。

### D4：JSON 模式自产 time/level
现状 time/level 由 pattern 输出，JSON 模式下必须由 `ZoneLog` 产出到 JSON 字段内（`System.currentTimeMillis()` + `ZoneLogLevel` 名称），否则 JSON 被 pattern 前缀污染、不可解析。JSON 模式与底层 pattern 的配合由 README 指引（建议 pattern 仅保留消息体与换行），库不强管 pattern。备选「包装 logback Encoder」被否：引入对具体日志实现的编译依赖，违反本库门面最小依赖原则。

### D5：JSON 渲染用 fastjson2 + 固定字段序
`LinkedHashMap` 按 time→level→appName→env→traceId→reqId→class→method→message→exception 顺序装配后 `JSON.toJSONString`，保证字段序稳定（diff 友好）。exception 字段 = 完整堆栈字符串（`printStackTrace` 落 StringWriter）；未携带异常为 null（占位，不缺席）。渲染兜底：任何序列化异常回退 `String.valueOf`，日志输出永不因渲染失败中断。

### D6：env 回退链取 spring.profiles.active 首段
`zone.middleware.log.env` → `spring.profiles.active`（逗号分隔取首段，"dev,local" 取 dev）→ `unknown`。EPP 与 AutoConfiguration 两处同链实现（复用现有幂等重放结构）。备选「原样透传多 profile 串」被否：污染平台按 env 筛选的基数。

### D7：format 为新枚举 `ZoneLogFormat`（TEXT/JSON）
与 `ZoneLogLevel` 同机制：枚举绑定，非法值启动失败（Binder 抛出）。`apply()` 签名从 4 参扩为 6 参（appName, level, includeTraceId, includeAppName, env, format）——库为 SNAPSHOT，直接改签名不保留重载。

## Risks / Trade-offs

- [TEXT 段序变化破坏存量文本解析] → 标注 BREAKING；库未正式发布（SNAPSHOT），README 变更说明明示新段序
- [JSON 模式与上游 pattern 叠加产生非纯 JSON 行] → README 配置指引 + 启动日志提示当前 format；库约束「ZoneLog 产出体必为合法单行 JSON」，pattern 前缀属上游配置责任
- [MDC 线程绑定不跨线程池] → 已知限制（现状即如此），README 注意事项声明；异步传播留待未来 web/auth 二方包的装饰器方案
- [堆栈字符串进 JSON 字段体积膨胀] → 平台侧截断策略属采集层职责；本库不截断（保真优先）

## Migration Plan

纯库增量，上游无配置即获得新 TEXT 段序（BREAKING 提示）+ 默认 TEXT 行为除段序外不变；需要 JSON 采集的上游显式配 `format: JSON`。回滚 = 退回上一 SNAPSHOT 版本。README 同步配置总表与示例。

## Open Questions

（无——三个方向性决策已由 owner 拍板：reqId 显式传入 / TEXT+JSON 双支持 / reqId 不进协议头）
