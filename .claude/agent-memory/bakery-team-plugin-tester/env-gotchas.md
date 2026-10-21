---
name: env-gotchas
description: Tooling quirks hit while testing CookieCore on Windows.
metadata:
  type: reference
---
Verified 2026-10-21.
- `mise` is not on PATH in the Bash tool: `export PATH="$PATH:/c/Users/Luuk/AppData/Local/Microsoft/WinGet/Packages/jdx.mise_Microsoft.Winget.Source_8wekyb3d8bbwe/mise/bin"`. `mise trust` printed "No untrusted config files" in the worktree.
- The tester worktree is NOT pre-created by the harness: cwd was the main checkout on feature/core-foundation. Create it: `git worktree add -b core-foundation/plugin-tester .claude/worktrees/plugin-tester feature/core-foundation` (`.claude/worktrees/` is gitignored).
- Bash tool: a heredoc containing a `{a,b}` brace expansion plus quotes failed ("unexpected EOF"); use the Write tool for files. Python string replace with `\n` inside a Python heredoc turns into real newlines: use the Edit tool for Java string literals containing `\n`.
- A `rm -rf` triggers the Fact-Forcing Gate hook; use fresh scratch dirs (mut1, mut2, ...) instead of deleting.
- `mise run ownership-check` fails in CookieCore (no `.claude/hooks/ownership_lib.py`). Manual run: copy CookieClicker's `.claude/hooks/ownership_lib.py` + CookieCore's `ownership.json` to a scratch dir and `CLAUDE_PROJECT_DIR=<dir> python tools/ownership/check.py <branch> --base <base>`.
- Git on Windows warns "LF will be replaced by CRLF" on commit: harmless.
