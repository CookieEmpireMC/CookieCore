# Review: core-gamemode

Reviewer: Gandalf (plugin-tester) · 2026-10-27 · branch `core-gamemode/plugin-coder` (merged into `feature/core-gamemode` at `5be0617`); tests and docs on `core-gamemode/plugin-tester` (`79b9cfd`, `513e3a9`, `60bc404`, `8374d9a`, `5f453ed`).

**VERDICT: READY** (to merge into `feature/core-gamemode`). No HIGH, MEDIUM or PERF-HIGH findings. `mise run check` is green with 160 tests (137 before). Ten LOW items are follow-ups, one of them a deprecated call (L-1) that is a one-line fix. Three decisions are for Luuk (L-3, L-4, L-5). Nothing was checked in game yet: the guide in section 4 is the PR's human test.

## 1. Target version
- `pom.xml`: `paper-api` `26.3.build.205-stable`. `paper-plugin.yml`: `api-version: '26.3'`. `mise.toml`: Java 25. All match (`PluginDescriptorTest`).
- API used by the feature was checked against the 26.3 jar with `javap`: `Commands.register` returns `Set<String>`; `ArgumentTypes.player()` is the single-player selector; `PlayerGameModeChangeEvent(Player, GameMode, Cause, Component)` exists.
- Deprecated in 26.3 (from the compiler): `Player#isOnGround()` (since 1.16.1) in `FlyTracker.java:33` → L-1. The two-argument `PlayerGameModeChangeEvent` constructor is deprecated for removal; only the test helper used it, and that is fixed on the tester branch (`5f453ed`).

## 2. Test results
- `mise run check` (`mvn -B clean verify`): **BUILD SUCCESS, 160 tests, 0 failures, 0 errors, 0 skipped.** Before this feature: 137 (`core-tidy` review). Added: 23.
- `mise run test`: green before and after each commit.
- `validate-assets`, `sound-check`, `balance-check`: not tasks in this repo (no assets, no balance). Not applicable.
- `ownership-check`: cannot run in this repo (F-02, no `.claude/hooks`). The tester commits touch only `src/test/**`, `docs/architecture.md`, `docs/reviews/**` and the tester's agent memory.

| Class | Tests | Covers |
|---|---|---|
| `GameModesTest` (new) | 3 | all 12 tokens, case, junk (empty, spaces, `4`, `-1`, `creativ`, `1.0`), one node per mode |
| `SpeedScaleTest` (new) | 3 | level 1 is exactly 0.2f / 0.1f, level 10 exactly 1.0f, levels 2..10 increase and level 5 is linear, 0, 11, -3 and 99 clamped, validity edges |
| `FlyTrackerTest` (new) | 5 | creative and spectator need no re-apply; survival switch re-applies on the next tick and keeps flight; players who never turned fly on are untouched; lost permission drops the player; quit switches survival flight off and forgets the player |
| `GameModeTreeTest` (new) | 11 | roots hidden without their node (`/gmsp`, `/gamemode`); completion shows only the modes the sender may use; same mode says so and makes no sound; self needs the mode node; another player needs `others` and the mode node; staff change notifies the target with the staff name and the right sounds; unknown or offline target is a message; console needs a target, `/execute as` gives one; `speed 5`, `speed reset fly`, `speed 5 walk Steve`; `speed 0` and `speed 11` rejected with no change; `fly` toggles, creative says so, `fly Steve` needs `fly.others` |
| `PluginDescriptorTest` | +1 (7 total) | `cookiecore.gamemode.*` has exactly the four modes plus `others`, all true |
| `MessagesTest` | 6 (scan widened, parse extended) | every `gamemode.`, `fly.` and `speed.` key literal in code exists in `messages.yml`; keys built at runtime are listed and checked; every shipped message parses strictly with the new placeholders (`mode`, `player`, `staff`, `type`, `level`) |

