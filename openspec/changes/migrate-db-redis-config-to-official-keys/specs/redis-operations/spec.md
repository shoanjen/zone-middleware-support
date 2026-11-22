# Spec Delta

## Purpose

Redis 连接配置契约从 zone 自定义键迁移到 Spring Boot 官方键 `spring.data.redis.*`，连接工厂改由官方自动装配；部署模式声明保持 zone 键不变，模式预设改以官方键承载；ZoneRedisTemplate 的 JSON 值语义与各结构操作能力不变。

## MODIFIED Requirements

### Requirement: 云端预设与逐项覆盖

CLOUD 模式 SHALL 自动套用比 LOCAL 更保守的预设值（更长的连接超时与命令超时、开启 TCP 保活）；预设值 SHALL 以 Spring Boot 官方键为承载（`spring.data.redis.connect-timeout` / `spring.data.redis.timeout` / `spring.data.redis.lettuce.pool.*` 等），且 MUST 可被上游对同一官方键的显式配置逐项覆盖，LOCAL 模式不受云端预设影响。

#### Scenario: 云端默认更保守

- **WHEN** 分别以 LOCAL 与 CLOUD 模式且均不配置超时项进行装配
- **THEN** CLOUD 的连接超时与命令超时均大于 LOCAL

#### Scenario: 覆盖云端预设

- **WHEN** CLOUD 模式下上游显式配置命令超时为自定义值
- **THEN** 生效值为自定义值而非云端预设

## ADDED Requirements

### Requirement: 连接配置官方键承载

Redis 连接配置（host、port、password、database、ssl、超时、连接池参数）SHALL 由上游应用通过 Spring Boot 官方键 `spring.data.redis.*` 提供；连接工厂 SHALL 由 Spring Boot 官方 Redis 自动装配构建，本能力 MUST NOT 自建连接工厂；`zone.middleware.redis.*` MUST NOT 承载任何连接配置语义。

#### Scenario: 官方键装配连接

- **WHEN** 上游通过 `spring.data.redis.host/port/database` 配置连接后启动
- **THEN** ZoneRedisTemplate 基于官方装配的连接工厂正常完成读写

#### Scenario: 连接池参数官方键生效

- **WHEN** 上游配置 `spring.data.redis.lettuce.pool.max-active` 等池参数后启动
- **THEN** 连接池按配置值初始化
