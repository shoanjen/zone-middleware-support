# Design

## Context

见 proposal.md「Why」。首期 change(`add-zone-middleware-security`)已落地:校验链拦截器(`ZoneSignCheckInterceptor`,校验顺序为 签名头→参数提取→reqId→时间窗→platform 分流→签名比对→nonce,失败统一 401 + `ZoneResult`)、登录令牌服务(`ZoneTokenService`,opaque token + Redis,签发/解析/滑动续期/撤销)、`Zone-Auth-Token` 请求头取值(`ZoneAuthTokenResolver`)。首期 design.md 决策 9「注解只管签名、令牌走 API」将注解式登录拦截预留为独立 change,即本次。

## Goals / Non-Goals

**Goals:**

- 注解级时间窗:敏感端点可收紧窗口(如 60s),且 nonce TTL 与有效窗口保持一致,不出现「窗口收紧但防重放 TTL 仍是全局值」的脱节。
- 注解级强制登录:签名与登录态在同一校验链内完成,失败语义与签名失败完全一致(统一 401,不区分环节)。
- 用户信息无类型侵入暴露:中间件不感知业务用户类型,业务侧按需解析。
- 纯增强零破坏:属性带缺省值,存量标注端点与未标注端点行为完全不变。

**Non-Goals:**

- 方法参数解析器(`HandlerMethodArgumentResolver`)直接注入用户对象——静态门面已覆盖取用诉求,参数注入留待有明确诉求再加。
- 「登录可选」模式(有 token 则解析、无 token 放行)——业务可在 requireLogin=false 端点自行调用 API 实现,不预设语义。
- 出站签名、摘要算法可插拔等首期已列 Non-Goal 的事项,维持不变。

## Decisions

1. **注解属性形态:`long timestampWindowMillis() default 0` + `boolean requireLogin() default false`**。时间窗用数值毫秒而非字符串/`Duration`——与协议 `timestamp` 字段同为 Epoch 毫秒精度,单位自洽且编译期即可发现书写错误,不引入字符串解析失败的运行期暗坑(用户定案:注解时间窗以毫秒表达,不用字符串);注解属性不支持 null 缺省,0 即「继承全局」的自然哨兵,正值覆盖,负值属配置错误——fail-closed 拒绝该端点请求并记 error 日志(与「secret 未配置全拒绝」同级处理,配置错误不能变成放行)。
2. **登录校验插入点:签名比对之后、nonce 消费之前**。延续首期决策 4 的「先比签名再消费 nonce」思路——无效令牌的请求(即使签名正确)不得消耗合法 reqId 的 nonce,防 reqId 抢占;同时登录失败与签名失败共用同一 401 响应契约(首期决策 8 不变,错误码仍复用 `BusinessError.UNAUTHORIZED`/BIZ-4002)。签名比对前置意味着登录态探测需先通过共享 secret 签名,抬高无差别探测成本。
3. **用户上下文:`ZoneUserContext` request attribute + `ZoneSecurityContext` 静态门面**。token 存储侧本来就是任意类型 userInfo JSON 串(fastjson2),拦截器经新增 `parseTokenRaw(token)` 拿原始串建立 `ZoneUserContext`(token、userId、rawJson、`as(Class)` 惰性解析)写入 request attribute;`ZoneSecurityContext` 经 Spring `RequestContextHolder`→`ServletRequestAttributes` 取用——复用 Spring 自管理的请求线程上下文,不引入自研 ThreadLocal(无异步泄漏与清理负担),非 Web 线程或未标注端点返回 null。userId 取用户信息 JSON 的 `userId` 字段(与 `ZoneTokenService` 撤销路径的既有约定一致),缺失不阻断仅透传 null。
4. **`parseTokenRaw(String)` 收口解析原语**:返回用户信息原始 JSON 串,含滑动续期语义(与 `parseToken` 一致);`parseToken(token, Class)` 重构为「parseTokenRaw + fastjson2 反序列化」,两条入口行为完全一致。拦截器只依赖 parseTokenRaw,避免对业务类型编译期依赖。
5. **注解解析:`AnnotatedElementUtils.findMergedAnnotation` + `ConcurrentHashMap<HandlerMethod, ZoneSignCheck>` 缓存**。方法级优先、其次类级(语义与首期一致),merged 查找额外支持业务自定义组合注解(如 `@PaySignCheck` 元标注 `@ZoneSignCheck`);Spring 对每个路由的 HandlerMethod 单例缓存,以其为 key 的注解缓存有界且稳定。
6. **nonce TTL 跟随有效时间窗**:「有效时间窗」定义为 注解 `timestampWindowMillis` 为正值时的毫秒值,缺省 0 时取全局 `zone.middleware.security.sign.timestamp-window`;`checkTimestampWindow` 与 `checkNonce` 共用该值,时间窗校验与防重放 TTL 严格同源。
7. **自动装配无新增键**:拦截器 bean 方法增加 `ZoneTokenService` 入参(两者装配条件同为 `StringRedisTemplate` 存在,bean 缺失即启动失败,符合 fail-fast);`ZoneConstants` 不动,request attribute 名常量定义在 `ZoneUserContext` 上(安全域常量不进 common)。

## Risks / Trade-offs

- [注解窗口为负值(配置错误)] → fail-closed 拒绝并 error 日志,错误在接入自测期即暴露;文档注明合法形态(正值毫秒,0 继承全局)。
- [requireLogin 端点每请求多一次 Redis 读(token 解析)] → 与 nonce 写同量级;滑动续期在该读路径顺带完成,无额外往返。
- [`ZoneSecurityContext` 在异步 Servlet(async controller)中可能取不到] → 门面对 null 语义透明(返回 null),文档注明异步场景限制;后续如有诉求再补上下文传播。
- [注解缓存以 HandlerMethod 为 key 的内存占用] → 上界为路由数量,量级可控,无淘汰必要。

## Migration Plan

- 纯增强:存量 `@ZoneSignCheck` 端点缺省属性下行为与首期完全一致(requireLogin=false、窗口继承全局)。
- 需强制登录的端点:补 `requireLogin = true`,登录签发沿用 `ZoneTokenService.issueToken`(用户信息 JSON MUST 含 `userId` 字段以支撑上下文 userId 取值,缺失仅 userId 为 null 不阻断),业务代码经 `ZoneSecurityContext` 取用。
- 回滚:移除注解属性即回退首期行为;`context/` 包无外部依赖可整体摘除。

## Open Questions

无——注解属性形态、校验链插入点、上下文传递方式已经用户确认(静态门面方案)。
