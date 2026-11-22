# Design

## 总体拓扑

四个能力点分两个载体落地：**新模块 `zone-middleware-web`**（两个可选入站能力）+ 既有模块内纯增量扩展（http 泛型重载、redis 原语、common 废弃标注）。选新模块而非塞入 http/security 的理由：http 是出站客户端语义、security 是鉴权语义，入站通用支撑自成能力边界；且按需引入方（只要 advice/filter 不要签名）能以单坐标精确引入。

## 新模块 zone-middleware-web

### 接线

- 根 pom：`dependencyManagement` 登记 `zone-middleware-web` + `<modules>` 增加；`zone-middleware` 聚合 pom `<modules>` 增加；suite pom `<dependencies>` 增加（suite 传递全量语义不变）。
- 模块依赖：`zone-middleware-common`（ZoneResult/ZoneBusinessException/ZoneConstants）、`zone-middleware-log`（ZoneLog/ZoneTraceContext）、`spring-boot-starter`、`spring-webmvc`（compile——提供 @RestControllerAdvice/BindException/MethodArgumentNotValidException）、`jakarta.servlet-api`（provided，由上游 web 容器提供）、lombok optional、spring-boot-starter-test。
- 自动装配：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `ZoneWebInboundAutoConfiguration`；无 EnvironmentPostProcessor。

### ZoneGlobalExceptionAdvice（开关 zone.middleware.web.exception-advice.enabled，默认 false）

- 配置类 `@ConditionalOnClass(RestControllerAdvice.class)` + `@ConditionalOnProperty(havingValue="true", matchIfMissing=false)`；非 web 类路径（无 spring-webmvc）时条件不通过整类跳过，非 web 消费方零影响。
- advice 类 `@RestControllerAdvice + @Order(Ordered.LOWEST_PRECEDENCE)`——Spring 按 Order 从低到高（数值小优先）挑选匹配的 advice，取 LOWEST_PRECEDENCE 保证上游自定义 advice（默认无 Order 或更小值）优先命中；上游 advice 与本 advice 均 LOWEST_PRECEDENCE 时按 Bean 注册顺序，上游先注册者胜，实现"让位"。
- 三类 handler：
  - `ZoneBusinessException` → `ZoneResult.fail(e.getError(), e.getMessage())`；
  - `MethodArgumentNotValidException` / `BindException` → `BIZ-4001 PARAM_INVALID`，message 取首个字段校验消息（field + defaultMessage），日志 WARN；
  - 兜底 `Exception` → `SYS-9999 INTERNAL_ERROR` 通用提示（"系统繁忙，请稍后重试"），`ZoneLog.error` 记录堆栈，响应不携带 e.getMessage()（防泄露内部细节）。
- 返回 `ZoneResult<?>`，由上游已注册的消息转换器（fastjson2 converter 或默认 jackson）序列化——本模块不绑定序列化器，遵守"不使用 jackson、不混入 jackson"约定（依赖树校验）。

### ZoneTraceInboundFilter（开关 zone.middleware.web.trace-filter.enabled，默认 false）

- `FilterRegistrationBean<ZoneTraceInboundFilter>`，urlPatterns `/*`，order `Ordered.HIGHEST_PRECEDENCE`（先于 security 的签名过滤器，确保鉴权日志已有链路标识）。
- 逻辑：`doFilter` 入口——读 `ZoneConstants.HEADER_TRACE_ID`（`Zone-Trace-Id`）非空则 bind 该 traceId、空则生成 `t-<uuid 短格式>`；reqId 生成 `r-<uuid 短格式>`；`chain.doFilter` 后 finally `ZoneTraceContext.clear()`。
- 生成格式对齐既有约定：traceId/reqId 均为调用方显式传入哲学的默认兜底，格式与手册 §5.3 样例一致（`t-yyyyMMddHHmmss-<4位随机>` 风格由实现时对齐现有 E2E/样例取值，避免两套风格）。
- 响应头回写 `Zone-Trace-Id`：透传场景下游服务可直接续链（与 http 模块出站头常量复用同一常量）。

## http 泛型重载（http-client）

`ZoneHttpTemplate` 新增四个重载，全部复用现有 `exchange` 私有链路（重试/熔断/失败语义不变），仅反序列化入口从 `JSON.parseObject(body, responseType)` 换为 `JSON.parseObject(body, typeReference)`：

- `get(String url, TypeReference<T>)`、`get(String url, Map<String,String> headers, TypeReference<T>)`
- `post(String url, Object body, TypeReference<T>)`、`post(String url, Object body, Map<String,String> headers, TypeReference<T>)`

