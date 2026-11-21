# Tasks

## 1. 依赖治理与父 pom

- [x] 1.1 父 pom dependencyManagement 新增 fastjson2 2.0.53、UserAgentUtils 1.21，移除 junit4 版本管理；验证 `mvn -N validate` 通过
- [x] 1.2 调整 5 个模块 pom 依赖（移除 jackson/starter-web/JPA/AOP/jedis/logstash-encoder/embedded-redis，新增 fastjson2 与 common 依赖），验证 `mvn clean install -DskipTests` 全模块编译安装通过

## 2. zone-middleware-common

- [x] 2.1 实现 `ZoneResult`、`ZoneError`、`SystemError`、`BusinessError`、`ZoneBusinessException`，验证 ZoneResultTest 与错误体系测试通过（含 fastjson2 序列化往返、码段区分断言）
- [x] 2.2 实现 `ZoneIpUtils`、`ZoneBrowserUtils`（含 `ZoneBrowserInfo`）、`ZoneRandomUtils`，验证工具类测试通过（多级代理/空 UA/非法参数/同头同指纹/字符集断言）

## 3. zone-middleware-log

- [x] 3.1 实现 `ZoneLog`、`ZoneLogSettings`、`ZoneTraceContext`、`ZoneLogAutoConfiguration`（spring.factories 注册），验证 ZoneLogTest 通过（四级输出、格式顺序、级别门槛、traceId 携带与清理，用 ListAppender 断言）

## 4. zone-middleware-redis

- [x] 4.1 实现 `ZoneRedisProperties`（LOCAL/CLOUD 预设解析）、fastjson2 值序列化、`ZoneRedisAutoConfiguration`，验证属性预设测试通过（云端默认更保守、逐项覆盖、非法 mode 启动失败）
- [x] 4.2 实现 `ZoneRedisTemplate`（String/Hash/List/Set/ZSet 增删改查 + mGet/批量删除/hDel），验证序列化往返单测与共享实例集成测试通过（assumeTrue 不可达跳过）

## 5. zone-middleware-database

- [x] 5.1 实现 `ZoneDatabaseProperties`（模式与双池预设）、`ZoneDataSourceAutoConfiguration`（Hikari/Druid 切换装配，含 @AutoConfigureBefore 排序修正），验证 H2 双池装配测试通过（默认 Hikari、切换 Druid、未知 pool-type 失败、缺 URL 失败）
- [x] 5.2 实现 MyBatis-Plus 整合（分页插件默认装配 + mapper-package 扫描），验证 H2 上 BaseMapper CRUD 与分页测试通过
- [x] 5.3 实现 Flyway 整合（默认路径/baseline/开关 + EnvironmentPostProcessor 联动短路），验证 H2 迁移测试通过（自动迁移、自定义路径、关闭迁移）

## 6. zone-middleware-http

- [x] 6.1 实现 `ZoneHttpProperties`、`ZoneAuthInterceptor` SPI、`ZoneHttpTemplate`（JDK HttpClient、GET/POST、并发上限信号量），验证 GET/POST/鉴权/并发测试通过（JDK HttpServer 本地端点）
- [x] 6.2 实现重试（退避+状态码）与 `ZoneCircuitBreaker`（CLOSED/OPEN/HALF_OPEN），验证重试触发/耗尽/默认关闭与熔断阈值/冷却恢复/默认关闭测试通过

## 7. 全局验证

- [x] 7.1 全量 `mvn clean install` 通过（7 模块 SUCCESS，共 88 个测试，含共享 Redis 集成测试），验证全部测试绿
- [x] 7.2 依赖树检查：`mvn dependency:tree` 5 个模块均无 jackson-databind、spring-boot-starter-data-jpa、hibernate-core、spring-boot-starter-aop、aspectjweaver、logstash-logback-encoder 构件
- [x] 7.3 更新 README：模块能力说明 + `zone.middleware.*` 全量配置表 + 上游接入示例，验证文档中的坐标与配置键与实现一致

