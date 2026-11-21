# Proposal

## Why

zone-middleware 已沉淀 database/http/log/redis 等基础支撑,但接口安全层缺位:上游应用的接口防伪造(参数签名)与用户登录态(会话令牌)没有统一支撑,各应用自实现口径不一。本次以自研轻量方案补齐该层(明确不引入 JWT/saToken 等开源鉴权组件),与既有 BaseRequest、ZoneRedisTemplate、ZoneAuthInterceptor SPI 形成闭环。

## What Changes

- 新增子模块 `zone-middleware/zone-middleware-security`(包根 `com.zone.middleware.security`),注册进 `zone-middleware/pom.xml`。
- 接口签名鉴权(web / 客户端双策略):
  - `@ZoneSignCheck` 注解(类/方法皆可标、方法优先)声明需签名校验的端点,拦截器按注解介入。
  - 签名算法:参数 TreeMap 升序、空值剔除、`k1=v1&...&key=<secret>` 拼串后 MD5,32 位小写 hex;sign 放请求头 `Zone-Access-Token`(复用既有常量)。
  - 按 `platform` 分流:web(platform=web)无 deviceId;客户端(pc/mobile/pad)必填 deviceId 并做额外校验;双策略共用同一个 secret。
  - 防重放:timestamp 时间窗校验 + reqId nonce(Redis SETNX)。
- 登录令牌:opaque token + Redis 方案——`SecureRandom` 生成不透明令牌,服务端存 token→用户信息,TTL 默认 7 天可配置,支持滑动续期、多端并存/单端踢旧切换、解析与撤销;token 经请求头 `Zone-Auth-Token` 携带(`ZoneConstants` 新增常量)。
- 既有能力调整:**BREAKING**(语义层):接口签名从 `BaseRequest.sign` 字段迁移至请求头 `Zone-Access-Token`,服务端校验只认请求头;`BaseRequest.sign` 字段标 `@Deprecated` 保留兼容,存量携带值不再被校验消费。

## Capabilities

### New Capabilities

- `api-sign-auth`: 接口签名鉴权能力——web/客户端双策略的参数签名算法、注解式接入、服务端校验链(签名比对、timestamp 窗口、reqId 防重放、deviceId 校验)、签名客户端工具(reqId 生成、签名计算)。
- `login-token`: 登录令牌能力——不透明令牌的签发、解析、滑动续期、撤销(登出/踢下线)、TTL 与多端策略配置,基于 Redis 存储的有状态会话。

### Modified Capabilities

- `common-request`: sign 字段的承载与消费语义变更——sign 由请求头 `Zone-Access-Token` 承载(服务端校验唯一来源),`BaseRequest.sign` 降级为废弃兼容字段;BaseRequest 其余七个字段与 platform/os 枚举解析工具语义不变。

## Impact

- **代码**:新增 `zone-middleware-security` 子模块(annotation / sign / web / token / config 五组类);`zone-middleware/pom.xml` 增 `<module>`;`zone-middleware-common` 的 `ZoneConstants` 增 `HEADER_AUTH_TOKEN` 常量、`BaseRequest.sign` 标 `@Deprecated`。
- **依赖**:security 模块依赖 `zone-middleware-common`(parent 已注入)、`zone-middleware-redis`(登录令牌与防重放存储)、spring-webmvc/jakarta servlet-api(拦截器,Boot 3.5.6 BOM 内,jakarta 命名空间);JSON 处理遵循总纲约定用 fastjson2,不引入 jackson。
- **配置**:新增 `zone.middleware.security.*` 配置段(secret、timestamp 窗口、token ttl、多端策略、续期开关)。
- **上游应用**:接入方式由继承 BaseRequest 升级为「继承 BaseRequest + 标注 @ZoneSignCheck + 请求头携带 sign」;已直接往 `BaseRequest.sign` 写签名的调用方需迁移到请求头(**BREAKING**)。
- **文档/登记**:子项目 AGENTS.md 速览技术栈描述、总纲第五节总表如涉及模块清单需同步(无端口变更,不可运行库)。
