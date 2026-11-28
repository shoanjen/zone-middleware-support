# Proposal

## Why

二方包的依赖治理存在两处欠账：其一，nacos 与 dubbo 尚无版本登记，下游接入时版本各行其是，需要在本库建立版本单一源与团队基线；其二，starter 形态审计发现 Druid 是全部模块中唯一存在官方 Boot starter 却仍以裸依赖 + 手工装配承载的特例——自研装配无法提供官方 starter 开箱即用的监控页（stat-view-servlet）、细粒度 filter 配置等生态能力，且随着其余键面全部官方化，自研 DataSource 装配已成为一致性最差的一块。同时本批次改动需要以版本号 1.0.1-SNAPSHOT 形成新基线。

## What Changes

- 根 POM 新增版本属性与 `dependencyManagement` 登记：`com.alibaba.nacos:nacos-client`（3.x 线最新稳定版 3.3.0，对齐 Nacos Server 3.x）、`org.apache.dubbo:dubbo-bom`（3.3.x 最新 3.3.7，一份 BOM 管住 dubbo-spring-boot-starter / dubbo-nacos-spring-boot-starter）。不新建封装模块、不引入 SCA 矩阵；dependencyManagement 仅服务库内单源与团队版本基线（Maven 语义不随依赖传递，已与上游确认）。
- **BREAKING** `zone-middleware-database` 拆除自研数据源装配：删除 `ZoneDataSourceAutoConfiguration` 与配套 `ZoneOfficialDataSourceProperties`。HikariCP 回归 Spring Boot 原生 `DataSourceAutoConfiguration`（获得全量 `spring.datasource.hikari.*` 官方键绑定）；Druid 改由 alibaba 官方 `druid-spring-boot-3-starter` 承接（绑定同为 `spring.datasource.druid.*` 前缀且为超集，存量上游配置基本兼容）。切池方式由「配置 `spring.datasource.type`」**BREAKING** 变更为「声明对应依赖即切换」（Hikari 缺省，声明 druid starter 即 Druid）。附带损失：`spring.datasource.url` 缺失的库级友好快速失败、`ZoneLog` 数据源 init 行、`zone-hikari`/`zone-druid` 池命名。
- **BREAKING** `spring.datasource.url` 缺失不再由本库启动期拦截，回归 Boot 原生语义（Hikari 路径首连接时报错 / Druid 路径 starter init 期报错）。
- Druid filter 别名快速失败校验保留：新增后置校验器（starter 装配完成后校验 `spring.datasource.druid.filters` 别名注册表，未知别名仍启动失败），延续「防 setFilters 静默跳过」的自研价值。
- 模块瘦身定位：`zone-middleware-database` 收敛为 MyBatis-Plus 分页拦截器默认装配 + Flyway 官方键默认值 + Druid filter 后置校验三块真空区能力。
- 版本基线：`revision` `1.0.0-SNAPSHOT` → `1.0.1-SNAPSHOT`（revision 单一源，仅根 POM 一行）；同步 README、接入手册、上游接入反馈清单中的版本串。
- 文档与登记：接入手册 database 章节重写（starter 声明片段带版本号、stat-view-servlet 监控页示例、`filter.<name>.enabled` 细粒度键引导、切池语义说明，yml + properties 双风格）；AGENTS.md 存根备注与总纲变更记录回填；suite pom description 补齐遗漏的 web 模块名。

## Capabilities

### New Capabilities

（无。版本登记、revision 升版与文档同步无运行时行为变化，不发明规格凑数。）

### Modified Capabilities

- `database-access`: 数据源装配所有权重构——删除「按 `spring.datasource.type` 切池」需求（改为依赖声明切池）；修改「连接配置」需求（url 缺失不再库级快速失败，回归 Boot 原生语义）；修改「Druid filter」需求（校验时序改为装配后校验，监控页归属随 starter 调整表述）；HikariCP 装配所有权移交 Boot 原生、Druid 装配所有权移交官方 starter 作为新增需求。

## Impact

- **代码**：`zone-middleware-database` —— 删除 `ZoneDataSourceAutoConfiguration`、`ZoneOfficialDataSourceProperties`；新增 Druid filter 后置校验自动配置；`ZoneMyBatisPlusConfiguration` / `ZoneFlywayConfiguration` 的 `@AutoConfigureAfter` 引用修正；`AutoConfiguration.imports` 更新；`ZoneDataSourceConfigurationTest` 重写（Boot 原生 Hikari + starter Druid + 后置校验三路）。
- **依赖**：database 模块 POM 删除裸 `druid`；根 POM 新增 `nacos-client` / `dubbo-bom` 条目，`com.alibaba:druid` 管理条目替换为 `com.alibaba:druid-spring-boot-3-starter`（1.2.20+ 线，落地时核实最新补丁版）。
- **上游兼容**：切池方式变更（`spring.datasource.type=Druid` → 声明 druid starter 依赖）；`spring.datasource.druid.*` 存量键前缀不变且被 starter 超集兼容；url 缺失报错时机与文案变化。当前全部构件为未发版 SNAPSHOT，无存量下游兼容负担。
- **文档**：README、`docs/接入手册.md`（database 章节重写）、`docs/上游接入反馈与改动清单.md`、项目 `AGENTS.md` 存根备注、总纲变更记录。
- **归档顺序**：与待归档变更 `remove-db-redis-mode-presets` 无需求交叠，归档先后无影响；本 delta 基于该变更归档后的 database-access 基线编写。
