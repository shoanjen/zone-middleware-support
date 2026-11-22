# Spec Delta

## Purpose

为 ZoneRedisTemplate 补充计数、过期与简单互斥原语（incr/incrBy/ttl/expire/setIfAbsent），覆盖计数器、TTL 管理与简易锁三类高频场景，值序列化语义与其余方法一致。

## ADDED Requirements

### Requirement: 计数与过期原语

SHALL 提供计数原语 `incr`（自增 1）、`incrBy`（自增指定步长）与过期原语 `ttl`（读取剩余存活时间，秒，-1 表示不过期、-2 表示键不存在）、`expire`（设置存活时间）；计数键的值 MUST 为整数字符串语义，与既有 fastjson2 值序列化体系共存。

#### Scenario: 计数自增

- **WHEN** 对同一键连续 incr 三次后读取
- **THEN** 结果为 3，且 incrBy 指定步长后在原值基础上累加

#### Scenario: TTL 读取与续期

- **WHEN** 对带过期时间的键调用 ttl 后调用 expire 续期
- **THEN** ttl 返回剩余秒数，expire 后剩余时间被更新；对不存在的键 ttl 返回 -2

### Requirement: 简单互斥原语

SHALL 提供 `setIfAbsent`（键不存在时写入并返回 true，已存在返回 false，支持过期时间）作为简易互斥/防重入原语。

#### Scenario: 抢占成功

- **WHEN** 对不存在的键以 5s 过期时间调用 setIfAbsent
- **THEN** 返回 true，键被写入且带过期时间

#### Scenario: 抢占失败

- **WHEN** 对已存在的键调用 setIfAbsent
- **THEN** 返回 false，原值与过期时间不被覆盖
