# Review: core-scoreboard

Reviewer: Gandalf (plugin-tester) · 2026-10-27 · round 1 on the coder branch `core-scoreboard/plugin-coder` (reviewed at `61fa3bb`). Round 2 re-review of the coder's fix round (`1c85bb8`, merged as `965e853`, brought into the tester branch as `7a7ca6d`): `git diff 61fa3bb..7a7ca6d -- src/main`. Tester commits: `0624e44` to `34bd8d7` (round 1), `cd4e29a`, `78597c2` (review, memory), then the round 2 tests and review.

**VERDICT: READY** (to merge into `feature/core-scoreboard`). No HIGH, MEDIUM or PERF-HIGH findings remain. M-1 (the `scoreboard.yml` reload test seam) is resolved and tested; L-1 is resolved. One LOW is still open and parked: L-4, the shipped `scoreboard.yml` Online line leaves its outer `<gray>` open, so the strict MiniMessage test is `@Disabled` until a one-line coder fix. Everything else is a follow-up.

## 1. Target version
- `pom.xml`: `paper-api 26.3.build.205-stable` (pinned, provided). `PluginDescriptorTest` passes: `api-version` and the pom pin match, nothing shaded.
- Java 25 (`mise.toml`). No legacy `ChatColor` / `§`, no `plugin.yml`, no NMS.
- APIs used exist in 26.3 (the code compiles against the pinned jar): `Score#customName(Component)`, `Objective#numberFormat(NumberFormat)`, `NumberFormat.blank()`, `Criteria.DUMMY`, `registerNewObjective(String, Criteria, Component)`, `Player#getPing()`. Not verified in game: whether `customName` renders with blank numbers (in-game check 1).

