# Spec Delta

## Purpose

为 ZoneHttpTemplate 补充 fastjson2 `TypeReference<T>` 泛型反序列化重载，使 `ZoneResult<复杂泛型>` 场景（如 data 为对象、列表或带泛型参数）的 data 字段还原为目标类型而非退化为 JSONObject。

## ADDED Requirements

### Requirement: 泛型反序列化重载

ZoneHttpTemplate SHALL 提供 fastjson2 `TypeReference<T>` 重载：get 与 post 各包含带/不带自定义请求头两个变体（共四个重载），响应体 SHALL 按调用方声明的完整泛型类型反序列化——以 `TypeReference<ZoneResult<Order>>` 调用时返回的 data MUST 为 `Order` 实例而非 JSONObject；既有 `Class<T>` 方法签名与行为 MUST 保持不变。

#### Scenario: 泛型 data 正确还原

- **WHEN** 以 `TypeReference<ZoneResult<Order>>` 调用 post，服务端返回 data 为订单对象的 JSON
- **THEN** 返回的 ZoneResult 中 data 为 Order 类型实例，字段值与 JSON 一致

#### Scenario: 泛型列表还原

- **WHEN** 以 `TypeReference<ZoneResult<List<Order>>>` 调用 get
- **THEN** data 为 List<Order>，元素类型正确

#### Scenario: 带请求头的泛型重载

- **WHEN** 以带 headers 的 TypeReference 重载调用 get/post
- **THEN** 请求携带指定头，响应按声明的泛型类型反序列化

#### Scenario: 既有 Class 方法不受影响

- **WHEN** 上游继续使用既有 `Class<T>` 方法
- **THEN** 行为与升级前完全一致
