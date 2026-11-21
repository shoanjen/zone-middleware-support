# Spec Delta

## Purpose

为上游应用装配数据源与连接池，按本地/云端形态提供合理的连接默认值，并让连接信息与池参数完全由上游应用配置提供。

## ADDED Requirements

### Requirement: 数据库部署模式声明

上游应用 SHALL 通过 `zone.middleware.db.mode` 声明部署形态，取值 `LOCAL`（默认）或 `CLOUD`；应用启动时 MUST 在日志中输出当前生效的部署模式，非法取值 MUST 导致启动失败。

#### Scenario: 声明云端模式

- **WHEN** 上游配置 `mode: CLOUD`
- **THEN** 数据源装配套用云端预设，启动日志输出云端模式标识

#### Scenario: 默认本地模式

- **WHEN** 上游未配置 mode
- **THEN** 以 LOCAL 模式装配数据源

### Requirement: 云端预设与逐项覆盖

CLOUD 模式 SHALL 自动套用更保守的预设值（更长的连接获取超时、开启连接保活、关闭开发期泄漏检测）；LOCAL 模式 SHALL 默认开启连接泄漏检测便于开发排查；所有预设值 MUST 可被上游配置逐项覆盖。

#### Scenario: 本地开启泄漏检测

- **WHEN** LOCAL 模式装配 HikariCP 且未显式配置泄漏检测阈值
- **THEN** 泄漏检测阈值为预设的开发期值

#### Scenario: 云端覆盖超时

- **WHEN** CLOUD 模式下上游显式配置连接获取超时为自定义值
- **THEN** 生效值为自定义值而非云端预设

### Requirement: 连接池类型切换

上游应用 SHALL 通过 `zone.middleware.db.pool-type` 选择连接池，取值 `HIKARI`（默认）或 `DRUID`；按选择装配对应的 `DataSource`；未知取值 MUST 导致启动失败并给出明确错误信息。

#### Scenario: 默认装配 HikariCP

- **WHEN** 上游仅提供连接信息未配置 pool-type
- **THEN** 装配的 DataSource 底层为 HikariCP 连接池

#### Scenario: 切换 Druid

- **WHEN** 上游配置 `pool-type: DRUID`
- **THEN** 装配的 DataSource 底层为 Druid 连接池

#### Scenario: 未知连接池类型

- **WHEN** 上游配置 pool-type 为不识别的值
- **THEN** 应用启动失败，错误信息包含非法值与合法取值提示

### Requirement: 上游自定义连接配置

连接 URL、用户名、密码、驱动类及池参数（最大连接数、最小空闲等）SHALL 由上游应用通过 `zone.middleware.db.*` 配置提供；连接信息缺失 MUST 导致启动失败并给出明确错误信息。

#### Scenario: 完整配置装配

- **WHEN** 上游配置 URL、用户名、密码与池参数后装配
- **THEN** 连接池按配置值初始化并可正常获取连接

#### Scenario: 缺少连接 URL

- **WHEN** 上游未配置数据库 URL 即启动
- **THEN** 启动失败，错误信息提示缺少 URL 配置

### Requirement: 与 Spring 事务协同

本能力装配的 `DataSource` MUST 可直接参与 Spring 声明式事务：上游使用 `@Transactional` 的方法在异常时回滚数据库操作。

#### Scenario: 事务回滚

- **WHEN** 上游事务方法内写入数据后抛出异常
- **THEN** 该方法内的数据库写入被回滚
