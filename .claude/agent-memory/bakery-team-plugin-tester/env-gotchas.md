---
name: env-gotchas
description: Tooling quirks hit while testing CookieCore on Windows.
metadata:
  type: reference
---
Verified 2026-10-26 (replaces the 2026-10-21 notes where they changed).
- `mise` works again (2026.10.12). In the Bash tool use `mise exec -- mvn -B -q test` or `mise run check` / `mise run test`. Earlier notes about "mise blocked by Application Control" are stale. Fallback if blocked again: JDK `C:\Users\Luuk\AppData\Local\mise\installs\java\temurin-25.0.4+101.0.LTS`, Maven `...\maven\3.9.16\apache-maven-3.9.16\bin\mvn`.
- `mise run check` has no `check-quiet` task here, and no `validate-assets` / `sound-check` / `balance-check` tasks (no assets, no balance). Only `build`, `test`, `check`, `team`, `ownership-check` (re-checked 2026-10-28).
- The tester worktree is not pre-created: `git worktree add .claude/worktrees/<name> -b <branch> <feature-branch>` (`.claude/worktrees/` is gitignored).
- Bash tool: a command containing `rm -rf` triggers the Fact-Forcing Gate hook and is denied. Do not delete `target/` by hand; `mvn clean` (inside `mise run check`) works. `rm -f <file>` of a throwaway test file works (2026-10-28). Surefire does not delete reports of renamed or removed test classes: check the report file times before counting tests.
- Bash tool: heredocs with backticks or apostrophes in Java/markdown text break ("unexpected EOF"); use the Write tool.
- Python string replace with `\n` inside a Python heredoc turns into real newlines: use the Edit tool for Java string literals containing `\n`.
- `mise run ownership-check` fails in CookieCore (no `.claude/hooks/ownership_lib.py`). Manual run: copy CookieClicker's `.claude/hooks/ownership_lib.py` + CookieCore's `ownership.json` to a scratch dir and `CLAUDE_PROJECT_DIR=<dir> python tools/ownership/check.py <branch> --base <base>`.
- Git on Windows warns "LF will be replaced by CRLF" on commit: harmless.
- Single class run: `mise exec -- mvn -B -q test -Dtest=<Class> -Dsurefire.failIfNoSpecifiedTests=false` (the `-Dsurefire...` flag stops the other modules' empty test sets from failing). Several classes: `-Dtest='A,B,C'`. Exit 0 with `-q` means pass. `mise run check` took 19-22 s, `mise run test` about the same.
- Bash tool blocks foreground `sleep` ("use Monitor or run_in_background"): run long checks with `timeout` in the foreground instead, or `run_in_background`.
- `mise run ownership-check <branch> --base <feature>` fails here (no `.claude/ownership.json` and no hook library in this checkout, same as F-02). Check by hand: `git diff --name-only <feature>..<branch>` must show only the owner's paths.
- `git -C C:\\dev\\...` fails in Git Bash ("cannot change to 'C:devCookieCore'"). Use `cd /c/dev/CookieCore/...` and absolute `/c/...` paths. The Read tool needs `C:/dev/...` absolute paths.
- Worktree at `.claude/worktrees/<name>` (2026-10-27/28): to review an owner's branch, run `git switch -c <owner-branch>/plugin-tester` inside the owner's worktree; the owner's branch stays intact (2026-10-28, `core-chat-join`). `mise run check` and `mise run test` both run there; `mise run test` is `mvn -B test`, `mise run check` is `mvn -B clean verify`. After a test-compile error, a stale `target/` is not the cause: check `tc.log` for the javac stack.
- mise path from Bash: `/c/Users/Luuk/AppData/Local/Microsoft/WinGet/Packages/jdx.mise_Microsoft.Winget.Source_8wekyb3d8bbwe/mise/bin/mise.exe`.
- The Fact-Forcing Gate denies any Bash command that contains `rm -rf`, even a no-op like `rm -rf $(echo)`. `mise run check` and `mise run test` were not gated in this run (they only write `target/`).
- A Python heredoc turns `\n` in a Java string into a real newline and breaks the file (happened twice, 2026-10-27). Fix it with the Edit tool.
- Surefire and the console: non-ASCII text (the messages.yml prefix glyph) prints as a replacement character in the test log. Not a bug; do not assert on console output.
- Memory on a branch: the agent memory lives in the worktree's `.claude/agent-memory/bakery-team-plugin-tester/` (2026-10-28, copied from the main checkout's copy, then updated there).
