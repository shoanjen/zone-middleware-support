# Spec Delta

## Purpose

Flyway 迁移控制键从 zone 自定义键迁移到 Spring Boot 官方键 `spring.flyway.*`；MyBatis-Plus 整合与分页插件的行为键不变。

## MODIFIED Requirements

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
