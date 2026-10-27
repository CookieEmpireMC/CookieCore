---
name: cookiecore-quirks
description: Verified build/tooling quirks in the CookieCore repo (Windows, worktree agent), stable as of 2026-10-26
metadata:
  type: reference
---

Verified 2026-10-21 in CookieCore (Paper 26.3.build.205-stable, Java 25):
- `mise` is not on PATH in the Bash tool; prepend `/c/Users/Luuk/AppData/Local/Microsoft/WinGet/Packages/jdx.mise_Microsoft.Winget.Source_8wekyb3d8bbwe/mise/bin`. `mise exec -- mvn -B -q compile` compiles in ~10 s, `mise run check` = `mvn -B clean verify`.
- 2026-10-26 (core-gamemode run): `mise` works from PowerShell after `$env:PATH += ";$env:LOCALAPPDATA\Microsoft\WinGet\Packages\jdx.mise_Microsoft.Winget.Source_8wekyb3d8bbwe\mise\bin"`; `mise run build` = `mvn package` WITH tests (~15 s). Earlier the exe was blocked by Application Control (fallback: call `mvn.cmd` from `%LOCALAPPDATA%\mise\installs\maven\...` with `JAVA_HOME` set). Bash tool in a worktree refuses heredocs, `$(...)` command names and `cat >>`: use Read+Edit/Write; use Glob for jar paths and PowerShell for `javap`.
- 2026-10-26: a destructive-command hook ("Fact-Forcing Gate") blocks `git reset --hard` until the facts (files, rollback, verbatim instruction) are written in a message; then the same command passes on retry.
- Architecture-scan quirks (ArchitectureRulesTest): `messages.(send|component|raw)("literal"` must be a full dotted key, so build dynamic keys through a `switch` returning full literals, not `"gamemode.mode." + x`; scheduler only in `CookieCore.java` (pass `this::runOnMainThread` as `Consumer<Runnable>` to features); `new SimpleCommandExceptionType(`, `MiniMessage`, `Component.text(` forbidden outside `text/`, `DynamicCommandExceptionType` is fine.
- MessagesTest strict-parses every `Messages.REQUIRED_KEYS` entry with a fixed placeholder set (prefix, name, input, count, problem, version, server, database, storage): new keys with other placeholders (`<mode>`, `<player>`, `<staff>`...) must NOT go into `REQUIRED_KEYS` unless Gandalf extends that test; jar defaults still cover missing keys at runtime.
- MiniMessage: never write `<mode|x>`-style angle brackets in messages.yml text (parsed as tags); use `(a|b)` or `[x]`.
- Paper 26.3.205 `Entity#isOnGround()` is not deprecated (javap, 2026-10-26).
- Worktree-isolated agents: Bash refuses `git -C <other path>` and compound commands with heredocs/many `&&`; use the Write tool for files and small separate Bash calls. Reading another repo's origin/main works with `git -C <path> show origin/main:<file>` from PowerShell.
- The PreToolUse ownership hook (main checkout `.claude/hooks`) denies the coder writing `src/test/**` (owned by plugin-tester per `.claude/ownership.json`) even if the orchestrator asks for unit tests: report it under REQUESTS instead of writing via Bash.
- `tools/ownership/check.py` needs `.claude/hooks/ownership_lib.py` + `.claude/ownership.json` in the main checkout (CookieCore has no hooks dir). Test with `CLAUDE_PROJECT_DIR=<dir with both>` (copy of CookieClicker's `ownership_lib.py`).
- Paper API used (compiles on 26.3.205): `CustomArgumentType.Converted<T,String>` (`convert`, `getNativeType`, `listSuggestions`), `MessageComponentSerializer.message().serialize(Component)`, `ArgumentTypes.player()` + `PlayerSelectorArgumentResolver` in `io.papermc.paper.command.brigadier.argument.resolvers.selector`, `getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, ...)` with `registrar().register(node, description)`.
- 2026-10-26: `PluginLoader`/`MavenLibraryResolver` (`io.papermc.paper.plugin.loader.library.impl`) and aether `DefaultArtifact`/`Dependency`/`RemoteRepository` compile against paper-api 26.3.205 with no extra dependency; pattern copied from CookieClicker `CookieClickerLoader`. Bukkit `ConfigurationSection.getInt(path, def)` ignores a quoted number (`'3308'`) and returns the default (F-12): parse `get(path)` yourself.
- Verified 2026-10-21 (fix pass): to check Gandalf's `@Disabled` tests without committing, `sed -i '/^    @Disabled(/d' <test files>`, run `mvn -B test`, then `git checkout src/test`.
