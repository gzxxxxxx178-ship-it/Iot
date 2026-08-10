# MATLAB SIL — 红壤丘陵稻田软件在环仿真客户端

## 架构边界

本目录是独立的 MATLAB 软件在环 (Software-in-the-Loop, SIL) 仿真客户端，按固定步长生成红壤丘陵稻田水位、流量、电导率 (EC)、土壤含水率和降雨的合成动态。

**边界说明：**
- MATLAB 仿真仅生成传感器遥测数据并上传至 Java 后端，不包含报警判断逻辑。报警规则由 Java 后端独立执行，报警后产生的 `STOP_IRRIGATION` / `STOP_FERTILIZER` / `STOP_ALL` 命令由 MATLAB 轮询并应用到仿真状态。
- 本简化模型基于水量平衡和简化 EC 混合/稀释动态，**不代表田间实测数据或高保真作物模型**，不可用于施肥处方或生产决策。
- 通信使用 Java 后端 `/api/simulation/*` REST 接口，Bearer Token 认证。不使用 MQTT，不连接真实 ESP 设备。
- 所有凭据通过函数运行时参数注入，不在任何文件中硬编码 Token、用户名、密码或生产地址。

```
matlab/sil/
├── README.md                        # 本文件
├── runSil.m                         # 主仿真循环入口
├── createSilConfig.m                # 配置结构体工厂
├── model/
│   ├── initializePaddyState.m       # 稻田状态初始化
│   └── stepPaddyModel.m             # 一步水量平衡 + EC混合模型
├── scenarios/
│   └── createScenario.m             # 六种仿真场景工厂
├── transport/
│   ├── buildTelemetryPayload.m      # 构建遥测上传JSON
│   ├── postTelemetry.m              # HTTP POST 遥测
│   ├── getPendingCommands.m         # HTTP GET 待处理命令
│   └── postCommandFeedback.m        # HTTP POST 命令反馈
├── contracts/
│   ├── telemetry-example.json       # 遥测JSON范例
│   └── command-example.json         # 命令响应JSON范例
└── tests/
    └── TestSilModel.m               # matlab.unittest 单元测试
```

## 前置要求

| 组件 | 版本/说明 |
|------|-----------|
| MATLAB | R2021b 或更高 (需支持 `webwrite`/`webread`/`weboptions`) |
| 工具箱 | 无特殊工具箱依赖。`matlab.unittest` 为标准内置包 |
| Java 后端 | 已启动的 Spring Boot 后端 (`http://localhost:8080` 或可访问地址) |
| 前端 (可选) | 用于创建仿真规则和查看报警 (推荐) |

## 模型假设

### 水量平衡模型

```
水位(next) = 水位(current) + 入流(mm) + 降雨(mm) - 入渗(mm) - 蒸散(mm) - 排水(mm)
```

- 1 L/m² = 1 mm（面积单位校正）
- 入渗速率：2 mm/hr（演示用可配置假设，未经本项目田间标定）
- 蒸散速率：3 mm/hr（演示用可配置假设，未经本项目田间标定）
- 排水：与当前水位成正比（排水系数 0.02 hr⁻¹）
- 稻田面积：100 m²（示范用小型试验田）

### EC 动态（简化混合/稀释模型）

- EC 质量平衡：进水 EC × 进水体积 + 田面水 EC × 当前水量 - 出水 EC × 出水体积
- **免责声明**：本 EC 模型仅为演示性使用，假设完全混合和均匀稀释，不考虑离子交换、土壤吸附、植物吸收、温度影响或施肥事件。**不可用于施肥处方或农业决策。**

### 约束条件

所有指标钳制到 Java 合法范围：

| 指标 | 范围 | 单位 |
|------|------|------|
| `waterLevelMm` | 0 – 500 | mm |
| `flowRateLMin` | 0 – 1000 | L/min |
| `ecMsCm` | 0 – 20 | mS/cm |
| `soilMoisturePct` | 0 – 100 | % |
| `rainfallMm` | 0 – 500 | mm |

## 启动步骤

### 1. 启动 Java 后端

```bash
cd java && ./mvnw -Dspring-boot.run.profiles=dev spring-boot:run
# 后端运行在 http://localhost:8080
```

### 2. （推荐）启动 Vue 前端

```bash
cd vue/IoT && npm run dev
# 前端运行在 http://localhost:5173
```

### 3. 登录获取 Bearer Token

通过 `/api/auth/login` 的响应获取 Token，或在浏览器开发者工具中查看当前登录会话。Token 只在 MATLAB 运行时注入，不得写入脚本、示例 JSON 或提交记录。

### 4. 在 MATLAB 中运行仿真

