# database-orm Specification

## Purpose

在统一数据源之上为上游应用整合 MyBatis-Plus 与 Flyway，使 ORM 操作与数据库版本迁移开箱可用，且不引入 JPA。

## Requirements

### Requirement: MyBatis-Plus 开箱整合

上游应用引入本模块并装配数据源后 SHALL 可直接使用 MyBatis-Plus 的 `BaseMapper` 能力；Mapper 扫描包可通过配置提供，未配置时不强制扫描。

#### Scenario: BaseMapper 增删改查

- **WHEN** 上游定义继承 `BaseMapper` 的 Mapper 并执行 insert 与 selectById
- **THEN** 数据成功写入并可按主键读回

#### Scenario: 配置扫描包

- **WHEN** 上游配置 mapper 扫描包路径后启动
- **THEN** 该包下的 Mapper 接口被注册为可用 Bean

### Requirement: 分页插件默认装配

本模块 SHALL 默认装配 MyBatis-Plus 分页插件；上游可通过配置关闭。

#### Scenario: 分页查询

- **WHEN** 上游使用 MyBatis-Plus 分页查询且结果超过每页数量
- **THEN** 返回的页对象包含正确的总记录数与当前页数据

#### Scenario: 关闭分页插件

- **WHEN** 上游配置关闭分页插件后启动
- **THEN** 分页插件 Bean 不再装配

### Requirement: 不引入 JPA

本模块 MUST NOT 引入或装配 JPA（`spring-boot-starter-data-jpa` 及其传递依赖不得出现在本模块依赖树中）。

#### Scenario: 依赖树无 JPA

- **WHEN** 解析本模块的依赖树
- **THEN** 不存在 `spring-boot-starter-data-jpa` 或 `hibernate-core` 构件

### Requirement: Flyway 迁移整合

上游应用引入本模块且存在迁移脚本时 SHALL 在应用启动阶段自动执行数据库迁移；迁移行为 SHALL 由 Spring Boot 官方键 `spring.flyway.*` 控制（enabled / locations / baseline-on-migrate 等），脚本路径 MUST 可配置且默认 `classpath:db/migration`，上游 MUST 可通过官方键关闭迁移；本能力 MUST NOT 要求上游使用自定义键控制迁移。

#### Scenario: 启动自动迁移

- **WHEN** classpath 默认路径存在 `V1__` 迁移脚本后启动应用
- **THEN** 迁移自动执行，目标表结构生效

#### Scenario: 自定义脚本路径

- **WHEN** 上游配置 `spring.flyway.locations` 指向自定义目录且该目录存在脚本
- **THEN** 迁移从自定义路径执行

#### Scenario: 关闭迁移

- **WHEN** 上游配置 `spring.flyway.enabled=false` 后启动
- **THEN** 不执行任何迁移且应用正常启动

### Requirement: 与统一数据源联动

ORM 与迁移 MUST 使用 database-access 能力装配的同一 `DataSource`，上游无需重复配置数据源。

#### Scenario: 单一数据源协同

- **WHEN** 上游同时使用 ORM 与迁移能力
- **THEN** 两者操作到达同一个数据库（同一 DataSource 实例）
