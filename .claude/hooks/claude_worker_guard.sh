#!/usr/bin/env bash
set -euo pipefail

# 二次校验 Claude worker 的高风险工具调用；退出码 2 会阻止本次调用并把原因返回给 Claude。
payload="$(< /dev/stdin)"
tool_name="$(jq -r '.tool_name // ""' <<<"$payload")"

# 阻止 worker 修改协作规则、任务书和自身安全配置。
if [[ "$tool_name" == "Edit" || "$tool_name" == "Write" ]]; then
  file_path="$(jq -r '.tool_input.file_path // .tool_input.path // ""' <<<"$payload")"
  case "$file_path" in
    .git/*|*/.git/*|.claude/*|*/.claude/*|.ai/tasks/*|*/.ai/tasks/*|AGENTS.md|*/AGENTS.md|CLAUDE.md|*/CLAUDE.md|scripts/claude_worker.sh|*/scripts/claude_worker.sh|docs/CODEX_CLAUDE_WORKFLOW.md|*/docs/CODEX_CLAUDE_WORKFLOW.md)
      printf 'Claude worker 不得修改协作规则、任务书、安全配置或 Git 元数据：%s\n' "$file_path" >&2
      exit 2
      ;;
  esac
fi

if [[ "$tool_name" == "Bash" ]]; then
  command_text="$(jq -r '.tool_input.command // ""' <<<"$payload")"
  command_lower="$(tr '[:upper:]' '[:lower:]' <<<"$command_text")"

  blocked_pattern='(^|[;&|[:space:]])([^;&|[:space:]]*/)?(sudo|su|ssh|scp|sftp|rsync|curl|wget|nc|ncat|telnet|ftp|rm|shred|dd|mkfs|mount|umount|kill|pkill|killall|brew|npx|mysql|redis-cli|mosquitto_pub|mosquitto_sub|docker|kubectl|terraform|ansible|eval)([;&|[:space:]]|$)|(^|[;&|[:space:]])([^;&|[:space:]]*/)?git([[:space:]][^;&|]*)?[[:space:]](add|commit|push|pull|fetch|merge|rebase|reset|clean|checkout|switch|restore|stash|tag|rm|mv)([;&|[:space:]]|$)|npm[[:space:]]+(install|uninstall|publish|login|adduser)([;&|[:space:]]|$)|arduino-cli[[:space:]]+upload([;&|[:space:]]|$)|(^|[;&|[:space:]])([^;&|[:space:]]*/)?(bash|sh|zsh)[[:space:]]+-c([;&|[:space:]]|$)|(^|[;&|[:space:]])([^;&|[:space:]]*/)?(node|python|python3|ruby|perl)[[:space:]]+(-e|-c)([;&|[:space:]]|$)|(^|[;&|[:space:]])(env|printenv|set|export|source)([;&|[:space:]]|$)|(^|[;&|[:space:]])([^;&|[:space:]]*/)?(mvn|mvnw)[^;&|]*(deploy)'

  if [[ "$command_lower" =~ $blocked_pattern ]]; then
    printf 'Claude worker 的命令命中高风险操作拦截规则；由 Codex 审核后执行：%s\n' "$command_text" >&2
    exit 2
  fi
fi

exit 0
