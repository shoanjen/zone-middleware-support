# Proposal

## Why

security 模块首期 change(`add-zone-middleware-security`)design.md 决策 9 明确将「需登录态的注解式拦截」预留为后续独立 change:当前 `@ZoneSignCheck` 是无属性的标记注解,时间窗只有全局配置 `zone.middleware.security.sign.timestamp-window`(默认 5 分钟),无法按端点差异化(如敏感接口收紧到 60 秒);强制登录只能靠业务代码手动调 `ZoneTokenService` API,校验时机与失败语义各应用自定,口径不一。本次将注解属性化,补齐两条能力:注解级请求时间窗、注解级强制登录(鉴权时读取并解析 `Zone-Auth-Token` 登录态,经静态门面向业务暴露用户信息)。

## What Changes

- `@ZoneSignCheck` 注解新增两个属性:
  - `timestampWindowMillis`(long,毫秒,默认 0):本端点请求时间窗,0(缺省)继承全局配置,正值覆盖,负值 fail-closed;nonce TTL 跟随本端点有效时间窗。
  - `requireLogin`(boolean,默认 false):强制登录校验——签名比对通过后、nonce 消费前,读取请求头 `Zone-Auth-Token` 并经 `ZoneTokenService` 解析登录态;失败统一 401(响应契约不变)。
- 新增用户上下文:`ZoneUserContext`(token/userId/原始 JSON + 按业务类型惰性解析)存入 request attribute;`ZoneSecurityContext` 静态门面基于 Spring `RequestContextHolder` 取用(不引入自研 ThreadLocal)。
- `ZoneTokenService` 新增 `parseTokenRaw(token)`:返回用户信息原始 JSON 串(含滑动续期语义),供拦截器在不知道业务用户类型的前提下建立上下文;既有 `parseToken(token, Class)` 重构为委托实现,行为不变。
- 拦截器注解解析升级为 `AnnotatedElementUtils.findMergedAnnotation`(支持组合注解),按 `HandlerMethod` 缓存;自动装配同步传递 `ZoneTokenService`。
- 兼容性:全部属性带缺省值,既有 `@ZoneSignCheck` 标注端点行为完全不变(requireLogin=false、窗口继承全局)。

## Capabilities

### Modified Capabilities

- `api-sign-auth`: 注解声明接入——注解属性化(时间窗覆盖 + 强制登录开关);时间窗防重放——引入「有效时间窗」概念(全局配置或注解覆盖),nonce 生存期跟随有效时间窗,登录态校验失败不消耗 reqId nonce。
- `login-token`: 新增「注解强制登录与用户上下文」requirement——requireLogin=true 的端点执行登录态校验链,通过后注入 ZoneUserContext 并可经 ZoneSecurityContext 门面取用;失败统一 401。

## Impact

- **代码**:security 模块内闭环——`annotation/ZoneSignCheck`(属性)、新增 `context/` 包(`ZoneUserContext`/`ZoneSecurityContext`)、`token/ZoneTokenService`(parseTokenRaw)、`web/ZoneSignCheckInterceptor`(窗口计算 + 登录分支)、`config/ZoneSecurityAutoConfiguration`(bean 入参);common/redis/log 模块零改动,无新增依赖。
- **配置**:无新增配置键(全部为注解级属性,全局 `timestamp-window` 语义不变、仍作为缺省值)。
- **上游应用**:纯增强,存量 `@ZoneSignCheck` 端点行为不变;需强制登录的端点补 `requireLogin = true` 并在业务代码经 `ZoneSecurityContext` 取用户信息。
- **文档/登记**:docs/接入手册.md §9 新增注解属性小节并修订「注解拦截为后续迭代」表述;README 模块能力表与包结构同步;子项目 AGENTS.md 速览同步。
