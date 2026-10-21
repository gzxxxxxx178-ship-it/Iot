# IDEA 本机开发启动

在 IDEA 重新加载 `java/pom.xml` 的 Maven 项目，运行 `IoTSystemApplication`，Active profiles 使用 `dev`；凭据继续保存在忽略的 `java/src/main/resources/application-dev.properties`。不要把真实值复制进可提交模板。

`application-dev.yml` 保存不含凭据的默认值，与旧 properties 文件合并：启用 Flyway、检查迁移校验和、禁止 clean，JPA 保持 `validate`。同名 properties 的显式配置优先；若其中曾手动设置 `spring.flyway.enabled=false`，应核对并移除该覆盖。MySQL 支持由与 Boot 管理版本一致的 `flyway-mysql` 提供。

命令行等效入口（在仓库根目录执行）：

```bash
./java/mvnw -f java/pom.xml -Dspring-boot.run.profiles=dev spring-boot:run
```

## 2026-10-21 缺表修复记录

本次 IDEA 日志已激活 `dev`，但旧私有配置没有迁移开关，因此继承主配置的 `spring.flyway.enabled=false`。JPA 首先发现缺少 `auth_refresh_sessions`；本机库还缺少微信身份表、派发时间和自动化命令关联字段。

修复按以下顺序完成：

1. 确认目标为本机 `localhost:3306/iot`，完整备份到忽略目录 `.ai/runtime/`，未读取远程数据库。
2. 还原到隔离数据库，核对 V1–V5 所需的 18 张表及列，并对原始 4,689 行的全部原始列建立指纹；这是本次基线登记的依据。
3. 显式登记该库 V5 基线，依次执行 V6–V10，再独立运行全部 JPA 实体结构校验。V10 将刷新会话的三个 CHAR 字段改为实体所需的 VARCHAR，旧迁移文件保持原样。
4. 副本校验通过后，对已备份的本机库执行同样操作，核对迁移历史与原始数据指纹；所有原有行及字段值保留。另在副本中验证已有刷新会话数据经 V10 转换后保持不变。

**V5 是本机现有库审计后的基线，不是所有旧数据库的默认值。** 开发默认关闭自动 baseline；其他未版本化旧库应先备份、核对实际结构，再决定基线或增量补齐方案。不能为了启动改成 `ddl-auto=update/create`，也不能用 repair 跳过未解释的迁移校验失败。

## 验证边界

已验证 MySQL 9.6 本机既有库从审计基线升级、重复迁移和 JPA 结构校验；配置测试使用 Boot 真实加载器验证旧 properties 与新 YAML 合并，另检查实际 MySQL 插件注册。IDEA重新加载依赖后以dev启动成功，Flyway确认版本10且无待迁移项，Tomcat监听8080，后端271项测试通过。

Flyway 8.5.13 提示其已测试支持范围到 MySQL 8.0，本机 9.6 仍有超出官方验证范围的提示；本次实测通过不等于该组合获得官方支持。此证据也不等同于空库执行全部 V1–V10、TiDB 或生产迁移验收。V1 的历史兼容语句仍需在 P5-03 单独处理和验证，禁止直接把本机操作复制到线上。

前面的 JDBC/Redis “Could not safely identify store assignment” 是扫描提示；判断失败原因应看最后一个 `Caused by`。结构正常之后，MQTT 连接失败应单独检查网络与证书，不能据此关闭数据库校验或 TLS。
