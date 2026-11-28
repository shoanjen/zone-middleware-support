# Spec Delta

## Purpose

Redis 能力去除部署模式预设层：`zone.middleware.redis.mode` 键、预设注入器与按模式派生的 TCP keepalive 装配全部删除，连接与池参数完全由 Spring Boot 官方键承载（显式配置生效，未配置即官方默认）；ZoneRedisTemplate 的 JSON 值语义与各结构操作能力不变。

## REMOVED Requirements

### Requirement: 部署模式声明

**Reason**: mode 的唯一价值是选择 LOCAL/CLOUD 两套预设值，预设注入器删除后无消费者；部署形态应直接以官方键配置表达，不再经模式间接展开。

### Requirement: 云端预设与逐项覆盖

**Reason**: 预设遮蔽官方默认值语义并带来两套默认值的记忆负担；超时/池参数由上游按官方键显式配置，未配置项回归官方默认。
