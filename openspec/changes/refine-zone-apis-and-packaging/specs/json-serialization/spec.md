# Spec Delta

## Purpose

为 Zone 生态提供统一的 JSON 序列化入口 JsonUtil，所有模块的 JSON 处理经同一工具收口，便于全局行为治理与上游接入一致性。

## ADDED Requirements

### Requirement: 统一 JSON 工具

`JsonUtil` SHALL 作为 Zone 生态 JSON 序列化的统一入口（fastjson2 底座）：提供 `toJson(Object)` 与 `fromJson(String, Class)` / `fromJson(String, TypeReference)` 方法；入参 null MUST 容错（toJson 返回 null，fromJson 对 null/空文本返回 null）而不抛异常；TypeReference 重载 MUST 支持完整泛型树反序列化（data 不退化为 JSONObject）。

#### Scenario: 序列化与反序列化往返

- **WHEN** 以 `toJson` 序列化一个 POJO 后以 `fromJson` 按原类型反序列化
- **THEN** 字段内容与原对象一致

#### Scenario: 泛型反序列化

- **WHEN** 以 `TypeReference<ZoneResult<Order>>` 声明类型反序列化嵌套泛型 JSON
- **THEN** data 还原为 Order 实例而非 JSONObject

#### Scenario: null 容错

- **WHEN** 以 null 调用 `toJson`、以 null 或空串调用 `fromJson`
- **THEN** 分别返回 null，不抛异常

#### Scenario: 模块序列化收口

- **WHEN** 检查 redis/http/security 模块的 JSON 序列化调用
- **THEN** 均经 `JsonUtil` 完成而非各自直连 fastjson2 API