## 2. Test results
- Round 1 (coder `61fa3bb` + my tests, before the fix round): **156 run, 0 failures**. Before my changes on this branch: **137 run, 5 failures** (the five known: API rule, toggle default, three schema-version tests).
- Round 2 (after the coder's fix round and my ScoreboardConfig tests): **`mise run check` BUILD SUCCESS, 160 run, 0 failures, 0 errors, 1 skipped** (the `@Disabled` strict test, see L-4). The skipped test was run once with `-Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition`: it fails with `All tags must be explicitly closed while in strict mode ... open tags: gray`, as expected.
- Net over the baseline: 137 to 160 run (+23 new, the 5 fixed in place, 1 skipped).
- New in round 2: `ScoreboardConfigTest` 3 (+3); `ScoreboardLayoutTest` unchanged count (4 run, plus the one skipped strict test; the shipped-file test keeps the lenient "no tag left as text" check so it stays green).
- Not runnable here: `validate-assets`, `sound-check`, `balance-check` (no such tasks in this repo). `ownership-check` cannot run in CookieCore (no `.claude/ownership.json` and hook library); checked by hand: this branch touches only `src/test/**`, `docs/architecture.md`, `docs/reviews/**` and this agent's memory folder.

### What is covered now
| Class | Tests | Covers |
|---|---|---|
| `ScoreboardConfigTest` | 3 | (new, round 2) a broken file on reload (invalid layout, then invalid YAML) keeps the previous layout and names the exact problem; a broken file on first load falls back to the jar copy and names the problem; a missing file is reported, the jar copy is used, and with no jar copy there is no sidebar and it says so |
| `SidebarDiffTest` | 4 | first apply writes count, title and every line; identical second apply sends 0; one changed line sends 1; a count change resizes and rewrites every line; a title alone is one write |
| `PlaceholderRegistryTest` | 5 | key regex and built-ins rejected (nothing changes on rejection); duplicate rejected, naming the first owner; `unregisterAll` removes only the owner's keys and bumps the version once; throwing or null resolver renders empty and is logged once per key; a layout resolves only the keys it uses |
| `TemplateTest` | 3 | filled-in text and MiniMessage serialisation equal a real parse of the same sentence; a player name with tags shows literally, with no click event; unknown keys stay literal, missing values render empty, component values go in as they are |
| `ScoreboardLayoutTest` | 4 (+1 disabled) | shipped `scoreboard.yml` loads with no problems and leaves no tag as text; missing title reported; 0 and 16 lines refused with the 1-to-15 message; a bare YAML line becomes blank. Disabled: strict MiniMessage parse of the shipped file (see L-4) |
| `JdbcPlayerStoreTest` | 13 | (+2) sidebar choice defaults to visible, a hidden choice survives a reopen, update does not add a row; a v1 database (with players) migrates to v2 and keeps every player |
| `SchemaMigratorTest` | 4 | (fixed) current version and re-run keeps data |
| `ArchitectureRulesTest` | 12 | (+1) scoreboard calls (`org.bukkit.scoreboard`, `setScoreboard(`, `getScore(`, `registerNewObjective(`, `getNewScoreboard(`) only in `scoreboard/`; (relaxed) `api/` imports |
| `MessagesTest` | 6 | (extended) the key scan covers `scoreboard.*` |

### Not covered, and why
- `SidebarService`, `Sidebar`, `SidebarPreferences` hand-off, the task lifecycle: they call Bukkit's scoreboard manager and the scheduler, so they need a server. Reviewed by reading (section 3); the in-game checks cover them.
- `/scoreboard` tree: its constructor needs `SidebarService`, which is final and package-private in `scoreboard/`. Not worth a seam for one literal and one permission (permission string is covered by `PluginDescriptorTest`).

## 3. Findings

### Round 2: M-1 and L-1 resolved
**M-1 (resolved). The keep-last-good rule has a test seam.** `scoreboard/ScoreboardConfig.java`: the constructor takes a `FileSource` (public nested interface, `read()` returns the file or empty, throws `YamlFiles.BadYamlException`) and a `Supplier<Optional<YamlConfiguration>>` for the jar copy. `ScoreboardModule` supplies the production versions (`YamlFiles.load(plugin, …)`, `YamlFiles.jarCopy(plugin, …)`). The decision logic is unchanged and now runs in `ScoreboardConfigTest` (3 tests, above). Re-reviewed: a broken read keeps the previous layout and adds `keeping the previous sidebar`; the first load with a broken file uses the jar copy and names the problem; a missing file is reported; no jar copy gives `no sidebar will be shown`. No behaviour change in production.

**L-1 (resolved).** `scoreboard/SidebarService.java:176`: the refresh catch now calls `sidebar.close()` before returning the player to "left alone", so the frozen board goes back to the main scoreboard. Not unit-tested (needs Bukkit). Small caveat, accepted: `close()` runs inside the `removeIf` of the tick; if `setScoreboard` itself threw, the rest of that tick would be skipped. That is not realistic and not a new risk.

### Round 2: L-4 partly fixed, one line open (LOW, follow-up)
**L-4. The shipped `scoreboard.yml` still leaves a tag open.** `src/main/resources/scoreboard.yml:15` is now `"<gray>Online: <white><online></white><dark_gray>/</dark_gray><gray><max></gray>"`. The inner tags are closed, but the first `<gray>` is never closed: the final `</gray>` closes the inner one. The lenient parser renders it correctly, so nothing is wrong on screen, but the team's strict standard fails (verified above). Suggested one-line fix: `"<gray>Online: <white><online></white><dark_gray>/</dark_gray><max></gray>"` (the max value inherits the outer gray). When it lands, remove the `@Disabled` on `ScoreboardLayoutTest.theShippedScoreboardFileIsStrictMiniMessage`. Not a bug in play; it is a standards item, so no fix round.

### LOW (follow-ups, no fix round)
- **L-2. A toggle during the first moments after join can invert the choice.** `scoreboard/SidebarPreferences.java:61-62`: `isVisible` returns `true` until the saved choice has loaded. A player who hid the sidebar and runs `/scoreboard toggle` within the load time gets "hidden" (they meant "shown"). Tiny window. Fix: a toggle before the answer is known waits for it, or reads the database.
- **L-3. A registered key can shadow a MiniMessage tag.** `scoreboard/PlaceholderRegistry.java:44-46` only rejects built-ins. Verified: with a key `red` registered, `Template.compile("<red>x</red>", ["red"])` treats `<red>` as the placeholder. A plugin named key such as `red`, `green`, `bold` changes every admin line that uses that tag. Suggest: reject the standard tag names too (or document it on `ScoreboardPlaceholders`).
- **L-5. Typos in `scoreboard.yml` are not reported at load.** `scoreboard/ScoreboardLayout.parse` checks the title and the line count only. The lenient parser accepts unknown tags and bad arguments without complaint (verified: `<unknowntag>`, `<rainbow:abc>`, `<click:run_command>` parse without error), so a typo in a tag name is silent. Also the brief says templates are parsed once at load; the code compiles them on first use after a load or a registry change (`SidebarService.layout()`). Both fine for correctness; suggest a strict parse at load that reports the first bad line.
- **L-6. Dead code.** `log/WarnOnce.java:23` `clear()` has no callers. Remove it, or call it on reload if warnings should repeat after one.
- **L-7. Javadoc nits.** `api/ScoreboardPlaceholders.java:23` uses `@throws` (not banned, but the standard keeps Javadoc to one or two lines without tags). `scoreboard/ScoreboardModule.java:50-51` wraps its Javadoc awkwardly.

### PERF-LOW (follow-up, no fix round)
- **PERF-LOW-1. Every line re-renders when any value changes.** `scoreboard/Sidebar.java:89-91`: when one value changes (ping changes nearly every second), the title and all lines go through `Template.render` again. Measured on the dev machine (throwaway timing test, not committed): one refresh of 16 templates with 5 keys each takes about 0.10 ms warm (0.23 ms cold). At 100 viewers whose ping changes every second that is about 10 ms per second, roughly 1% of the main thread. Suggested fix (also what the brief asked for: "rendered text per line is cached per player"): `CompiledLayout` already knows each line's keys, so re-render only the lines whose used keys changed; a ping change then re-renders only the ping line.

### Checked, no finding
- **Main thread.** Every Bukkit scoreboard call is on the main thread: `show` runs in the join callback (main executor, `runOnMainThread`), `tick` in the repeating main task, `toggle` in the command, `reconfigure` on enable and reload, `shutdown` on disable. The IO thread only calls `SidebarStore` (`SidebarPreferences.java:42-45, 68-70`); `visible` is touched only on the main thread.
- **Task lifecycle.** One task (`CookieCore.repeatOnMainThread`, `PERIOD_TICKS` 20), started by the first viewer (`SidebarService.java:123-125`), cancelled by `stopTaskIfIdle` when the viewer map empties: quit, hide, `hideAll` (disable, reload with `scoreboard.enabled: false`, null layout). `reconfigure` with `enabled` true joins everybody online. Nothing runs when disabled, and `shutdown` cancels it before the rest of disable.
- **Leaks.** `viewers`, `replacedByOthers` and `visible` are cleared on quit (`SidebarService.java:79-83`, `SidebarPreferences.forget`). A load that finishes after the player left forgets the entry again (`SidebarService.java:69-70`). A disabled placeholder owner's keys are removed (`SidebarListener.onPluginDisable`). Only the viewer's own `Scoreboard` is held strongly, and it is dropped on quit.
- **`setScoreboard` on quit.** Not called. `Sidebar.close()` runs only on hide, disable and a failed refresh, and only if we still own the board (`ownsBoard()`).
- **Flicker.** No per-tick reset: `resetScore` runs only when the line count shrinks (`BoardSink.java:38-40`). The title is written only when it changes, and a line only when its text changes (`SidebarDiff`).
- **Thread safety.** `PlaceholderRegistry` uses a `ConcurrentHashMap` and an `AtomicInteger`, so a registration from another plugin's thread cannot corrupt it (the documented contract is the main thread). `WarnOnce` uses `ConcurrentHashMap.newKeySet()`.
- **MiniMessage.** Values go into `Template.render` as `Component.text` (`Template.java:60`) or as the component a resolver returned; nothing from a player or a value is parsed. `Placeholder.parsed` is still only in `text/Messages`. The test `playerInputIsNeverReadAsMiniMessage` passes.
- **Messages and sounds.** `scoreboard.shown`, `.hidden`, `.disabled`, `.players-only` are in `Messages.REQUIRED_KEYS`, in `messages.yml`, and every key used in code parses strictly. Sounds go through `CommandKit.done` (`ui_confirm`) and `SoundService` (`ui_notify`); no direct `playSound`.
- **Storage v2.** `sidebar_prefs` uses the table prefix, `CREATE TABLE IF NOT EXISTS`, plain `BOOLEAN` (H2 and MariaDB), and runs on the one IO thread, so the UPDATE-then-INSERT in `saveVisible` cannot race. Tested on H2.
- **Descriptor.** `cookiecore.scoreboard.toggle` default `true` (`paper-plugin.yml`), matching `Perm`; `PluginDescriptorTest` passes.
- **Config.** `scoreboard.enabled` reads through `PluginSettings`, a bad value is reported and defaults to `true`; `config` imports no scoreboard class. `ScoreboardConfig` imports `config.YamlFiles` only (one direction).
- **Standards.** No `@param` or `@return`, no wildcard imports, largest scoreboard class `SidebarService` at about 200 lines, no method near 60 lines. Package direction matches `docs/architecture.md`.

## 4. Architecture
- The split is clean: `Sidebar` (one player), `SidebarDiff` (what changed, behind `SidebarSink`), `BoardSink` (the only Bukkit writer), `SidebarService` (who sees it, the task), `ScoreboardConfig` (file decision, now fed by suppliers). Swapping to the Team fallback only needs `BoardSink` (documented in `architecture.md`).
- `scoreboard` depends on `config`, `storage` (through the `SidebarStore` interface), `text`, `log` and `api`; `command` depends on `scoreboard`; nothing depends back. No cycles (`ArchitectureRulesTest`).
- The replaced-board rule is as documented: one takeover per join at most, the player is left alone until rejoin (`SidebarService.refresh`, `replacedByOthers`).

## 5. Performance
Per-tick work: the repeating task runs once per 20 ticks and only while someone sees the sidebar. Per viewer per run: about five key lookups (one `ping` read, the registry for registered keys actually used), one array comparison, and a re-render only when a value changed (PERF-LOW-1: about 0.1 ms warm, 16 templates). No entities, no packets beyond the changed lines, no allocations beyond the value array and, on change, the render. Idle cost: zero (no task). Join: one IO-thread read, no main-thread I/O. Toggle: one memory write and one background save. `serverValues()` runs once per refresh and reads the online count (O(1)). Worst case at normal player counts is about 1% of the main thread, so no PERF-HIGH.

## 6. In-game test guide (for Luuk)
Build the jar, start a Paper 26.3 test server, and run with a few players (or bots) online.
1. **Key check, numbers and text.** The sidebar shows: `Cookie Empire` (gold, bold) title, then the player name, the ping, `Online: x/max`, with blank spacer lines. No red numbers at the right of any line. No `l00`..`l14` text anywhere, including on the blank spacer lines. If the text does not render (`customName` ignored), tell me: the fallback is a Team prefix/suffix (documented, swap `BoardSink` only).
2. **Flicker.** Stay in view for two minutes while moving and changing your ping (or add a second player who joins and leaves). The board must not blink. Values update about once a second.
3. **Toggle.** `/scoreboard toggle` hides it and plays the "notify" sound with the message `Sidebar hidden...`. Again shows it with the "confirm" sound. `/sb toggle` does the same. A player without the permission gets the no-permission message (permission default is true, so test with a revoked node if you have one).
4. **Persistence.** Hide it, log out and back in: still hidden. Restart the server: still hidden. Show it again, restart: still shown.
5. **Reload.** Edit `scoreboard.yml` (change the title and one line), `/cookiecore reload`: the board updates for everybody online without a relog. Break the file (an unclosed `[` or delete the `lines:` key), reload: a message names the problem and the old sidebar stays. Fix it again, reload: it comes back.
6. **Enable switch.** `scoreboard.enabled: false`, reload: the sidebar disappears for everybody and `/scoreboard` says it is turned off. Back to `true`, reload: it returns for those who did not hide it.
7. **Database v1 migration.** Use a database written by the previous build (schema version 1; for H2 keep a copy of `plugins/CookieCore/` before updating). Start the new build: no error, the log names no problem, `core_players` rows are still there, and `SELECT version FROM core_schema_version` gives 2 (the `core_sidebar_prefs` table exists).
8. **Other plugins.** A placeholder registered by another plugin (none ships with this build; skip unless CookieClicker is installed) shows its value after `/cookiecore reload` and goes away when that plugin is disabled.
9. **Performance (spark).** With about 20 players online, `/spark profiler start --timeout 60`, then check the share of the tick used by `CookieCore` (`scoreboard` frames). Good: well under 1% of the tick. Anything above a few percent needs the PERF-LOW-1 fix.
10. **Sounds.** Listen to `ui_confirm` (shown) and `ui_notify` (hidden) on toggle, as the brief asks.

## 7. Fixes per owner
- **plugin-coder:** nothing blocks the merge. Optional follow-ups, in any later PR or small commit: L-4 (one line, then the tester removes the `@Disabled`), L-2, L-3, L-5, L-6, L-7, PERF-LOW-1.
- **plugin-tester:** when L-4 lands, remove `@Disabled` on the strict shipped-file test and rerun `mise run check`.

## 8. Follow-up list (for the PR)
L-2 to L-7, L-4 (strict line, see above), PERF-LOW-1, and the in-game items in section 6.

VERDICT: READY
