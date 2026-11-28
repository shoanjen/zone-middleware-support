# Spec Delta

## ADDED Requirements

### Requirement: 连接池装配所有权与依赖声明切池

HikariCP 数据源 SHALL 由 Spring Boot 原生数据源自动装配提供（全量 `spring.datasource.hikari.*` 官方键生效）；Druid 数据源 SHALL 由上游在依赖中声明 druid-spring-boot-3-starter 启用，由该官方 starter 自动装配（绑定 `spring.datasource.druid.*` 官方前缀）；未声明 Druid starter 时 MUST 缺省装配 HikariCP，`spring.datasource.type` 不再作为本能力的切池开关。

#### Scenario: 缺省装配 HikariCP

- **WHEN** 上游仅声明本库依赖并配置连接信息，未声明 druid starter 依赖
- **THEN** 装配的 DataSource 底层为 Spring Boot 原生 HikariCP 连接池

#### Scenario: 声明 starter 即切换 Druid

- **WHEN** 上游在依赖中声明 druid-spring-boot-3-starter
- **THEN** 装配的 DataSource 底层为官方 starter 装配的 Druid 连接池

#### Scenario: Druid 池参数官方键绑定兼容

- **WHEN** 上游通过 `spring.datasource.druid.*` 配置池参数
- **THEN** starter 装配的 Druid 连接池按配置值生效（前缀与历史配置兼容）

## MODIFIED Requirements

### Requirement: 上游自定义连接配置

连接 URL、用户名、密码、驱动类及池参数（最大连接数、最小空闲等）SHALL 由上游应用通过 Spring Boot 官方键配置提供：连接信息对应 `spring.datasource.url` / `spring.datasource.username` / `spring.datasource.password` / `spring.datasource.driver-class-name`，池参数对应 `spring.datasource.hikari.*`（HikariCP）或 `spring.datasource.druid.*`（Druid，经官方 starter 装配）；连接 URL 缺失时回归 Spring Boot 原生行为（HikariCP 路径首次获取连接时报错，Druid 路径在 starter 数据源初始化期报错），本能力不再提供启动期库级快速失败。

#### Scenario: 完整配置装配

- **WHEN** 上游通过官方键配置 URL、用户名、密码与池参数后装配
- **THEN** 连接池按配置值初始化并可正常获取连接

#### Scenario: 缺少连接 URL

- **WHEN** 上游未配置数据库 URL 即启动
- **THEN** 回归 Spring Boot 原生行为：类路径含内嵌数据库驱动时自动装配内嵌数据源；否则 HikariCP 路径在首次获取连接时失败且提示缺少连接信息，Druid 路径在数据源初始化期失败

### Requirement: Druid filter 别名配置与校验

上游 SHALL 通过官方键 `spring.datasource.druid.filters`（逗号分隔别名，如 stat,wall,slf4j）为 Druid 连接池启用 filter，亦可采用官方 starter 提供的 `spring.datasource.druid.filter.<name>.enabled` 细粒度键；本能力 MUST 在 Druid 数据源装配完成后校验 filters 别名（对照 Druid 注册表），存在未注册别名时应用启动失败并给出明确错误信息，不得依赖 Druid 原生对未知别名的静默跳过；监控页 stat-view-servlet MUST NOT 由本能力代码注册，由官方 starter 按上游配置（`spring.datasource.druid.stat-view-servlet.*`）提供。

#### Scenario: 合法别名装配生效

- **WHEN** Druid 连接池配置 filters 为 stat,slf4j 后启动
- **THEN** 对应 filter 实例装配至数据源，SQL 监控与日志 filter 生效

#### Scenario: 未注册别名快速失败

- **WHEN** Druid 连接池配置 filters 含未注册别名（如 nope）后启动
- **THEN** 应用启动失败，错误信息包含该别名与合法取值提示

## REMOVED Requirements

### Requirement: 连接池类型切换

**Reason**: 自研双池装配拆除后，切池控制权移交依赖声明与官方自动装配（HikariCP 缺省、Druid 经 druid-spring-boot-3-starter 承接），`spring.datasource.type` 不再作为本能力切池开关，避免同一开关在两条装配链下语义漂移。

**Migration**: 需要 Druid 的上游改为在依赖中声明 druid-spring-boot-3-starter；删除存量 `spring.datasource.type` Druid 配置（`spring.datasource.druid.*` 池参数键保持不变，由 starter 同前缀超集兼容）。