Infrastructure added (test sources only): `gamemode/FakePlayer` (a `Player` proxy with mode, flight and speed state that behaves like the server: the event fires before the change, survival and adventure drop flight); `io.papermc.paper.command.brigadier.argument.TestVanillaArgumentProvider` with a `META-INF/services` entry, so `ArgumentTypes.player()` resolves names without a server; `TestSupport.noStore()` and `source(sender, executor)` moved or added. Trimmed nothing else: `CommandTreeTest` still has 11 tests.

Counts are from the surefire reports of the final `mise run check`. Tests ran on this branch: yes.

## 3. Findings
Most severe first. File and line refer to the coder's code at `5be0617`.

### Blocking
None. No HIGH or MEDIUM bugs, no PERF-HIGH.

### LOW (follow-ups, no fix round)
- **L-1 Deprecated call (version check).** `gamemode/FlyTracker.java:33` calls `player.isOnGround()`, which is `Player#isOnGround()` and deprecated since 1.16.1 in 26.3. `Entity#isOnGround()` is not deprecated. Fix: call it through an `Entity` reference (`((Entity) player).isOnGround()`). Works today; no behaviour change.
- **L-2 Unknown mode and bad numbers play no `ui_error`.** `GameModeArgument.java:35` (unknown mode) and the Brigadier integer parse for `/speed` throw a parse error, so the sender gets the message but no sound. The brief says unknown mode gets `ui_error`. `KnownPlayerArgument` has the same shape (accepted in `core-tidy`). Accept, or wrap the argument errors in a `kit.fail`.
- **L-3 `/gamemode` takes one player (decision for Luuk).** `CommandKit.java:94` (`onlinePlayer`, `ArgumentTypes.player()`) is used at `GameModeCommand.java:69`. Vanilla `/gamemode creative @a` and command blocks or datapacks that use multi-target selectors now fail with a parse error. The brief asks for one player; the research note suggested `players()` with one summary message. Keep, or switch the gamemode tree to `players()`.
- **L-4 Changing someone else needs `others` and the mode node (decision for Luuk).** `GameModeCommand.java:87` and `PlayerTargets.java:82`. The brief's wording ties the mode nodes to "changing yourself", so the literal reading lets `others` alone set anyone to any mode. The coder chose the stricter reading (least privilege). Recommend keeping it and writing it into the PR body, so `/gm` with `others` alone cannot put someone in survival.
- **L-5 `/minecraft:gamemode` is not gated by our nodes (by design).** The brief keeps it reachable. An op who lacks `cookiecore.gamemode.*` can still use it. To make the nodes the only gate, the server's permission setup must deny `minecraft.command.gamemode`. Put this in the PR's notes (in-game step 11).
- **L-6 Permission revocation takes effect on the next mode switch.** `FlyTracker.java:66`. Paper has no event for permission changes, so a player who loses `cookiecore.fly` keeps flying until they switch mode or relog. Accept; document.
- **L-7 `/fly` and `/speed` registration results are not checked.** `CookieCore.java:101-102` ignore the `Set` returned by `register`. `/gm` and `/gamemode` log a warning when another plugin owns the label (`GameModeCommand.java:58`). Add the same check for `fly` and `speed` (one line each) or accept.
- **L-8 Command descriptions are English literals.** `GameModeCommand.java:46` ("Change a game mode"), `:53` ("Switch to … mode"), `CookieCore.java:101-102` ("Toggle flight", "Change walking or flying speed"). They show in Paper's command listings rather than in chat. `CookieCoreCommand` has the same pattern. Move them to `messages.yml`, or accept.
- **L-9 Kicked players are cleaned up only if Paper fires `PlayerQuitEvent` for kicks.** `FlyTracker.onQuit` is the only cleanup. I did not verify this from source here. In game (guide step 15). If it does not fire, a kicked flyer keeps `allowFlight` in the saved data and gets flight on rejoin: raise to MEDIUM.
- **L-10 `/fly` off in mid-air drops the player (fall damage).** `FlyCommand.toggle` → `FlyTracker.disable` → `setAllowFlight(false)`. Same accepted behaviour as the brief's re-apply note. Listed so the PR says it.

