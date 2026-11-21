# Tasks

## 1. 注解与用户上下文

- [x] 1.1 `@ZoneSignCheck` 属性化:新增 `timestampWindowMillis`(long 毫秒,默认 0=继承全局,负值 fail-closed)与 `requireLogin`(boolean,默认 false);javadoc 补属性说明、校验链顺序与使用示例;验证:编译通过,既有注解元信息断言单测无回归
- [x] 1.2 新增 `context/ZoneUserContext`:常量 `REQUEST_ATTRIBUTE`、字段 token/userId/rawJson、`as(Class)` 惰性解析(fastjson2);userId 取用户信息 JSON 的 `userId` 字段,缺失不阻断;验证:单测覆盖类型解析、userId 提取、缺失字段场景
- [x] 1.3 新增 `context/ZoneSecurityContext` 静态门面:`currentUser()`/`currentUser(Class)`/`currentUserId()`,经 `RequestContextHolder` 读 request attribute;非 Web 线程与上下文缺失返回 null 不抛异常;验证:单测覆盖有/无 `RequestContextHolder` 两种场景

## 2. 服务与拦截器、自动装配

- [x] 2.1 `ZoneTokenService` 新增 `parseTokenRaw(String)`:合法令牌返回用户信息原始 JSON 串(含滑动续期),无效返回 null;`parseToken(token, Class)` 重构为委托 parseTokenRaw,行为不变;验证:既有令牌集成测试无回归 + parseTokenRaw 集成测试(合法/撤销/过期/续期)
- [x] 2.2 `ZoneSignCheckInterceptor` 改造:注解解析升级 `findMergedAnnotation`(方法级优先→类级)并按 HandlerMethod 缓存;有效窗口计算(注解毫秒覆盖或继承全局,负值 fail-closed 拒绝)注入时间窗校验与 nonce TTL;requireLogin=true 时在签名比对后、nonce 消费前执行登录态校验(`ZoneAuthTokenResolver` → `parseTokenRaw`),通过后构建 `ZoneUserContext` 写入 request attribute;验证:单测覆盖窗口覆盖(方法级/类级/方法覆盖类级)、nonce TTL 跟随有效窗口、负值窗口拒绝、登录三分支(缺头/无效/通过且 attribute 写入)
- [x] 2.3 `ZoneSecurityAutoConfiguration` 拦截器 bean 方法增加 `ZoneTokenService` 入参;验证:自动装配上下文测试无回归

## 3. 端到端集成

- [x] 3.1 E2E:`requireLogin = true` 端点全链路——签发令牌后携 `Zone-Auth-Token` 签名请求通过且业务代码经 `ZoneSecurityContext` 取到 userId;缺令牌/无效令牌统一 401;验证:MockMvc 集成测试全绿

## 4. 文档与登记回填

- [x] 4.1 docs/接入手册.md:§9 新增「注解属性:请求时间窗与强制登录」小节(属性表 + requireLogin 服务端与登录签发示例);修订 §9.3「需登录态的注解拦截为后续迭代」表述为已支持;§9.2 配置表注明 `timestamp-window` 可被注解按端点覆盖;验证:配置键与代码绑定键核对一致,示例可直接粘贴编译
- [x] 4.2 README.md 模块能力表 security 行与包结构树(context 包)同步;子项目 AGENTS.md 速览技术栈描述同步;勾选本 tasks.md 全部任务项;验证:`mvn clean test` 根构建全绿后逐项核对
