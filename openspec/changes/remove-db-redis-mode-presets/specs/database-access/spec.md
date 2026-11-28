# Spec Delta

## Purpose

数据源能力去除部署模式预设层：`zone.middleware.db.mode` 键与预设注入器删除，池参数完全由 Spring Boot 官方键承载（缺省回归 HikariCP / Druid 官方默认）；连接池类型切换、Druid filter 别名校验、url 缺失快速失败等行为件不变。

## REMOVED Requirements

### Requirement: 数据库部署模式声明

**Reason**: mode 的唯一价值是选择 LOCAL/CLOUD 两套预设值，预设注入器删除后无消费者；连接形态应直接以官方键配置表达。

### Requirement: 云端预设与逐项覆盖

**Reason**: 预设遮蔽官方默认值语义；池参数由上游按 `spring.datasource.hikari.*` / `spring.datasource.druid.*` 显式配置，未配置项回归官方默认。