泛型用法约定（写入手册 §8）：调用方声明 `new TypeReference<ZoneResult<Order>>() {}`——fastjson2 按完整泛型树反序列化，data 为 Order 实例。既有 Class<T> 方法零改动。

## redis 原语（redis-operations）

`ZoneRedisTemplate` 新增五个方法，全部委托 `stringTemplate`（与既有方法同一封装层）：

- `Long incr(String key)`、`Long incrBy(String key, long delta)` → `opsForValue().increment/increment(key, delta)`；
- `Long ttl(String key)` → `getExpire(key, TimeUnit.SECONDS)`（-2 不存在 / -1 永不过期，Redis 原生语义）；
- `Boolean expire(String key, Duration ttl)`；
- `Boolean setIfAbsent(String key, Object value, Duration ttl)` → `opsForValue().setIfAbsent(key, JSON.toJSONString(value), ttl)`，与其他写方法一致的 fastjson2 存储。

无分布式锁的重入看门狗/续期语义（那是 Redisson 级能力），手册明确 setIfAbsent 仅适合"单次抢占 + 固定 TTL"的简易互斥场景。

## common sign 废弃

代码已 `@Deprecated`（javadoc 已注明），无需改代码；规格与手册 §4.3 字段表同步标注（废弃/兼容保留/下版本移除/替代方案指向 `@ZoneSignCheck`）。

## 手册欠账落地

- **§3.3 多模块/DDD 分层接入建议**（新增小节）：映射表——suite→application（装配根）；database/http→infrastructure；security→web；log→service/web（横切）；common→service 及以下；**domain 层禁引任何 zone 构件**（保持领域纯净）。标注为"建议而非强制"，来自 zone-boot-template 实践验证。
- **§2 传递表标注**：log 模块行补注"传递 spring-boot-starter（spring-context 等），不含 spring-tx——应用层 `@Transactional` 需自引 spring-tx 或经 database 模块传递获得"。
- **§4.2 自定义错误码扩展**（新增小节）：实现 `ZoneError` 接口的枚举模板 + `ZoneBusinessException` 用法 + **码段约定**：`SYS-`（基础设施，本库独占 1001–9999）、`BIZ-`（业务，本库内置 4001–4099 段）；上游自定义业务码 MUST 使用 `BIZ-7xxx`/`BIZ-9xxx` 等未占段（禁止侵入 4001–4099 内置段），码值含义由各上游自持登记。
- **§4.3** 字段表 sign 行标注"已废弃（兼容保留），新代码禁用"。
- **§5.3 补标准 Filter 样例**：单体应用入口绑定样例代码（与 web 模块 ZoneTraceInboundFilter 行为一致——反哺自 zone-boot-template 实现），并指向"也可直接开启 `zone.middleware.web.trace-filter.enabled` 免自写"。
- **新模块章节**：模块总览表（§2）、按需引入示例（§3.1）、新 §10 zone-middleware-web（配置表 + 用法 + 开关），原 §10–§14 顺延为 §11–§15。

## 测试策略

- web 模块：`@SpringBootTest + MockMvc`——advice 默认关（无 advice 时 500 原生行为）、开启后三类异常各自断言返回体错误码、上游自定义 advice 优先命中；filter 默认关、开启后透传/生成/清理三场景（第二个请求断言无串号）。
- http：TypeReference 四重载测试（对象/列表泛型 data 类型断言 + headers 传递 + 假服务端）。
- redis：五原语集成测试（连共享实例，db 15，不可达跳过——沿用既有测试模式）。
- 全量回归 `mvn clean install`（suite 模块数 9→10）。

## 风险与对策

| 风险 | 对策 |
|---|---|
| suite 消费方非 web 应用引入 spring-webmvc 传递依赖 | advice/filter 类默认关且 @ConditionalOnClass 保护；依赖树 review：spring-webmvc 无传递 jackson（jackson 约定不破，apply 时 grep 验证） |
| 上游 advice 与本 advice 同为 LOWEST_PRECEDENCE 的优先级歧义 | 手册写明：需要压过本 advice 时显式 @Order 更小值；测试覆盖"上游自定义 advice 优先" |
| Filter 生成格式与现有样例风格不一致 | 实现前对照手册 §5.3 与 E2E 现值对齐 |
| Manual 章节号顺延遗漏引用 | apply 时全文 grep "第 10 节"等交叉引用统一更新 |
