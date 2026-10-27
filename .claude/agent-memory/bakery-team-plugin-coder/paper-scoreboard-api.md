---
name: paper-scoreboard-api
description: Verified Paper 26.3.205 scoreboard/command API signatures and CookieCore architecture-test constraints met while building core-scoreboard (2026-10-26)
metadata:
  type: reference
---

Verified with javap on paper-api 26.3.build.205-stable (stable for this version), 2026-10-26:
- `Score#customName(Component)`/`customName()`, `Score#resetScore()`, `Objective#numberFormat(NumberFormat)` (`io.papermc.paper.scoreboard.numbers.NumberFormat.blank()`), `Objective#displayName(Component)`, `Player#getPing()`, `ScoreboardManager#getNewScoreboard()`.
- `Commands.register(LiteralCommandNode, String description, Collection<String> aliases)` exists (alias `/sb`).
- `BukkitScheduler#runTaskTimer(Plugin, Runnable, long, long)` returns `BukkitTask`.
- Adventure `TextReplacementConfig.builder().matchLiteral(..).replacement(Component)` + `Component#replaceText` compile; used by `text/Template` (marker `\u0001key\u0001` recipe).

CookieCore `ArchitectureRulesTest` constraints (source scan, stable until the test changes):
- `getScheduler()` only in `CookieCore.java` (pass a `TaskRunner` lambda down); `MiniMessage`, `Component.text(`, `.deserialize(` only in `text/`; `Placeholder.parsed(` only in Messages; `registerNewObjective` line must contain `Criteria.`; `api/` may import only `java.*` (ScoreboardPlaceholders breaks this on purpose); config must not import feature packages; CookieCore.java <= 200 lines.
- `Perm` defaults are asserted `op` by `PluginDescriptorTest`; `cookiecore.scoreboard.toggle` is `true` by contract.
- Shell quirk: Bash/PowerShell heredoc python commands that mention many things are sometimes refused as "too complex" in a worktree agent; use the Edit tool. PowerShell `mvn` runs hit a "Destructive command" fact-forcing hook: state files modified (target/ only), rollback, and quote the instruction, then retry. `mise exec -- mvn` via Bash hung >300 s once; direct `%LOCALAPPDATA%\mise\installs\maven\3.9.16\...\mvn.cmd -B -q -o compile` with JAVA_HOME=`...\mise\installs\java\temurin-25` works (about 1 min).
