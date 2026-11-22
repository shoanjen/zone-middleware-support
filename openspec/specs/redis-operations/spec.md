# redis-operations Specification

## Purpose

让上游应用以最少配置完成 Redis 字符串、哈希、列表、集合、有序集合的读写与批量操作，并显式声明本地/云端部署形态以获得合理的连接默认值。

## Requirements

### Requirement: 部署模式声明

上游应用 SHALL 通过 `zone.middleware.redis.mode` 声明部署形态，取值 `LOCAL`（默认）或 `CLOUD`；非法取值 MUST 导致启动失败并给出明确错误信息；应用启动时 MUST 在日志中输出当前生效的部署模式。

#### Scenario: 默认本地模式

- **WHEN** 上游未配置 mode
- **THEN** 以 LOCAL 模式装配连接，启动日志输出本地模式标识

#### Scenario: 声明云端模式

- **WHEN** 上游配置 `mode: CLOUD`
- **THEN** 连接装配套用云端预设，启动日志输出云端模式标识

#### Scenario: 非法模式值

- **WHEN** 上游配置 mode 为不识别的值
- **THEN** 应用启动失败，错误信息包含非法值与合法取值提示

### Requirement: 云端预设与逐项覆盖

CLOUD 模式 SHALL 自动套用比 LOCAL 更保守的预设值（更长的连接超时与命令超时、开启 TCP 保活）；预设值 SHALL 以 Spring Boot 官方键为承载（`spring.data.redis.connect-timeout` / `spring.data.redis.timeout` / `spring.data.redis.lettuce.pool.*` 等），且 MUST 可被上游对同一官方键的显式配置逐项覆盖，LOCAL 模式不受云端预设影响。

#### Scenario: 云端默认更保守

- **WHEN** 分别以 LOCAL 与 CLOUD 模式且均不配置超时项进行装配
- **THEN** CLOUD 的连接超时与命令超时均大于 LOCAL

#### Scenario: 覆盖云端预设

- **WHEN** CLOUD 模式下上游显式配置命令超时为自定义值
- **THEN** 生效值为自定义值而非云端预设

### Requirement: 连接配置官方键承载

Redis 连接配置（host、port、password、database、ssl、超时、连接池参数）SHALL 由上游应用通过 Spring Boot 官方键 `spring.data.redis.*` 提供；连接工厂 SHALL 由 Spring Boot 官方 Redis 自动装配构建，本能力 MUST NOT 自建连接工厂；`zone.middleware.redis.*` MUST NOT 承载任何连接配置语义。

#### Scenario: 官方键装配连接

- **WHEN** 上游通过 `spring.data.redis.host/port/database` 配置连接后启动
- **THEN** ZoneRedisTemplate 基于官方装配的连接工厂正常完成读写

#### Scenario: 连接池参数官方键生效

- **WHEN** 上游配置 `spring.data.redis.lettuce.pool.max-active` 等池参数后启动
- **THEN** 连接池按配置值初始化

### Requirement: 字符串结构操作

SHALL 提供字符串（String）结构的写入（含过期时间）、按类型读取、删除能力。

#### Scenario: 写读删

- **WHEN** 写入一个 POJO 并带 60 秒过期时间，随后读取并删除
- **THEN** 读取得到等值的 POJO，删除后读取为 null

### Requirement: 哈希结构操作

SHALL 提供哈希（Hash）结构的字段写入、字段读取、全量读取、字段删除能力。

#### Scenario: 字段级读写

- **WHEN** 写入两个字段后读取其中一个并删除它
- **THEN** 读取值与写入一致，删除后该字段不存在、另一字段不受影响

### Requirement: 列表结构操作

SHALL 提供列表（List）结构的左/右入队、区间读取、按下标修改、按值移除能力。

#### Scenario: 入队与区间读取

- **WHEN** 右侧依次入队三个元素后读取全区间
- **THEN** 返回列表与入队顺序一致且元素类型正确

### Requirement: 集合结构操作

SHALL 提供集合（Set）结构的成员添加、成员判断、全量成员读取、成员移除能力。

#### Scenario: 成员判断与移除

- **WHEN** 添加两个成员后判断成员存在性并移除其中一个
- **THEN** 判断结果分别为存在，移除后该成员不存在、另一成员保留

### Requirement: 有序集合结构操作

SHALL 提供有序集合（ZSet）结构的成员加分写入、按排名区间读取（升序/降序）、成员删除、成员分值读取能力。

#### Scenario: 按分值排序读取

- **WHEN** 以不同分值写入三个成员后按升序读取
- **THEN** 返回成员按分值从小到大排列

### Requirement: 批量查询与删除

SHALL 提供跨键批量能力：多键批量读取、多键批量删除、单键哈希多字段批量删除。

#### Scenario: 多键批量读取

- **WHEN** 写入三个键后以键集合批量读取
- **THEN** 返回结果与键一一对应，值类型正确、不存在的键对应 null

#### Scenario: 多键批量删除

- **WHEN** 写入三个键后以键集合批量删除
- **THEN** 三个键全部不存在，删除返回的计数为 3

### Requirement: 统一值序列化

所有结构的值读写 MUST 使用统一的 fastjson2 序列化：对象写入后以 JSON 字符串存储，读取时按调用方指定的目标类型还原。

#### Scenario: 对象透明读写

- **WHEN** 以 POJO 写入后用目标类型读取
- **THEN** 无需调用方做任何转换即可得到等值对象
