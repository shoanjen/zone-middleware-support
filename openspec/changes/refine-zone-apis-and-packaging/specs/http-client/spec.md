# Spec Delta

## REMOVED Requirements

### Requirement: 熔断保护

**Reason**: 自研熔断为单实例内存态（无集群共享、无规则化配置、无可视化），与 Sentinel / resilience4j 等专业熔断框架职责重叠；维护两套熔断语义增加上游认知负担，专业场景应由专业框架承接。

**Migration**: 需要熔断的上游应用接入 Alibaba Sentinel（推荐）或 resilience4j，在调用 `ZoneHttpTemplate` 的外层以资源名 + 熔断规则承接（模板自身保留可配置重试）；`zone.middleware.http.circuit-breaker.*` 配置项已删除（宽松绑定下未知配置项被忽略，不影响启动）；`SYS-3002 CIRCUIT_OPEN` 错误码与 `ZoneHttpTemplate.circuitBreakerState()` 方法同步移除。