## Workflow follow-up

- 全部任务完成后按项目评审要求归档 change（`openspec archive add-zone-middleware-core-capabilities`）
- 归档后核对 `openspec list --specs` 中 8 个 capability 的主 spec 已生成

## 8. 依赖清理与 ZoneLog 门面重构（2026-11-17 追加）

- [x] 8.1 清理无用依赖（common 移除 spring-boot-starter 与 commons-lang3、redis 移除未使用的 common、database 移除 mysql-connector-java、父 pom 移除 mysql 版本管理与未用的 spring-boot-maven-plugin），验证全量构建通过
- [x] 8.2 ZoneLog 重构为静态门面：入参按「级别、类名、方法名、业务数据、正确或错误信息」，四级 ×（无业务数据/含业务数据）重载 + error 携 Throwable + 显式级别 log()，业务数据 fastjson2 序列化，验证 ZoneLogTest 通过
- [x] 8.3 库内埋点统一替换为 ZoneLog（log/redis/database 自动装配 + http 重试耗尽/熔断转换），并新增 ZoneLogInitEnvironmentPostProcessor 使设置先于一切 Bean 日志生效
- [x] 8.4 补齐遗漏 Lombok（ZoneLogLevel、测试 ZoneUser），验证全量 mvn clean install 通过与依赖树检查（无 mysql/commons-lang3/jackson，lombok optional）

## 9. ZoneLog 占位符化（2026-11-17 追加）

- [x] 9.1 ZoneLog 重构为 `{}` 占位符 + Object... 变参（字符串/JSON 透传/对象 JSON/null 渲染规则、末位 Throwable 自动堆栈、显式异常重载），移除业务数据独立参数重载（全 String 调用静默歧义），验证 ZoneLogTest 通过
- [x] 9.2 库内 8 处埋点全部改为占位符风格（消除字符串 + 拼接），验证编译与既有测试通过
- [x] 9.3 同步 spec/design/proposal/README，openspec validate --strict 通过
- [x] 9.4 全量 mvn clean install + 依赖树检查通过

## 10. 全方法规范化 Javadoc（2026-11-18 追加）

- [x] 10.1 src/main 全部方法补齐规范化 Javadoc（一句话功能 + @param/@return/@throws，含私有方法与工具类），验证 mvn clean install 通过
- [x] 10.2 类级 @param <T>、枚举类型与枚举常量、嵌套配置 getter 补齐注释（手写代码 doclint 零 warning）
- [x] 10.3 javadoc:joavadoc -Ddoclint=all 逐模块验证：无 error，手写方法零 warning（仅剩 Lombok 生成/隐式构造类的 default-ctor 噪音，业界惯例接受）

## 11. BaseRequest 基础请求载体（2026-11-21 追加）

- [x] 11.1 实现 `BaseRequest`（reqId/platform/ip/deviceId/os/version/timestamp/sign 八字段，可继承）与 `Platform`（web/pc/mobile/pad）、`OsType`（android/ios/windows/harmony/linux）枚举（`of()` 大小写不敏感容错解析），验证 BaseRequestTest 与 ZoneEnumsTest 通过
- [x] 11.2 验证 fastjson2 小写协议值反序列化（实测 2.0.53 原生支持，零兼容层）与子类继承往返
- [x] 11.3 同步 spec delta（common-request）/proposal/design/README，openspec validate --strict + 全量构建 + doclint 复查通过

## 12. BaseRequest 字段类型修正（2026-11-21 追加）

- [x] 12.1 platform 与 os 改为 String 原样透传（枚举从字段类型中移除，未知值/大小写形态不丢失），枚举定位为上层解析工具（of() 容错解析），验证 BaseRequestTest（透传/上层解析/继承往返）通过
- [x] 12.2 同步 spec delta（字符串透传 requirement）/proposal/design/README，openspec validate --strict + 全量构建通过