```matlab
% 切换到 sil 目录
cd /path/to/matlab/sil

% 添加所有子目录到路径
addpath(genpath('.'));

% --- 方式A: 联网运行 (需要有效token和运行中的后端) ---
cfg = createSilConfig('http://localhost:8080', 'YOUR_BEARER_TOKEN_HERE');
res = runSil(cfg, 'normal');

% --- 方式B: dry-run (无需后端, 仅生成payload) ---
cfg = createSilConfig('http://localhost:8080', 'dummy-token-for-dryrun');
cfg.dryRun = true;
cfg.steps = 10;
res = runSil(cfg, 'high_water');

% 查看dry-run结果
disp(res.records);           % 所有步数的记录
plot([res.records.waterLevelMm]);  % 水位变化曲线
```

### 5. Token 注入方法

Bearer Token **必须**作为运行时参数传入 `createSilConfig()`，不得硬编码在任何 `.m` 文件中：

```matlab
% 推荐: 从MATLAB输入对话框获取
token = inputdlg('请输入Bearer Token:', '认证', [1 60]);
cfg = createSilConfig('http://localhost:8080', token{1});

% 或: 通过环境变量 (Linux/macOS)
% export IOT_BEARER_TOKEN="eyJhbGci..."
[~, token] = system('echo $IOT_BEARER_TOKEN');
cfg = createSilConfig('http://localhost:8080', strtrim(token));
```

## 场景说明

| 场景 | 代码 | 描述 |
|------|------|------|
| 正常灌溉 | `normal` | 泵/阀按正常周期运行，偶发小雨，标准 EC |
| 低水位 | `low_water` | 进水流量减半，长关闭周期，高蒸散 |
| 高水位 | `high_water` | 从 260 mm 越限初值启动，并叠加 8 mm/步的合成故障脉冲；不代表真实降雨过程 |
| 高电导率 | `high_ec` | 从 3.5 mS/cm 越限初值启动，进水 EC 按演示倍率放大 |
| 堵塞水流 | `blocked_flow` | 泵阀开启但流量降为 2%，模拟管道堵塞 |
| 阀门泄漏 | `valve_leak` | 阀门"关闭"状态下仍有 5 L/min 残余流量 |

**注意**：场景仅影响扰动参数和模拟传感值。Java 后端报警规则不读取场景名称，所有报警判断基于遥测指标阈值独立执行。

## 建议的 6 条演示规则

在前端“仿真验证”页面创建以下规则。建议先绑定 `SIM-PADDY-001`，避免其他仿真设备意外命中；阈值仅用于功能演示，不能解释为农艺控制阈值。

| # | 规则名称 | 指标/运算符 | 触发阈值 | 恢复阈值 | 防抖 | 等级 | 动作 |
|---|----------|-------------|----------|----------|------|------|------|
| 1 | 高水位演示 | `waterLevelMm` / `gt` | 250.0 | 240.0 | 2 | WARN | `STOP_IRRIGATION` |
| 2 | 极高水位演示 | `waterLevelMm` / `gt` | 400.0 | 380.0 | 2 | CRITICAL | `STOP_ALL` |
| 3 | 低水位演示 | `waterLevelMm` / `lt` | 10.0 | 20.0 | 2 | WARN | `NOTIFY` |
| 4 | 高 EC 演示 | `ecMsCm` / `gt` | 3.0 | 2.5 | 2 | WARN | `STOP_FERTILIZER` |
| 5 | 低含水率演示 | `soilMoisturePct` / `lt` | 20.0 | 25.0 | 2 | WARN | `NOTIFY` |
| 6 | 低流量演示 | `flowRateLMin` / `lt` | 5.0 | 10.0 | 2 | WARN | `NOTIFY` |

当前规则引擎是单指标阈值判断。`blocked_flow` 可演示低流量告警，但不能证明“泵已开启且流量异常”的复合因果；`valve_leak` 同样只能按流量阈值演示，不能判断阀门状态与流量是否矛盾。若用于论文验证，应另加多变量状态一致性规则。

## dry-run 示例

```matlab
% 无后端情况下验证所有场景的payload输出
scenarioNames = {'normal', 'low_water', 'high_water', 'high_ec', 'blocked_flow', 'valve_leak'};

for i = 1:numel(scenarioNames)
    cfg = createSilConfig('http://localhost:8080', 'dummy');
    cfg.dryRun = true;
    cfg.steps = 10;
    cfg.realtimePauseSeconds = 0;
    cfg.randomSeed = 20260810 + i;

    fprintf('\n=== 场景: %s ===\n', scenarioNames{i});
    res = runSil(cfg, scenarioNames{i});
    fprintf('  最终水位: %.1f mm, 最终EC: %.2f mS/cm\n', ...
        res.summary.finalWaterLevelMm, res.summary.finalEcMsCm);
end
```

## MATLAB 测试命令

```matlab
% 切换到 sil 目录
cd /path/to/matlab/sil
addpath(genpath('.'));

% 运行全部测试
results = runtests('tests/TestSilModel.m');

% 显示测试结果
disp(results);

% 运行单个测试
results = runtests('tests/TestSilModel.m', 'ProcedureName', 'testReproducibility');

% 运行参数化测试 (六个场景)
results = runtests('tests/TestSilModel.m', 'ParameterName', 'scenarioName');
```

