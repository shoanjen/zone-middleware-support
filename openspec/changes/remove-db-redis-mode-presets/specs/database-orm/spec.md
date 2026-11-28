# Spec Delta

## Purpose

database 模块键面清零：`zone.middleware.db.mybatis.pagination-enabled` 与 `mapper-package` 两个 zone 键删除——分页拦截器默认装配且无开关键（上游自定义 `MybatisPlusInterceptor` Bean 即接管），Mapper 扫描回归官方行为（主应用包下 `@Mapper` 自动扫描 / 上游 `@MapperScan` 声明）。

## MODIFIED Requirements

### Requirement: MyBatis-Plus 开箱整合

上游应用引入本模块并装配数据源后 SHALL 可直接使用 MyBatis-Plus 的 `BaseMapper` 能力；Mapper 扫描 SHALL 遵循官方行为——主应用包（自动配置包）下标注 `@Mapper` 的接口由官方 MP 自动装配扫描注册，扫描包之外的场景由上游以标准 `@MapperScan` 声明；`zone.middleware.db.*` MUST NOT 提供扫描包配置键。

#### Scenario: BaseMapper 增删改查

- **WHEN** 上游定义继承 `BaseMapper` 的 Mapper 并执行 insert 与 selectById
- **THEN** 数据成功写入并可按主键读回

#### Scenario: 配置扫描包

- **WHEN** 上游 Mapper 位于主应用包之外，在配置类上以标准 `@MapperScan("...")` 声明扫描包
- **THEN** 该包下的 Mapper 接口被注册为可用 Bean（`zone.middleware.db.*` 已无扫描键，不参与扫描）

#### Scenario: 主包自动扫描

- **WHEN** 上游 Mapper 位于主应用包下且标注 `@Mapper`
- **THEN** 官方 MP 自动装配扫描注册为可用 Bean，无需任何配置

### Requirement: 分页插件默认装配

本模块 SHALL 默认装配 MyBatis-Plus 分页拦截器（方言运行期按连接自动识别），且 MUST NOT 提供开关配置键（不使用分页的上游零影响）；上游声明自定义 `MybatisPlusInterceptor` Bean 时 MUST 优先于本模块默认装配。

#### Scenario: 分页查询

- **WHEN** 上游使用 MyBatis-Plus 分页查询且结果超过每页数量
- **THEN** 返回的页对象包含正确的总记录数与当前页数据

#### Scenario: 关闭分页插件

- **WHEN** 上游声明自定义 `MybatisPlusInterceptor` Bean（`@ConditionalOnMissingBean` 让位语义）
- **THEN** 本模块默认分页拦截器不再装配，自定义 Bean 生效

#### Scenario: 默认装配无开关

- **WHEN** 上游不做任何配置直接启动
- **THEN** 分页拦截器默认装配；`zone.middleware.db.mybatis.pagination-enabled` 键不存在