### Reviewed and correct (no finding)
- **Permissions.** Every root and sub-literal is gated: `main()` requires any of the four mode nodes (`GameModeCommand.java:65`), each shortcut its own mode (`:76`), `/fly` and `/speed` use `kit.literal` or `kit.checked`. A mode check uses the sender (`mayUse`), and the target check compares the sender with the target (`PlayerTargets.allowed`, `isSelf`). So `/execute as Steve run gamemode …` by a player without `others` is refused (checked by reading; the tests cover the console case).
- **Completion.** `GameModeArgument.listSuggestions` offers only the modes the sender may use (tested). Player suggestions are public tab-list names.
- **Console and `/execute`.** `hasPermission` is true for the console, and a non-player executor gets `*.console-needs-target` (tested). `/execute as Steve` gives the console a target (tested).
- **Same-mode no-op.** Message only, no sound, no `setGameMode` (tested).
- **Threading.** Brigadier, events and the tracker run on the main thread. `runOnMainThread` is `Bukkit.getScheduler().runTask` (`CookieCore.java:151-156`), so the re-apply runs on the next tick, after the server has dropped flight. `FlyTracker`'s `HashSet` is main-only.
- **Mode-change handler.** `onGameModeChange` is MONITOR with `ignoreCancelled`. Per change: one `HashSet` lookup and one enum switch. No re-entrant `setGameMode`.
- **Re-apply rules.** Scheduled only for tracked players switching into survival or adventure. At run time it checks online, permission, and creative or spectator (tested).
- **Leaks.** The UUID set holds only players who turned `/fly` on, removed on quit or permission loss. Nothing static.
- **Messages.** Every key used in code exists in `messages.yml`. Placeholders were checked against the call sites:
  - `gamemode.no-permission-mode`, `already-self`, `changed-self`: `mode`. `already-other`, `changed-other`: `mode`, `player`. `changed-by`: `mode`, `staff`. `failed`: `player`. `invalid-mode`: `input`.
  - `fly.already-flies-self`: `mode`. `already-flies-other`: `mode`, `player`. `enabled-by`, `disabled-by`: `staff`.
  - `speed.invalid-level`: `input`. `set-self`, `set-other`, `set-by`: `type`, `level` (plus `player` or `staff`). `reset-*`: `type` (plus `player` or `staff`).
  - No player text is built in Java; names and input go through `unparsed`.
- **Standards.** No `@param` or `@return`, no wildcard imports, no history in comments. Largest class `GameModeCommand` is 114 lines; no method close to 60 lines.
- **Descriptor.** `cookiecore.gamemode.*` children are the four modes plus `others`; all new nodes default to op (`PluginDescriptorTest`).
- **Architecture.** `gamemode` imports only `command`, `sound`, `text` and `Perm`; the root imports `gamemode`; no cycle (`ArchitectureRulesTest`).

### Decisions made by the coder, reviewed
- Changing someone else needs the mode node too: keep (L-4).
- No target means the executor, so `/execute as` works, and the console gets `console-needs-target`: keep, tested.
- Quit switches survival and adventure flight off: keep. Without it, a relog would give flight without `/fly`.
- New keys not in `Messages.REQUIRED_KEYS`: keep. The validator only names `command.*` keys; the scan test covers `gamemode.`, `fly.` and `speed.` (both literal and runtime-built keys). An older admin `messages.yml` gets the new keys from the jar defaults without a warning.

## 4. Performance
- **Hot paths:** none. No tick task, no timer, no per-player-per-second work. The re-apply task is scheduled once per mode switch of a tracked player.
- **Per command:** one player-selector resolve (Paper scans the online players once), an O(1) token lookup, 4 mode suggestions, 10 level suggestions.
- **Per mode change:** one `HashSet` lookup in the event handler. Rare (staff action, or a join with `force-gamemode`).
- **Memory:** the flight set is bounded by online players who turned `/fly` on.
- **Threads:** nothing async, no I/O.
- **Result:** PERF-HIGH none, PERF-MEDIUM none, PERF-LOW none.
- **spark (in game):** `/spark profiler start --timeout 60`, then toggle `/fly` and `/gm` 20 times. Expected: CookieCore's share of the tick is effectively 0 %. Anything visible under `gamemode` is a bug to report.

