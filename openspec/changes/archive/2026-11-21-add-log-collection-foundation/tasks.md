# Tasks

## 1. 链路上下文：traceId + reqId 双标识

- [x] 1.1 `ZoneTraceContext` 新增 reqId 承载：`MDC_KEY_REQ_ID`、`setReqId`/`getReqId`、`bind(traceId, reqId)` 组合入口，`clear()` 一次性清理双标识；不提供任何 id 生成器。验证：ZoneTraceContext 单测新增「reqId 显式设置后获取一致 / 未设置返回 null / bind 双值生效 / clear 后双标识均为 null」断言通过
- [x] 1.2 ZoneLog 携带 reqId 段前置改造：`format()` 读取 reqId。验证：`ZoneLog.info` 在设置 reqId 后输出行含该值（与 3.1 段序断言合并验证）

## 2. 设置与配置面：env + format

- [x] 2.1 新增 `ZoneLogFormat` 枚举（TEXT/JSON）；`ZoneLogProperties` 增加 `env`、`format` 字段（非法 format 经 Binder 枚举绑定启动失败）；`ZoneLogSettings` 扩展 `env`/`format` 字段，`apply(...)` 签名扩为 6 参（SNAPSHOT 直接改签名）。验证：属性测试「非法 format 启动失败且提示合法取值」「默认 TEXT」通过
- [x] 2.2 env 回退链：`zone.middleware.log.env` → `spring.profiles.active` 首段 → `unknown`，在 `ZoneLogInitEnvironmentPostProcessor` 与 `ZoneLogAutoConfiguration` 两处一致实现，启动日志输出当前 env 与 format。验证：测试「显式 env 生效 / 回退 profiles 首段 / 双回退 unknown / EPP 与 AutoConfiguration 应用结果一致」通过

## 3. 输出渲染：TEXT 扩段 + JSON 行模式

- [x] 3.1 TEXT 行组装规约落地：段=「开关开启且值非空」，缺席段跳过（替代原空段占位），固定顺序 应用名|环境|traceId|reqId|类名.方法名|消息。验证：ZoneLogTest 更新并通过——完整段序、关闭 traceId 开关、traceId/reqId 缺席无空占位、环境段始终存在（含 unknown 兜底）
- [x] 3.2 JSON 行模式：`JSON.toJSONString(LinkedHashMap)` 固定字段序 time/level/appName/env/traceId/reqId/class/method/message/exception；time=毫秒时间戳与 level=级别名由 ZoneLog 自产；标识缺席 null 占位；exception=完整堆栈字符串（无异常为 null）；序列化异常回退 `String.valueOf`。验证：JSON 模式输出逐行 `JSON.parse` 断言字段齐备、null 占位、异常携带（含类名与消息）、非法字符不破坏单行合法性

## 4. 全局验证与文档

- [x] 4.1 全量 `mvn clean install` 通过（全部模块测试绿），`mvn -pl zone-middleware-log -Ddoclint=all javadoc:javadoc` 零 warning。验证：两条命令退出码 0
- [x] 4.2 README 同步：模块能力表、`zone.middleware.log.*` 配置总表（新增 env/format 两键）、TEXT 新段序 BREAKING 说明（缺席段跳过）、JSON 模式使用示例与底层 pattern 简化指引、包结构树补 `ZoneLogFormat`。验证：文档中配置键、段序、示例与实现逐项一致

## Workflow follow-up

- 实现不在本 propose 流程内：由用户显式发起 `/openspec-apply-change add-log-collection-foundation` 后实施
- 实现完成、任务全勾后：`openspec validate --specs` + 归档（`/openspec-archive-change`），log-format 规格增量合入 `openspec/specs/log-format/spec.md`
