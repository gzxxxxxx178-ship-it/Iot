# 研究验证工作台集成说明

## 概述

研究验证工作台（Research Workbench）是智慧农业 IoT 系统的新增模块，提供MATLAB实验结果的持久化存储、BM25 RAG证据问答和Vue前端可视化展示。

## 新旧接口兼容表

| 原接口 | 状态 | 说明 |
|--------|------|------|
| `POST /api/chat` | ✅ 保持原样 | DeepSeek AI 对话，不动 |
| `GET/POST /api/simulation/**` | ✅ 保持原样 | 仿真SIL闭环，不动 |
| `POST /api/research/experiments` | 🆕 新增 | 实验run上传（幂等） |
| `GET /api/research/experiments` | 🆕 新增 | 实验列表（owner隔离） |
| `GET /api/research/experiments/{id}` | 🆕 新增 | 实验详情 |
| `GET /api/research/overview` | 🆕 新增 | 研究总览 |
| `POST /api/research/rag/query` | 🆕 新增 | BM25 RAG问答 |

## 数据表

| 表 | 用途 | 隔离方式 |
|-----|------|----------|
| `research_experiment_runs` | 实验运行元信息 | `owner_username` + 唯一runKey |
| `research_experiment_metrics` | 实验运行指标 | 通过`run_id`关联 |
| `research_rag_queries` | RAG问答记录 | `owner_username` |

所有表采用 `owner_username` 隔离，后端从 SecurityContext 获取用户名，不接受客户端传入。

## 手工演示顺序

### 1. 启动 Java 后端和 MySQL

```bash
cd java && ./mvnw -Dspring-boot.run.profiles=dev spring-boot:run
# 等待 Flyway 执行 V3 迁移创建三张新表
```

### 2. 登录获取 JWT Token

```bash
curl -c cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'
```

### 3. MATLAB 发布实验 (dry-run)

在 MATLAB 中：
```matlab
addpath('matlab/integration');
publishFrozenExperimentResults();  % 默认 dry-run，查看 payload
```

联网发布：
```matlab
publishFrozenExperimentResults(baseUrl='http://localhost:8080', ...
    authToken='<JWT_TOKEN>', dryRun=false);
```

### 4. Vue 前端：研究验证页面

访问 `http://localhost:5173/#/research`

- 查看"实验结论"区域的 MPC/SAC/RAG 三张卡片
- 在"指标对比"表中选择已上传的运行
- 在"RAG证据问答"中测试：
  - 正常查询："AWD方法的复灌阈值是多少？"
  - 控制拒答："请打开灌溉水泵"
  - 地域外推拒答："DB32标准能在江西用吗？"
  - 田间实测拒答："这个方法经过大田实测验证了吗？"
  - 盐碱地拒答："盐碱地水稻灌溉水位应该控制在多少？"

### 5. 原仿真报警闭环验证

访问 `http://localhost:5173/#/simulation`，确认原有遥测/规则/报警/命令闭环不受影响。

### 6. 截图建议

1. 研究验证页面全貌（含安全警示、三张结论卡片、指标对比表、RAG问答）
2. RAG 正常回答示例（带引用和证据卡片）
3. RAG 拒答示例（控制请求、地域外推）
4. 仿真页面对比（证明原有功能完好）
5. 数据库中三张新表记录

## 链路区别

| 链路 | 用途 | 数据来源 |
|------|------|----------|
| 研究实验结果入库链 | MATLAB冻结实验 → Java鉴权 → MySQL → Vue展示 | 人工发布的确认性实验 |
| 实时SIL遥测链 | MATLAB仿真 → `/api/simulation/telemetry` → 规则引擎 → 报警命令闭环 | 仿真运行时的实时数据 |

**两条链路互不干扰**。研究验证工作台展示的是已冻结的实验结论，不参与实时遥测和控制。

## 安全声明

1. **RAG不生成控制命令**：BM25 RAG服务仅提供知识检索和解释，不生成任何泵阀控制命令。
2. **SAC失败如实报告**：残差SAC实验状态标记为 FAILED（或 MIXED），不包装为优势。
3. **内部RAG小样本**：BM25指标基于16题test集，属于内部小样本评估，不是严格盲测，不等同于事实正确率。
4. **所有实验仅标记 SIMULATION**：不写"田间实测"或"生产验证"。
5. **不调用外部LLM**：Java RAG完全基于本地BM25检索和规则化模板回答。