## 5. Architecture
- Layers are clean. `CookieCore` registers three more roots inside the lifecycle lambda (lines ~99–103); the main class stays under its 200-line rule.
- `PlayerTargets` does three things (who acts, the permission check, the target notice). It is small (88 lines) and each part is used by all three commands, so it stays.
- The three command trees repeat the online-player argument and the `fail`/`done`/`say` replies. Both go through `CommandKit`, so the repetition is one line each.
- `FlyTracker` is both a listener and the in-memory flight state. One responsibility (who has flight on); fine.

## 6. In-game test guide (Luuk)
Use an op account (Alex) with every node, a second account (Steve) as the target, and the console. Repeat steps 6, 8, 16 and 17 with a staff account that lacks `cookiecore.gamemode.others` / `cookiecore.fly.others`.

| # | Do | Expected |
|---|---|---|
| 1 | `/gm c` (survival) | "Your game mode is now Creative." and the pling sound |
| 2 | `/gm c` again | "You are already in Creative mode." and no sound |
| 3 | `/gamemode` | the usage line |
| 4 | `/gamemode banana` | "'banana' is not a game mode…". No sound (L-2) |
| 5 | Staff without `cookiecore.gamemode.spectator`: type `/gamemode ` and press TAB | only the modes you may use; `/gmsp` is not a command for you |
| 6 | `/gmc Steve` as staff with `others` | Steve: "Alex set your game mode to Creative." and the orb pickup sound; you: "Steve is now in Creative mode." and the pling |
| 7 | `/gamemode creative Ghost` | "That player is not online." and the bass sound |
| 8 | `/gamemode creative Steve` without `others` | "You do not have permission to change the game mode of other players."; Steve unchanged |
| 9 | Console: `gamemode creative`, then `gamemode creative Steve` | first: "The console has no game mode…"; second: Steve changes |
| 10 | Console: `execute as Steve run gamemode survival` | Steve changes to survival |
| 11 | `/minecraft:gamemode survival` as op | works (L-5) |
| 12 | `/gamemode creative @a` | an error: one player only (L-3, expected today) |
| 13 | `/fly` in survival, then `/gm a` while flying, then `/gm s` | "Flight enabled."; you keep flying through both switches (flight comes back on the next tick) |
| 14 | `/fly` again | "Flight disabled."; you fall normally |
| 15 | With `/fly` on in survival, kick yourself (or have Steve kick you) and rejoin | no flight on rejoin; `/fly` on again works (L-9) |
| 16 | `/fly` in creative | "You can already fly in Creative mode." |
| 17 | `/fly Steve` as staff without `cookiecore.fly.others` | "You do not have permission to change flight for other players." |
| 18 | `/speed 5`, then `/speed 1`, then `/speed 10` | walking speed visibly up, back to normal, then maximum |
| 19 | `/speed reset`; while flying, `/speed reset fly` | normal walking; fly speed back to normal |
| 20 | `/speed 0`, `/speed 11` | "'0' is not a speed…" with the bass sound |
| 21 | `/speed 5 walk Steve` as staff with `others` | Steve: "Alex set your walking speed to 5." |
| 22 | Server log after start | no "Could not take over" line, unless another plugin owns `/gm` (EssentialsX does) |

## 7. Follow-ups per owner
- **plugin-coder:** L-1 (`FlyTracker.java:33`, use the `Entity` method), L-7 (check the `fly` and `speed` labels like `gm`), L-8 (descriptions into `messages.yml`, optional). No fix round: these are LOW.
- **Luuk (decisions):** L-3 (single player or `@a`), L-4 (`others` plus the mode node, keep as built), L-5 (note the vanilla escape hatch in the PR).
- **In game:** steps 15 (L-9) and 22; if step 15 fails, the item becomes MEDIUM and returns to the coder.

VERDICT: READY