本机已使用 `/Applications/MATLAB_R2025a.app/bin/matlab` 实际执行上述测试，结果为 `16 Passed, 0 Failed, 0 Incomplete`。

## 已完成的隔离连通性验证

2026-08-10 使用 H2 内存数据库、MQTT/Redis 替身和本地端口 `18080` 完成一次真实 HTTP 闭环，测试实例没有连接开发数据库或真实 Broker：

1. 注册临时测试用户并创建高水位规则：连续 2 次大于 250 mm，动作 `STOP_IRRIGATION`。
2. MATLAB R2025a 运行 `high_water` 场景 5 步，成功上传 5 条遥测。
3. Java 后端生成 1 条报警和 1 条仿真停止命令。
4. MATLAB 轮询命令、锁存停止灌溉状态并提交 `SUCCESS` 反馈。
5. 后端查询结果：`sourceType=SIMULATION`、`scenarioCode=high_water`、报警数 1、待处理命令数 0。

测试专用后端启动器位于 `java/src/test/java/com/ruoyi/iotsystem/SilConnectivityApplication.java`，只应从测试 classpath 启动。

### 本机 MySQL 持久化验证

2026-08-10 已将 `V2__simulation_sil_schema.sql` 应用于本机 `Iot` 数据库，并使用独立设备号 `SIM-MYSQL-001` 完成真实持久化验证：

- MATLAB R2025a 上传 5 条 `high_water` 遥测；
- MySQL 持久化 1 条仿真规则、1 条报警和 1 条命令；
- MATLAB 执行 `STOP_IRRIGATION` 后，最后一条遥测的 `pumpOn=false`；
- 命令反馈后待处理命令数为 0；
- 测试后端停止后重新查询 MySQL，以上记录仍然存在。

这组记录归属于数据库中已有用户，不写入真实设备表，也不通过 MQTT 下发命令。前端使用同一用户登录后，可在“仿真验证”页面查询 `SIM-MYSQL-001`。

## 竞赛真实性表述

本 MATLAB SIL 客户端中的所有仿真数据均为**基于简化水量平衡和 EC 混合模型的合成数据**：

- 水位、流量、EC、土壤含水率和降雨量基于数学模型计算，**不代表任何田间实测值**。
- EC 模型仅为演示性混合/稀释动态，**不可用于施肥处方或农作物管理决策**。
- 生长阶段 (`growthStage`) 仅按仿真步数简单划分，**不代表真实作物物候**。
- 所有仿真输出应明确标记为 `sourceType: SIMULATION`（由 Java 后端自动设置），以与真实 ESP 设备数据区分。
- 报警规则配置和触发仅用于演示仿真-后端-前端闭环工作流，**不作为真实灌溉控制决策依据**。

## 尚未验证事项

- [ ] 尚未在远程 TiDB/生产部署环境验证；本机 MySQL 9.6.0 持久化闭环已通过。
- [ ] 尚未执行浏览器自动化测试；Vue API 契约和生产构建已通过，页面人工交互仍需验证。
- [ ] 长时间运行稳定性（> 10,000 步）未验证。
- [ ] 并发多个 MATLAB 实例（多设备仿真）未测试。

## 与 Java 后端契约一致性

| Java 字段 | MATLAB payload 字段 | 类型 | 来源 |
|-----------|---------------------|------|------|
| `sampleId` | `sampleId` | string(1-64) | 每次会话 UUID 前缀 + 自增序号 |
| `deviceId` | `deviceId` | string(1-64) | 配置注入 |
| `occurredAt` | `occurredAt` | ISO-8601 UTC | `datetime('now','UTC')` |
| `growthStage` | `growthStage` | string | MATLAB 阶段判定 |
| `scenarioCode` | `scenarioCode` | string | 场景工厂名 |
| `waterLevelMm` | `waterLevelMm` | double | 模型计算 |
| `flowRateLMin` | `flowRateLMin` | double | 模型计算 |
| `ecMsCm` | `ecMsCm` | double | 模型计算 |
| `soilMoisturePct` | `soilMoisturePct` | double | 模型计算 |
| `rainfallMm` | `rainfallMm` | double | 随机扰动 |
| `pumpOn` | `pumpOn` | boolean | 模型状态 |
| `irrigationValveOpen` | `irrigationValveOpen` | boolean | 模型状态 |
| `fertilizerPumpOn` | `fertilizerPumpOn` | boolean | 模型状态 |
| `sourceType` | *不提交* | - | Java 服务端设 "SIMULATION" |
| `ownerUsername` | *不提交* | - | Java 服务端注入 |
| `alarm` 相关字段 | *不提交* | - | Java 独立判断 |
