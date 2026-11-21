# AGENTS.md · zone-middleware-support

> **本文件是子项目存根。开发（含启动任何服务）之前，必须先阅读总工作区总纲 [`../AGENTS.md`](../AGENTS.md)**，
> 遵守其中的环境说明、共享服务与端口规范；本存根与总纲冲突时，以总纲为准。

## 开发前检查（强制）

1. **阅读总纲** `../AGENTS.md`：第二节（环境）→ 第三节（共享服务）→ 第四节（端口规则）→ 第五节（项目总表）。
2. **环境检查**：

   ```bash
   java -version; mvn -v; python3 --version; node -v; pnpm -v
   mysql -uroot -prootroot -e "SELECT VERSION();"   # MySQL 8.4.5，root/rootroot
   redis-cli ping && docker ps                      # Redis 8.8.0 无密码；RocketMQ 等容器
   ```

3. **端口双重检查（仅当本项目存在监听端口的启动场景时执行；非服务型项目——控制台 / 共享库 / 纯移动端 / CLI / 研究 PoC，速览端口为「—/无端口」——跳过本步，日后新增监听产物再按此检查）**：
   - ① **实时占用**：逐个执行 `lsof -nP -iTCP:<端口> -sTCP:LISTEN`，有输出 = 被占用；
   - ② **登记预留**：对照总纲第五节总表，避开**其他子项目已登记**的端口（含推荐端口与推荐端口汇总）——`lsof` 此刻空闲 ≠ 可用，那是别人登记了还没启动的端口。
4. ①② 任一命中，或撞系统保留端口（3306/33060/6379/7890/9090/9200/9876/10909-10911/5432/11434 等）→
   按总纲 4.2 / 4.3 规则换到默认段下一个**通过双重检查**的空闲端口（前端 5200–5299 / 后端 8200–8299 / Metro 8082–8089 / 工具 4700–4799）。
5. **端口确定后立即回填**：更新总纲第五节总表 + 更新下方速览 + 总纲第十节变更记录。
   MySQL / Redis 为全局共享实例（3306 / 6379），本项目直接连接，用独立 database 名 / db index 隔离，**禁止另起实例占新端口**。
6. **需要大模型 API Key 时**：统一从工作区根 `.env` 读取（`SILICONFLOW_API_KEY` / `DEEPSEEK_API_KEY` / `ZHIPU_API_KEY` 等槽位，加载方式与变量清单见总纲第六节）；**禁止**把完整 key 复制进本项目 `.env`，若引入**新的模型厂商 / key**：先加根 `.env`（按 `<厂商>_API_KEY/_BASE_URL/_MODEL` 命名），开发完成后**必须同步登记总纲第六节（脱敏）**；引入新中间件（向量库、MQ 等）/ Docker 容器时，同步登记总纲第三节 + 第五节总表。
7. **编码约定（Java/JVM 项目）**：JSON 处理统一使用 **fastjson**（新依赖建议 fastjson2），**不使用 jackson**；存量项目已用 jackson 的保持现状、不强制迁移，详见总纲第九节「开发规范」。

## 项目速览（登记于 2026-11-11，2026-11-15 更名为 zone-middleware-support；端口权威来源：总纲第五节）

- **路径**：`zone-middleware-support/`
- **类型 / 技术栈**：Java 共享库 · Spring Boot 2.7.18 BOM（zone-middleware-common / zone-middleware：database/http/log/redis 子模块）
- **端口登记**：无（不可运行库）
- **中间件**：—
- **备注**：—
