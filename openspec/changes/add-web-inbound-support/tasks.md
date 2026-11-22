# Tasks

## 1. 新模块 zone-middleware-web：接线与全局异常处理器

- [x] 1.1 模块接线：根 pom（dependencyManagement + modules）、zone-middleware 聚合 pom（modules）、suite pom（dependencies）；模块 pom（common/log/spring-boot-starter/spring-webmvc/jakarta.servlet-api provided/lombok/test）
- [x] 1.2 ZoneGlobalExceptionAdvice：@RestControllerAdvice + @Order(LOWEST_PRECEDENCE)，三类 handler（ZoneBusinessException→原码失败返回体；MethodArgumentNotValidException/BindException→BIZ-4001 含字段级消息；兜底 Exception→SYS-9999 通用提示 + ZoneLog.error 记堆栈）
- [x] 1.3 ZoneWebInboundAutoConfiguration：@ConditionalOnClass(RestControllerAdvice) + exception-advice 开关 @ConditionalOnProperty 默认关；AutoConfiguration.imports 注册；properties 类（exception-advice.enabled / trace-filter.enabled 两个开关）
- [x] 1.4 advice 测试：MockMvc 覆盖默认关（500 原生行为不变）、业务异常转码、参数校验含字段消息、兜底不泄露内部细节、上游自定义 advice 优先命中

## 2. Trace 入站 Filter

- [x] 2.1 ZoneTraceInboundFilter：读 Zone-Trace-Id 透传/缺失生成、reqId 生成、finally clear；FilterRegistrationBean 注册（order HIGHEST_PRECEDENCE）；trace-filter 开关默认关
- [x] 2.2 filter 测试：MockMvc 覆盖默认关、透传头、无头生成、连续两请求无串号（清理验证）、响应头回写 Zone-Trace-Id

## 3. http 泛型重载

- [x] 3.1 ZoneHttpTemplate 新增四个 TypeReference 重载（get/post × 带或不带 headers），复用既有 exchange 链路（重试/熔断/失败语义不变）
- [x] 3.2 测试：对象泛型 data 类型断言、列表泛型、带 headers 变体、既有 Class 方法回归

## 4. redis 原语

- [x] 4.1 ZoneRedisTemplate 新增 incr/incrBy/ttl/expire/setIfAbsent（委托 stringTemplate，fastjson2 写入语义一致）
- [x] 4.2 集成测试：计数累加、ttl 读值（含 -2 语义）、expire 续期、setIfAbsent 抢占成功/失败

## 5. 手册与文档欠账（P2）

- [x] 5.1 §2 传递表标注 log 不带 spring-tx（@Transactional 需自引或经 database 传递）；模块总览表增加 zone-middleware-web 行
- [x] 5.2 §3.1 按需引入示例补 web 模块；新增 §3.3「多模块/DDD 分层接入建议」（suite→装配根、database/http→infrastructure、security→web、log→service/web、common→service、domain 禁引；标注建议非强制）
- [x] 5.3 §4.2 新增「自定义错误码扩展」小节（ZoneError 实现模板 + 码段约定 SYS-/BIZ- 内置段与上游 BIZ-7xxx/9xxx 未占段）；§4.3 字段表 sign 行标注废弃兼容保留（指向 @ZoneSignCheck + Zone-Access-Token）
- [x] 5.4 §5.3 补入站 Filter 标准样例（反哺自 zone-boot-template 实现）并指向 web 模块可选自动装配
- [x] 5.5 新增 §10 zone-middleware-web 章节（开关配置表 + 用法），原 §10–§14 顺延为 §11–§15 并全文 grep 交叉引用同步；§11 properties 全量参考补 web 开关键
- [x] 5.6 README 模块表/配置总表同步；AGENTS.md 存根速览更新；http 手册 §8 补 TypeReference 泛型示例；redis 手册 §6 补原语方法示例

## 6. 回归与登记

- [x] 6.1 全量回归 mvn clean install（10 模块全绿）；依赖树校验（web 模块无 jackson 传递、suite 完整性）
- [x] 6.2 消费方兼容确认：suite 存量消费方（zone-boot-template）零改动启动验证（新模块默认关零行为）；总纲变更记录登记
