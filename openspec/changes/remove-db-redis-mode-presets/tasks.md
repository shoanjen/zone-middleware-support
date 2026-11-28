# Tasks

## 1. redis 模块

- [x] 1.1 删除 `ZoneRedisProperties.java`、`ZoneRedisPresetEnvironmentPostProcessor.java`、`ZoneRedisKeepAliveAutoConfiguration.java`、`ZoneRedisLegacyKeepAliveConfiguration.java`
- [x] 1.2 删除 `META-INF/spring.factories`（仅注册 EEP），`AutoConfiguration.imports` 移除两条 keep-alive 条目
- [x] 1.3 `ZoneRedisAutoConfiguration`：去 `ZoneRedisProperties` 依赖，启动日志去 mode/keepAlive 项，保留 `zone-redis enabled` 关键字与官方生效值

## 2. database 模块

- [x] 2.1 删除 `ZoneDbPresetEnvironmentPostProcessor.java` 与其 spring.factories 注册
- [x] 2.2 `ZoneDatabaseProperties`：删 mode / `DeployMode` / `parsedMode()`，保留 `mybatis` 段
- [x] 2.3 `ZoneDataSourceAutoConfiguration`：去 mode 日志项与 `ZoneDatabaseProperties` 装配引用

## 2A. database 键面清零（mybatis 两键，2026-11-28 追加）

- [x] 2A.1 `ZoneMyBatisPlusConfiguration`：删分页 @ConditionalOnProperty 开关、删 ZoneMapperScannerRegistrar 与扫描键读取，去 `ZoneDatabaseProperties` 装配
- [x] 2A.2 删除 `ZoneDatabaseProperties.java`（`zone.middleware.db.*` 键面清零）
- [x] 2A.3 `ZoneMyBatisPlusIntegrationTest`：Mapper 用例改 `@MapperScan` 用户配置；`pagination-enabled=false` 用例改为上游自定义拦截器接管用例
- [x] 2A.4 database-orm spec delta（扫描官方路径 / 分页无开关）与文档同步（README、手册 §7/§12/§15.1）

## 3. http 模块

- [x] 3.1 新增 `ZoneOfficialHttpClientProperties`（自建绑定 `spring.http.client` 前缀：connectTimeout / readTimeout / redirects）
- [x] 3.2 `ZoneHttpProperties`：删 connect-timeout / read-timeout / redirect 与 `Redirect` 枚举，保留 max-concurrent-requests 与 retry.*
- [x] 3.3 `ZoneHttpAutoConfiguration`：改读官方绑定（兜底 5s/10s/NORMAL），`ZoneHttpTemplate` readTimeout 经构造传入

## 4. 测试

- [x] 4.1 删除 `ZoneRedisPresetEnvironmentPostProcessorTest`、`ZoneDbPresetEnvironmentPostProcessorTest`、`ZoneRedisPropertiesTest`
- [x] 4.2 `ZoneDatabasePropertiesTest` 去 mode 用例；`ZoneDataSourceConfigurationTest` 去 CLOUD 预设用例
- [x] 4.3 http 测试改官方键名与 redirects 两值枚举

## 5. 文档与登记

- [x] 5.1 README：删 LOCAL/CLOUD 预设表与 keep-alive 描述，http 表改官方键
- [x] 5.2 接入手册：redis/database/http 配置节重写，删预设/keep-alive 内容，新增迁移对照表与生产建议官方键清单、keep-alive customizer 配方
- [x] 5.3 AGENTS.md 存根备注更新 + 根总纲 `../AGENTS.md` 变更记录回填

## 6. 验证

- [x] 6.1 根构建 `mvn clean install` 全模块绿（含本机 Redis 集成测试）
- [x] 6.2 src/main grep `DeployMode|parsedMode|PresetEnvironmentPostProcessor|zone.middleware.http.connect-timeout` 零命中
- [x] 6.3 `openspec validate --strict` 通过
