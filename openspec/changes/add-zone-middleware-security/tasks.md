# Tasks

## 1. 模块脚手架与公共改动

- [x] 1.1 创建 `zone-middleware/zone-middleware-security` 子模块:pom(依赖 `zone-middleware-redis`、spring-webmvc/jakarta servlet-api,版本走 Boot 3.5.6 BOM;JSON 用 fastjson2)、包目录 `com.zone.middleware.security`,并在 `zone-middleware/pom.xml` 注册 `<module>`;验证:`mvn -pl zone-middleware/zone-middleware-security -am compile` 通过
- [x] 1.2 common 侧改动:`ZoneConstants` 新增 `HEADER_AUTH_TOKEN = "Zone-Auth-Token"` 常量;`BaseRequest.sign` 标注 `@Deprecated`(javadoc 注明签名改经请求头承载);验证:`mvn -pl zone-middleware-common test` 全绿,既有测试无回归

## 2. 签名核心算法(sign)

- [x] 2.1 实现 `ZoneSignUtils`:待签名串构造(顶层参数收集、空值剔除、TreeMap 升序、`k1=v1&` 连接、末尾 `&key=<secret>`)、嵌套值 fastjson2 默认序列化保插入序、`md5Hex` 32 位小写;摘要算法以常量收口单点;验证:单测覆盖升序拼串、空值剔除、复杂值序列化(同输入同输出)三个 spec 场景
- [x] 2.2 实现 reqId 生成:复用 `ZoneRandomUtils` 生成 32 位无连接符字母数字串;验证:单测断言长度 32、字符集字母数字、不含 `-`、两次生成不同

## 3. 服务端校验链(web)

- [x] 3.1 定义 `@ZoneSignCheck` 注解(`@Target({TYPE, METHOD})`,`RUNTIME`);验证:编译通过,元注解断言单测
- [x] 3.2 实现 `ZoneRepeatableReadFilter`(`OncePerRequestFilter` + `ContentCachingRequestWrapper`,缓存上限 1MB 超限拒绝);验证:单测覆盖 body 读两次内容一致、超限请求被拒
- [x] 3.3 实现 `ZoneSignCheckInterceptor` 校验链:注解探测(method 优先→class)→ 取 `Zone-Access-Token` 头 → platform 分流(web 免 deviceId / pc-mobile-pad 必填且 `[A-Za-z0-9_-]+` / 其余拒绝)→ timestamp 窗口(默认 5 分钟,`Duration` 可配)→ reqId nonce(Redis SETNX,TTL=窗口,fail-closed)→ 重建签名 `MessageDigest.isEqual` 常量时间比对;失败统一 HTTP 401 + `ZoneResult`(模块内 `AuthError` 实现 `ZoneError`);验证:单测逐条覆盖 api-sign-auth spec 的全部 Scenario(有效通过/缺头拒绝/web 无 deviceId/客户端缺与非法 deviceId/未知平台/窗口内外/可配窗口/首过与重放/nonce 过期/存储不可用拒绝/签名不符/篡改参数)
- [x] 3.4 从请求提取参数的实现:POST body 解析为 JSONObject 取顶层 kv(经缓存 body)、GET 取 query 参数;验证:单测覆盖 GET 与 JSON POST 两类请求拼串一致(spec「调用方与服务端一致」场景)

## 4. 登录令牌(token)

- [x] 4.1 实现 `ZoneSecurityProperties`(`zone.middleware.security.*`:sign.secret、sign.timestamp-window、token.ttl、token.renew-sliding、token.multi-device);验证:属性绑定单测(默认值 + 自定义覆盖)
- [x] 4.2 实现 `ZoneTokenService`:签发(`SecureRandom` ≥128bit 字母数字、Redis key `zone:security:token:<token>` 存用户信息,TTL 默认 7 天可配)、解析(非法/过期/撤销返回 null 不抛异常)、滑动续期(默认开,可关)、登出(按令牌)与踢下线(按用户,`zone:security:user-token:<userId>` Set + 惰性清理)、多端并存/单端互斥配置;验证:单测+集成测试覆盖 login-token spec 全部 Scenario(签发/默认与自定义 TTL/合法解析/非法 null/撤销失效/续期与关闭/登出/踢下线/多端并存/单端踢旧),对齐 `zone-middleware-redis` 既有集成测试的 Redis 接入方式
- [x] 4.3 实现请求头取值方法(读 `Zone-Auth-Token`,缺失/空返回 null);验证:单测覆盖携带与未携带两场景

## 5. 自动装配与端到端集成

- [x] 5.1 实现 `ZoneSecurityAutoConfiguration`(注册 filter、interceptor 经 `WebMvcConfigurer`;`@ConditionalOnClass(ZoneRedisTemplate)`,`@AutoConfiguration(after = ZoneRedisAutoConfiguration)`;启用校验而 `sign.secret` 未配置时启动期快速失败)+ `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册;验证:上下文加载测试(secret 缺失启动失败、齐全装配成功、redis 类缺失不装配)
- [x] 5.2 端到端集成测试:`@ZoneSignCheck` Controller + MockMvc 全链路——客户端工具签名→校验通过进入业务、重放被拒、篡改被拒、`BaseRequest.sign` 字段值不参与校验(请求头唯一来源);验证:集成测试全绿

## 6. 文档与登记回填

- [x] 6.1 编写接入文档(子项目 README 或 docs/):`@ZoneSignCheck` 接入步骤、客户端签名与 reqId 生成示例、`ZoneTokenService` 用法、配置项说明——配置示例按接入手册规范**必含 yml + properties 双风格**;验证:文档中的配置键与代码实际绑定键逐一核对一致,示例代码可直接粘贴编译
- [x] 6.2 登记回填:子项目 AGENTS.md 速览技术栈补 security 子模块;核对总纲第五节总表(不可运行库,无端口变更)与第六节(无新增模型厂商/中间件);验证:`mvn clean test` 根构建全绿后逐项核对登记内容
