---
name: env-gotchas
description: Tooling quirks hit while testing CookieCore on Windows.
metadata:
  type: reference
---
Verified 2026-10-26 (replaces the 2026-10-21 notes where they changed).
- `mise` works again (2026.10.12). In the Bash tool use `mise exec -- mvn -B -q test` or `mise run check` / `mise run test`. Earlier notes about "mise blocked by Application Control" are stale. Fallback if blocked again: JDK `C:\Users\Luuk\AppData\Local\mise\installs\java\temurin-25.0.4+101.0.LTS`, Maven `...\maven\3.9.16\apache-maven-3.9.16\bin\mvn`.
- `mise run check` has no `check-quiet` task here, and no `validate-assets` / `sound-check` / `balance-check` tasks (no assets, no balance). Only `build`, `test`, `check`, `team`, `ownership-check`.
- The tester worktree is not pre-created: `git worktree add .claude/worktrees/<name> -b <branch> <feature-branch>` (`.claude/worktrees/` is gitignored).
- Bash tool: a command containing `rm -rf` triggers the Fact-Forcing Gate hook and is denied. Do not delete `target/` by hand; `mvn clean` (inside `mise run check`) works. Surefire does not delete reports of renamed or removed test classes: check the report file times before counting tests.
- Bash tool: heredocs with backticks or apostrophes in Java/markdown text break ("unexpected EOF"); use the Write tool.
- Python string replace with `\n` inside a Python heredoc turns into real newlines: use the Edit tool for Java string literals containing `\n`.
- `mise run ownership-check` fails in CookieCore (no `.claude/hooks/ownership_lib.py`). Manual run: copy CookieClicker's `.claude/hooks/ownership_lib.py` + CookieCore's `ownership.json` to a scratch dir and `CLAUDE_PROJECT_DIR=<dir> python tools/ownership/check.py <branch> --base <base>`.
- Git on Windows warns "LF will be replaced by CRLF" on commit: harmless.
