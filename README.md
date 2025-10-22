# shoan-platform-support

Shoan 平台支撑二方库（Maven 多模块项目）。本仓库作为其他应用的二方库依赖使用，当前阶段仅创建模块与目录结构，不包含具体功能实现。

## 项目概览
- GroupId: com.shoan
- ArtifactId: shoan-platform-support
- Version: 1.0.0-SNAPSHOT
- Packaging: pom（父项目）
- JDK 要求: 21
- 包名前缀: cn.shoan

## 模块结构
- shoan-common
  - 用途：公共实体类、工具类、枚举、常量、状态码、自定义异常等
  - 包结构示例：cn.shoan.common.{entity, util, enums, constant, code, exception}
- shoan-supportor（父模块，命名规范：shoan-supportor-模块名称）
  - shoan-supportor-redis：封装 Redis 相关操作类、连接配置
    - 包结构示例：cn.shoan.supportor.redis.{config, service, util}
  - shoan-supportor-database：封装数据库连接池、连接地址等配置信息
    - 包结构示例：cn.shoan.supportor.database.{config, pool, properties}
  - shoan-supportor-log：封装项目日志规范等信息
    - 包结构示例：cn.shoan.supportor.log.{config, aspect, annotation, appender}
  - shoan-supportor-http：封装 HTTP 客户端、拦截器、配置等
    - 包结构示例：cn.shoan.supportor.http.{config, client, interceptor, util, dto}

## 目录约定
- 每个模块均包含以下标准目录：
  - src/main/java
  - src/main/resources
  - src/test/java

## 构建与安装
- 在项目根目录执行：
  - mvn clean install
- 说明：当前版本仅目录结构，构建成功后将各模块安装到本地 Maven 仓库，供其他应用引用。

## 在其他项目中使用（示例）
- 引用公共模块（示例）：
  - <dependency>
      <groupId>com.shoan</groupId>
      <artifactId>shoan-common</artifactId>
      <version>1.0.0-SNAPSHOT</version>
    </dependency>
- 引用支撑模块（示例）：
  - <dependency>
      <groupId>com.shoan</groupId>
      <artifactId>shoan-supportor-redis</artifactId>
      <version>1.0.0-SNAPSHOT</version>
    </dependency>
  - <dependency>
      <groupId>com.shoan</groupId>
      <artifactId>shoan-supportor-database</artifactId>
      <version>1.0.0-SNAPSHOT</version>
    </dependency>
  - <dependency>
      <groupId>com.shoan</groupId>
      <artifactId>shoan-supportor-log</artifactId>
      <version>1.0.0-SNAPSHOT</version>
    </dependency>
  - <dependency>
      <groupId>com.shoan</groupId>
      <artifactId>shoan-supportor-http</artifactId>
      <version>1.0.0-SNAPSHOT</version>
    </dependency>

## 注意事项
- 本项目当前阶段不包含具体功能实现，仅提供模块与目录结构。
- 如需添加实现，请在对应模块与包中按规范补充类与配置。

## 后续计划（建议）
- shoan-common：补充基础实体、统一返回体、全局异常与错误码等
- shoan-supportor-redis：连接工厂配置、序列化配置、操作封装
- shoan-supportor-database：数据源/连接池配置、事务管理、MyBatis/JPA整合
- shoan-supportor-log：统一日志格式、AOP切面、日志输出策略
- shoan-supportor-http：RestTemplate/WebClient封装、拦截器、重试与熔断策略