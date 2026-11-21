# Design

## Context

见 proposal.md「Why」。落地约束:Spring Boot 3.5.6 BOM(servlet 栈,jakarta 命名空间);JSON 处理统一 fastjson2(总纲约定,不用 jackson);工作区共享 Redis 8.8.0(`zone-middleware-redis` 已提供 `ZoneRedisTemplate`);`zone-middleware-common` 已有 `BaseRequest`(reqId/platform/deviceId/timestamp 等)、`ZoneConstants.HEADER_ACCESS_TOKEN = "Zone-Access-Token"`(注释明确为鉴权二方包预留)、`Platform` 枚举(web/pc/mobile/pad)、`ZoneRandomUtils`(空连接符生成不分段随机串);`zone-middleware-http` 已留出站签名 SPI `ZoneAuthInterceptor`。各模块自动装配遵循既有模式:`config/ZoneXxxAutoConfiguration`(标注 `@AutoConfiguration`) + `ZoneXxxProperties` + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。

## Goals / Non-Goals

**Goals:**

- 签名与校验「同一套规范、两侧一致」:拼串/序列化/摘要单一实现收口,客户端工具与拦截器共用。
- 注解式接入,零侵入:不标注的端点行为完全不变。
- 校验 fail-closed:任何环节失败或依赖不可用均拒绝。
- 与既有设施复用:BaseRequest 字段、Platform 枚举、ZoneRedisTemplate、ZoneResult/ZoneError 契约,不另起炉灶。

**Non-Goals:**

- 「需登录态」的注解式拦截(本期仅提供 `ZoneTokenService` API,注解拦截留待后续独立 change)。
- 出站方向自动签名(实现 http 模块 `ZoneAuthInterceptor` SPI),独立后续 change。
- 摘要算法可插拔配置(MD5 已定案;仅以常量收口,为将来换 HMAC-SHA256 留单点)。
- refresh token、分布式会话管理界面。

## Decisions

1. **签名拼串:`TreeMap` 升序 + 空值剔除 + `&key=<secret>` 结尾 + MD5 小写 hex**——与微信支付 v2 同款,业界共识最不易做错。备选:纯拼接不掺 secret(拒绝:可伪造)、HMAC-SHA256(用户已定 MD5;不做成配置项,避免双算法并存的选择负担)。
2. **嵌套值序列化:fastjson2 默认序列化(不启用 MapSortField 等排序特性)**。理由:请求 body 解析为 `JSONObject`(内部 LinkedHashMap)后重新序列化保持插入序;前端 JS `JSON.stringify` 对字符串键同样保持插入序——双端「插入序」天然一致。若启用服务端排序,前端反而无法低成本复现。POJO 按字段声明序,同一规则。
3. **body 可重读:`OncePerRequestFilter` 全局包装自研 `ZoneCachedBodyRequestWrapper`**(惰性缓存:首次读取透传并缓存,后续 `getInputStream()` 回放缓存)。`ContentCachingRequestWrapper` 只做观测缓存、二次读流返回 -1,不满足「拦截器读后 Controller 再读」的需求故弃用。惰性设计保证:未标注端点读放行为与原始请求完全一致(规格「不受拦截」),超限(1MB)只停缓存置溢出标记、透传不截断,标注端点由拦截器据溢出标记拒绝;另在拦截器先做 Content-Length 预检快速失败。
4. **校验链落点:`HandlerInterceptor.preHandle` + 注解探测(method 优先,其次 class)**;签名比对用 `MessageDigest.isEqual` 常量时间比较;签名比对先于 nonce 消费——垃圾签名请求不占用合法 reqId 的 nonce,防 reqId 抢占 DoS。不引入 Spring Security 过滤器链(拒绝:引入依赖违背自研轻量目标)。
5. **平台分流:platform 值域白名单(web/pc/mobile/pad,经 `Platform.of` + 原始值双校验),未知/空拒绝;deviceId 校验仅客户端策略执行**。secret 单一配置项,web 与客户端共用。
6. **令牌存储 key 设计(经 `StringRedisTemplate` 直连)**——redis 门面 `ZoneRedisTemplate` 缺 `setIfAbsent`(nonce)与 `expire`(滑动续期)原语,为不扩大 change 范围进 redis 模块,security 组件直接注入门面底座 `StringRedisTemplate`(zone-redis 自动装配已暴露该 Bean,仍遵守 zone-redis 的连接配置):
   - `zone:security:token:<token>` → 用户信息 JSON 串(fastjson2,任意 userInfo 类型),TTL = token.ttl;
   - `zone:security:user-token:<userId>` → Set(token 集合),供踢下线与单端互斥枚举;
   - 滑动续期 = 校验通过时对 token key 重置 expire;Set 成员不做独立 TTL,撤销时惰性 `srem` 清理,避免双 key 过期漂移(解析路径无 userId 上下文,不清理)。
   - 备选:扩展 `ZoneRedisTemplate` 门面补原语(拒绝:扩大 change 范围、需 redis 能力规格同步 delta);仅前缀 scan 查用户令牌(拒绝:scan 阻塞 Redis)。
