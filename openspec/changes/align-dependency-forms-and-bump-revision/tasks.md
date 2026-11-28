# Tasks

## 1. 根 POM：版本登记与基线升版

- [x] 1.1 复核并钉版：Maven Central 解析核实 `nacos-client` 3.x 最新稳定版（探索期结论 3.3.0）、`dubbo-bom` 3.3.x 最新（探索期结论 3.3.7）、`druid-spring-boot-3-starter` 1.2.20+ 线最新补丁版；验证命令 `mvn dependency:get -Dartifact=<groupId>:<artifactId>:<version>` 逐坐标成功
- [x] 1.2 根 POM `properties` 新增 `nacos.client.version`、`dubbo.version`、`druid.spring.boot.starter.version` 三属性，`dependencyManagement` 新增 `com.alibaba.nacos:nacos-client` 条目与 `org.apache.dubbo:dubbo-bom`（`type=pom scope=import`）导入，`com.alibaba:druid` 条目替换为 `com.alibaba:druid-spring-boot-3-starter`；验证 `mvn -q -N help:effective-pom` 中三条目版本与属性一致
- [x] 1.3 `revision` 属性 `1.0.0-SNAPSHOT` → `1.0.1-SNAPSHOT`；验证 `mvn -q help:evaluate -Dexpression=project.version` 输出 `1.0.1-SNAPSHOT`

## 2. database 模块：Druid 迁移与测试重写

- [x] 2.1 删除 `ZoneDataSourceAutoConfiguration` 与 `ZoneOfficialDataSourceProperties` 两个类，database 模块 POM 删除裸 `com.alibaba:druid` 依赖，并在 test scope 新增 `com.alibaba:druid-spring-boot-3-starter`（供 starter 装配链测试）；验证 `grep -r "ZoneDataSourceAutoConfiguration\|ZoneOfficialDataSourceProperties" zone-middleware/zone-middleware-database/src/main` 无残留
- [x] 2.2 新增 Druid filter 后置校验自动配置：`@ConditionalOnClass(DruidDataSource.class)` + `BeanPostProcessor` 在数据源 Bean afterInitialization 阶段读取 `spring.datasource.druid.filters`，对照 `FilterManager.loadFilterConfig()` 注册表校验，未注册别名抛 `ZoneBusinessException(CONFIG_ERROR)`（错误信息含别名与合法取值提示），埋点走 `ZoneLog`；注册进 `AutoConfiguration.imports`；验证：见 2.4 测试
- [x] 2.3 修正 `ZoneMyBatisPlusConfiguration` / `ZoneFlywayConfiguration` 的 `@AutoConfigureAfter` 引用（指向 `DataSourceAutoConfiguration` 或按需移除），更新模块 POM `description` 与 suite POM `description`（补齐 web 模块名遗漏）；验证 `mvn -q -pl zone-middleware/zone-middleware-database -am compile` 通过
- [x] 2.4 重写 `ZoneDataSourceConfigurationTest` 为三路：Boot 原生 Hikari（上下文启动后 DataSource 为 HikariCP）、starter Druid（test classpath 含 starter 时 DataSource 为 Druid）、后置校验正反例（合法别名 `stat,slf4j` 通过；`nope` 启动失败且错误信息含别名）；验证 `mvn -q -pl zone-middleware/zone-middleware-database test` 全绿，H2/Flyway/MP 既有集成测试在 Boot 缺省 Hikari 下回归通过

## 3. 文档同步

- [x] 3.1 README 与《上游接入反馈与改动清单》版本串 `1.0.0-SNAPSHOT` → `1.0.1-SNAPSHOT`；验证 `grep -rn "1.0.0-SNAPSHOT" README.md docs/` 零命中（历史叙事段落如需保留旧版本号表述，单独列明豁免行）
- [x] 3.2 《接入手册》database 章节重写：druid-spring-boot-3-starter 声明片段（带显式版本号并注明 dependencyManagement 不随依赖传递）、`spring.datasource.druid.*` 池参数与 `filter.<name>.enabled` 细粒度键示例、stat-view-servlet 监控页最小配置、切池语义说明（声明依赖即切换，删除 `spring.datasource.type` 用法）、url 缺失报错时机变化提示；yml + properties 双风格、按需引入与套件引入两种方式并列；验证：文档评审通过（示例键与 2.x 实际绑定前缀一致）
- [ ] 3.3 项目 `AGENTS.md` 存根备注追加本次变更条目；工作区总纲变更记录回填（总纲在工作区根、超出本仓编辑范围，执行时与用户确认或由用户执行）；验证：存根 diff 就位、总纲条目经用户确认

## 4. 集成验证

- [x] 4.1 全 reactor `mvn install` 通过（含 flatten 后 1.0.1-SNAPSHOT 字面量落盘）；验证本地仓库 `~/.m2/repository/com/zone/middleware/` 下 POM 无 `${revision}` 残留且版本为 `1.0.1-SNAPSHOT`
- [x] 4.2 `openspec validate --change align-dependency-forms-and-bump-revision` 通过；版本串全量复核：`grep -rn "1.0.0-SNAPSHOT" --exclude-dir=target --exclude-dir=.git .` 仅允许的豁免行存在
