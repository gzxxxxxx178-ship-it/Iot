#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$ROOT_DIR"

TASK_FILE="${1:-.ai/tasks/current.md}"
REPORT_DIR="$ROOT_DIR/.ai/reports"
RUNTIME_DIR="$ROOT_DIR/.ai/runtime"
RAW_REPORT="$REPORT_DIR/latest.raw.json"
TEXT_REPORT="$REPORT_DIR/latest.md"
BASELINE_REPORT="$REPORT_DIR/baseline-status.txt"
LOCK_DIR="$RUNTIME_DIR/claude-worker.lock"

# worker 只能执行由 Codex 明确标记为 READY 的任务书。
if [[ ! -f "$TASK_FILE" ]]; then
  printf '缺少任务书：%s\n请先从 .ai/tasks/TEMPLATE.md 创建任务。\n' "$TASK_FILE" >&2
  exit 1
fi

if ! grep -Eq '^-[[:space:]]+\*\*状态\*\*：`READY`' "$TASK_FILE"; then
  printf '任务书未标记为 READY，拒绝启动 Claude worker：%s\n' "$TASK_FILE" >&2
  exit 1
fi

mkdir -p "$REPORT_DIR" "$RUNTIME_DIR"
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  printf '已有受管 Claude worker 正在运行：%s\n' "$LOCK_DIR" >&2
  exit 1
fi

tmp_report="$(mktemp "$RUNTIME_DIR/claude-report.XXXXXX")"
# 无论成功或失败都释放单 worker 锁并删除临时输出。
cleanup() {
  rm -f "$tmp_report"
  rmdir "$LOCK_DIR" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

git status --short > "$BASELINE_REPORT"

prompt="你是 Codex 管理的代码实现 worker。请先读取根目录 AGENTS.md、CLAUDE.md 和任务书 ${TASK_FILE}，然后直接完成任务。严格限制在任务书允许范围内，保护任务开始前已有改动。不要修改任务书或协作规则，不要执行任何 Git 写操作、推送、部署、SSH、数据库写入、外部发布、硬件上传或凭据读取。完成后运行任务书指定的定向验证并自行检查 git diff。最终只输出一份简洁 Markdown 报告，固定包含：状态（完成/受阻/失败）、实现摘要、修改文件、实际测试命令与结果、剩余风险或阻塞。不要声称未实际运行的测试通过。"

claude_args=(
  -p
  --output-format json
  --permission-mode dontAsk
  --tools Read Edit Write Glob Grep Bash
  --settings "$ROOT_DIR/.claude/worker-settings.json"
  --max-turns "${CLAUDE_WORKER_MAX_TURNS:-40}"
  --no-chrome
)

if [[ -n "${CLAUDE_WORKER_RESUME_ID:-}" ]]; then
  claude_args+=(--resume "$CLAUDE_WORKER_RESUME_ID")
fi

if [[ -n "${CLAUDE_WORKER_MAX_BUDGET_USD:-}" ]]; then
  claude_args+=(--max-budget-usd "$CLAUDE_WORKER_MAX_BUDGET_USD")
fi

printf '启动 Claude worker：%s\n' "$TASK_FILE"
if ! claude "${claude_args[@]}" "$prompt" > "$tmp_report"; then
  cp "$tmp_report" "$REPORT_DIR/latest.failed.log"
  printf 'Claude worker 执行失败，原始输出已保存：%s\n' "$REPORT_DIR/latest.failed.log" >&2
  exit 1
fi

if ! jq -e . "$tmp_report" >/dev/null 2>&1; then
  cp "$tmp_report" "$REPORT_DIR/latest.failed.log"
  printf 'Claude worker 未返回有效 JSON，原始输出已保存：%s\n' "$REPORT_DIR/latest.failed.log" >&2
  exit 1
fi

mv "$tmp_report" "$RAW_REPORT"
jq -r '.result // "Claude worker 未提供文本报告。"' "$RAW_REPORT" > "$TEXT_REPORT"

printf 'Claude worker 已结束。\n精简报告：%s\n原始报告：%s\n' "$TEXT_REPORT" "$RAW_REPORT"