7. **Redis 为必选依赖(fail-closed)**:nonce 与令牌均强依赖,不设降级开关——静默降级等于静默关闭防重放/会话,风险大于可用性。自动装配 `@ConditionalOnClass(ZoneRedisTemplate)` 且 `@AutoConfiguration(after = ZoneRedisAutoConfiguration)`。
8. **失败响应:HTTP 401 + `ZoneResult` JSON body(统一「鉴权失败」,不区分环节)**;错误码复用既有 `BusinessError.UNAUTHORIZED`(BIZ-4002)——common-error 规格规定 SYS-/BIZ- 两码段互不重叠的契约,security 模块自起新码段会破坏它;响应不区分失败原因(安全惯例,不向调用方泄露校验细节),审计原因仅记服务端日志。
9. **注解只管签名、令牌走 API**:`@ZoneSignCheck` 仅表达「此端点需签名校验」;登录态判断由上层在业务代码中调用 `ZoneTokenService` 完成——两类安全语义分词分组件,避免一个注解含义过载。
10. **配置段 `zone.middleware.security.*`**:`sign.enabled`(默认 true;纯令牌场景显式关闭后不装配拦截器与过滤器,避免强制配 secret)、`sign.secret`(启用签名校验时必填,装配期快速失败)、`sign.timestamp-window`(默认 5m,`Duration`)、`token.ttl`(默认 7d,`Duration`)、`token.renew-sliding`(默认 true)、`token.multi-device`(默认 true=并存,false=单端互斥踢旧)。

## Risks / Trade-offs

- [MD5 抗碰撞已被攻破] → 本方案依赖的是掺 secret 后的抗原像性,业界(微信支付 v2)长期实践可行;摘要算法常量收口,升级 HMAC-SHA256 只动一处。
- [web 端 secret 随前端代码暴露,防伪造强度有限] → 方案固有属性(已确认接受);时间窗 + nonce 使重放与篡改成本可控,关键操作建议上层叠加业务级防护(短信/二次确认)。
- [嵌套 JSON 双端序列化不一致(如 Java 端误用 HashMap 导致乱序)] → 客户端工具统一收口序列化出口;接入文档明确「Map 用 LinkedHashMap 或 POJO」;新增集成测试覆盖「工具签名 → 拦截器校验」往返。
- [多节点服务器时钟偏移导致误拒] → 默认 5 分钟窗口容忍小偏移;运维要求 NTP 对时(接入文档注明)。
- [Redis 故障时所有标注端点不可用(fail-closed 代价)] → 使用工作区高可用共享实例;故障影响面仅为标注了 `@ZoneSignCheck` 的端点,未标注端点不受影响。
- [大 body 缓存内存压力] → 缓存上限 1MB,超限拒绝并在响应中明确错误码。
- [`user-token` Set 与 token key 过期漂移] → 惰性清理(解析失败 `srem`)+ 踢下线时逐个删除;不追求强一致,泄漏代价仅为 Set 中残留失效 member。

## Migration Plan

- 新增子模块,无存量行为变更;唯一迁移点:存量把签名写入 `BaseRequest.sign` 的调用方迁移至请求头 `Zone-Access-Token`(`BaseRequest.sign` 标 `@Deprecated` 后编译期可见告警)。
- 回滚:上游移除 `@ZoneSignCheck` 注解或卸载依赖即恢复原状;模块对未标注端点零侵入。

## Open Questions

无——算法拼串、header 语义、分流规则、令牌策略均已在探索阶段与用户确认。
