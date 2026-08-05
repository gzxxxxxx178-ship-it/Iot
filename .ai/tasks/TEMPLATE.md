# Claude 实现任务书

- **状态**：`READY`
- **任务编号**：`{{TASK_ID}}`
- **任务名称**：`{{TASK_NAME}}`

## 目标结果

{{用可验收的一段话说明最终行为，不只描述修改动作。}}

## 已确认背景

- {{只列完成任务必需的事实、根因和现有约束。}}

## 允许修改范围

- `{{PATH_OR_GLOB}}`

## 禁止范围

- 不修改本任务未列出的业务模块。
- 不修改 `AGENTS.md`、任何 `CLAUDE.md`、`.claude/`、本任务书或 worker 脚本。
- 不读取或输出真实凭据，不执行 Git 写操作、推送、部署、SSH、数据库写入或硬件上传。

## 实现要求

1. {{REQUIREMENT}}
2. {{REQUIREMENT}}

## 验收标准

- [ ] {{OBSERVABLE_RESULT}}
- [ ] {{REGRESSION_REQUIREMENT}}
- [ ] 没有任务范围外修改和调试残留。

## 必须执行的验证

```bash
{{TARGETED_TEST_COMMAND}}
{{BUILD_OR_STATIC_CHECK_COMMAND}}
```

## Codex 修订意见

首次执行保持为空；审查不通过时由 Codex 在此列出可验证的修订项。
