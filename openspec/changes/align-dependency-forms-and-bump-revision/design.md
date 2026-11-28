# Design

## Context

当前 `zone-middleware-database` 以 `ZoneDataSourceAutoConfiguration` 手工装配双池（`spring.datasource.type` 切换 HikariCP/Druid），Druid 路径含自研 filter 别名快速失败校验（`FilterManager.loadFilterConfig()` 对照）。其余模块依赖形态已全部符合「有官方 starter 用官方 starter」惯例，redis/log/http/security/web 均无此类欠账（审计结论见 proposal）。主规格 `database-access` 中「部署模式/云端预设」两条需求由已完成待归档的 `remove-db-redis-mode-presets` 负责移除，与本变更 delta 无需求交叠，归档先后互不影响。

## Goals / Non-Goals

**Goals:**
- nacos-client / dubbo-bom 版本单一源登记，形成团队版本基线
- 数据源装配所有权对齐官方：HikariCP → Boot 原生，Druid → alibaba 官方 starter；模块收敛为真空区能力（MP 分页默认装配、Flyway 官方键默认值、Druid filter 后置校验）
- 保留 Druid filter 别名快速失败的自研价值（校验时序后移）
- 以 `revision=1.0.1-SNAPSHOT` 形成新基线并同步全部文档版本串

**Non-Goals:**
- 不新建 nacos/dubbo 封装模块（下游形态未定，触发条件出现时另立变更）
- 不引入 Spring Cloud Alibaba 矩阵（SCA 2025.0.0.0 强绑 Nacos Server 3.x 生态由下游自管）
- 不做 `zone-middleware-bom` 发布物（下游免版本号诉求当前不存在，dependencyManagement 定位为库内单源 + 团队基线）
- 不保留 zone 侧 url 缺失快速失败、数据源 init 日志行、`zone-hikari`/`zone-druid` 池命名（随自研装配一并废弃，上游已确认）

## Decisions

1. **nacos/dubbo 仅登记版本，不建模块**。备选「封装 zone-middleware-nacos」被否：下游使用形态未定，且裸 `nacos-client` 3.x 是对接 Server 3.x 的最正路线（`com.alibaba.boot` 纯 Boot starter 0.2.x 线绑 2.x client 且有 Boot 3 自动刷新问题报告，不满足前提）；dubbo 侧注解驱动无封装面，`dubbo-bom` import 一份即管住 starter 全家。
2. **Druid 迁移至 `druid-spring-boot-3-starter`（上游决策，替代保守推荐「保持裸依赖」）**。驱动因素：官方 starter 开箱提供 stat-view-servlet 监控页、`filter.<name>.enabled` 细粒度键、web-stat-filter、aop-patterns 等生态能力；绑定前缀同为 `spring.datasource.druid.*` 且为超集，存量上游配置键面基本零迁移。备选「裸依赖 + 手工装配保持现状」被上游否决。
3. **HikariCP 装配彻底移交 Boot 原生（删除整类，而非仅删 Druid 路径）**。zone 手工 Hikari 仅绑 5 键，是 Boot 原生绑定的子集；若保留则形成「Hikari 归 zone、Druid 归 alibaba」的所有权不对称。`ZoneOfficialDataSourceProperties`（Hikari/Druid 两个内部绑定类）随之整体删除。
4. **切池语义 = 依赖声明**。官方 starter 经 `@ConditionalOnClass` + `@AutoConfigureBefore(DataSourceAutoConfiguration)` 生效：声明依赖即 Druid，否则 Boot 缺省 HikariCP。`spring.datasource.type` 官方键仍存在但不再承担本能力切池职责。
5. **filter 别名校验后置保留**：新增 `@ConditionalOnClass(DruidDataSource.class)` 的独立自动配置，以 `BeanPostProcessor` 在数据源 Bean 初始化完成后读取 `spring.datasource.druid.filters` 属性值并对照 `FilterManager.loadFilterConfig()` 注册表校验，未注册别名抛 `ZoneBusinessException(CONFIG_ERROR)` 启动失败——与现行为的差异仅在时序（装配后 vs 装配前），快速失败语义不变。备选「放弃校验、引导细粒度键」被否（校验源自真实踩坑，属自研价值）。
6. **归档顺序无耦合**：本 delta 仅触及「连接池类型切换（删）／上游自定义连接配置（改）／Druid filter（改）／装配所有权（增）」，与 `remove-db-redis-mode-presets` 的 mode/预设删除无交叠，任一先归档均不冲突；本文档与 delta 已按其归档后基线表述。
7. **版本号**：`nacos.client.version=3.3.0`、`dubbo.version=3.3.7`（2026-11-28 核实的当前最新稳定版）；`druid-spring-boot-3-starter` 取 1.2.20+ 线最新补丁版，apply 阶段以 Maven Central 实际解析为准钉版。

## Risks / Trade-offs

- [上游沿用 `spring.datasource.type=Druid` 但未声明 starter 依赖] → Boot 原生装配因类不存在启动失败；接入手册迁移小节明确「删 type 配置 + 声明 starter」两步操作，错误信息含类名足够定位。
- [filter 校验时序后移导致的窗口] → 校验在数据源 Bean afterInitialization 阶段执行，仍处上下文刷新期，失败同样阻断启动；无运行期窗口。
- [url 缺失快速失败丢失] → 文档以最小完整配置示例兜底；Hikari 首连接报错信息含 `jdbcUrl is required` 可定位。
- [dependencyManagement 不传递的预期差] → 手册依赖片段带显式版本号，proposal 已记录与上游确认过该语义。
- [测试改造波及] → `ZoneDataSourceConfigurationTest` 按三路重写（Boot 原生 Hikari／starter Druid 装配链／后置校验正反例）；H2 + Flyway + MP 集成测试改走 Boot 缺省 Hikari，行为不变。

## Migration Plan

全部构件为未发版 SNAPSHOT，无存量下游：分支 `feat/add-nacos-dubbo-component` 内一次落地，`mvn install` 全 reactor 验证；回滚策略为 revert 分支提交（单 commit 序列，无数据迁移面）。

## Open Questions

- `druid-spring-boot-3-starter` 的最新补丁版本号在 apply 阶段核实后钉入（仅影响版本字面量，不影响方案与任务拆分）。
