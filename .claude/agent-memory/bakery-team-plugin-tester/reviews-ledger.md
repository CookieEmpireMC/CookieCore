---
name: reviews-ledger
description: Per reviewed area, the commit verified and what passed; re-review only the diff since.
metadata:
  type: project
---
## CookieCore `core-tab` phase 1 round 2 (2026-10-29), VERDICT READY
Coder commit `2d2bc61` (diff only): M-1 fixed (`RankSource.orElse`: `own > 0 ? own : other.order`), L-3 fixed (`PermissionRankSource.refreshIfAbsent`, `TabListener` at HIGHEST after chat's HIGH join handler), L-2 comment in `tab.yml`, L-4 Javadoc in `api/ScoreboardPlaceholders`. Tester: `f71054a` (M-1 test enabled, refreshIfAbsent test, `ArchitectureRulesTest.tabListCallsLiveOnlyInTheTabPackage`, proven to bite), `64faf20` (review round 2, architecture). Suite 282 run, 0 failed, 1 skipped (pre-existing) via `mvn.cmd -B clean verify`.
Open LOW: L-1, L-5 (LuckPerms weight clamp needs a seam), L-6, L-7, PERF-LOW-2 (TPS step), R2-1 (`computeIfAbsent` holds a bin lock during the permission lookup; main-thread writer only).
Next re-review: none planned for core-tab; the follow-ups go to the PR list.

## CookieCore `core-tab` phase 1 (2026-10-29), VERDICT NOT READY (M-1)
Review doc: `docs/reviews/2026-10-29-core-tab.md` (tester branch `core-tab/plugin-tester` in the coder's worktree, tip is the review commit `a2f8576`; tests `5d0b254`, `5b9aa48`, `ae0e16d`). Coder verified at `664cad1`.
Areas: `tab/*` (all 8 classes), `rank/*` (all 6, moved from `chat`), `chat/ChatModule` and `ChatSettings` (rank split), `CookieCore` onEnable/reload/onDisable, `CoreWiring`, `ConfigLoader.collect`, `PluginSettings` tab switch, `PlaceholderRegistry` (made public), `text/TabText`, `config.yml`, `tab.yml`, `paper-plugin.yml` description.
Passed: packet rule (send on text change only), main-thread only, getPing for viewers only, quit and drop cleanup, reload and disable resets, shipped `tab.yml` strict, names stay text, broken-file rules, 200-viewer timing (26 us quiet tick, 0 packets), `CookieCore.java` 199 lines, architecture rules with `rank/LuckPermsRankSource`, full build 280 run / 0 failed / 2 skipped.
Open: M-1 MEDIUM (`rank/RankSource.java:32` `orElse` takes the LuckPerms order only when it has a prefix or weight; prefix-without-weight hides chat.yml order; test `RankSettingsTest.aLuckPermsGroupWithoutWeightDoesNotHideTheChatYmlOrder` is `@Disabled`, fails today). LOW L-1..L-7 and PERF-LOW-1/2 in the review.
Next re-review: `git diff <coder commit after 664cad1>..HEAD -- src/main` for the M-1 fix; enable the disabled test; full suite.

## CookieCore `core-chat-join` round 2 (2026-10-28), VERDICT READY
Coder commit `a49eeb4` (authored Wesly, on the tester branch `core-chat-join/plugin-tester`; src/main and messages.yml only). Reviewed the whole `git show a49eeb4`. Tests: `9cbf4b6` (ChatListenerTest 4, ChatSettingsTest +2, ArchitectureRulesTest +1). Suite 258 run, 0 failed, 1 skipped; `mise run check` BUILD SUCCESS.
Resolved: M-1 (`MuteListener` predicate is `Predicate<Player>`, `ChatModule.staffChannelActive(Player)` needs the permission), M-2 (`CookieCore.settings` volatile, reflection test), L-1 (rank cache swapped as a whole, miss = default prefix, no permission read), L-3 (console stays a staff reader; `staffReaders` static, tested), L-4 (`chat.motd-hidden-count` key), L-5 (bad motd item rule), PERF-LOW-3 (notifier early return).
Open LOW: L-2 (`hasPermission` read on chat thread in `ChatListener.java:46` and in `staffChannelActive`, reads only), L-6 (`CookieCore.java:90` inline FQN), L-7 (staff line dropped on shutdown), PERF-LOW-1 (LuckPerms prefix parsed per message).
Gap: `ChatModule.staffChannelActive` itself is not unit tested (needs a JavaPlugin); ChatListenerTest mirrors the expression. Suggest a package-private static seam. In-game step 12 covers it.

## CookieCore `core-chat-join` (2026-10-28, round 1), VERDICT NOT READY (M-1, M-2)
Review doc: `docs/reviews/2026-10-28-core-chat-join.md` (tester branch `core-chat-join/plugin-tester` in the coder's worktree: `643433c` fixes the two invalidated tests, `31e32ef` chat tests, then the docs commit). Coder verified at `03dce54`.
Areas: `chat/*` (all 15 classes), `CoreWiring` and the `CookieCore.java` wiring diff, `MuteListener`/`MuteList`/`ModerationModule` (exemption, labels, `collectMissing`), `ChatText`, `Messages` helpers, `PluginSettings` `chat.enabled`, `Perm`, `paper-plugin.yml`, `chat.yml`, `messages.yml`, `sounds.yml`, `config.yml`.
Passed: renderer only (no `event.message`), `ignoreCancelled` at HIGH, no player text parsed, staff delivery on main, LuckPerms isolation (factory plus architecture rule), `CookieCore.java` 192 lines, scheduler only in `CookieCore`, 4a/4b labels intact, per-player state removed on quit.
Open: M-1 MEDIUM (`MuteListener.java:83` exemption ignores the staff permission; `ChatModule.java:79`), M-2 MEDIUM (`CookieCore.java:52` settings not volatile, read by async chat and ping), LOW L-1..L-7, PERF-LOW-1..3, no PERF-HIGH.
Timing (dev machine, warm, throwaway test, deleted): chat line render about 10 us, MOTD render about 9 us, expiry tick with 200 players about 20 us per second.
Suite: 225 before the chat tests, 251 after (1 skipped, pre-existing `ScoreboardLayoutTest`).
Next re-review: `git diff 03dce54..HEAD -- src/main` on the coder branch, plus the M-1 regression test.

## CookieCore `core-moderation-mutes` slice 1 (2026-10-28), VERDICT READY
Review doc: `docs/reviews/2026-10-28-core-moderation-mutes.md`. Coder code verified at `468182e`; tester commits `78a5dcb` (tests), `ce3060f` (review, architecture). Suite 213 run, 0 failed, 1 skipped; `mise run check` BUILD SUCCESS.
Areas: `moderation/MuteList`, `MuteListener`, `ModerationService` mute/unmute, `ModerationModule` mute load, `JdbcPlayerStore` loadActive/deactivate refactor (ban SQL unchanged), `PunishmentStore` defaults, `paper-plugin.yml` mute nodes.
Open LOW (PR follow-ups): LOW-1 `/mute` can double-insert in the window between `usable=true` and `loadMutes()` (`ModerationModule`), LOW-2 unused 10-arg `ModerationService` constructor, LOW-3 expired mutes stay in `MuteList`, LOW-4 `MuteRecord.of(BanRecord)` coupling and default store methods.
Next re-review: `git diff 468182e..HEAD -- src/main` for the coder's fixes.

## CookieCore `core-moderation-bans` (2026-10-27), round 2 VERDICT READY (conditional on Rick's boot-outage decision)
Round 2 reviewed `git diff 2531cd6..HEAD -- src/main` (coder `d52a5a5`, `c451740`): ModerationModule fail-closed load (one in flight, flag cleared in finally and on rejection, usable/loaded volatile order), ModerationListener retryLoad + L-5, ModerationService background/finish (busy in finally, Error rethrown), messages.yml key removal, CommandKit Javadoc. Tests +2 (`ModerationModuleTest`, Error case in `ModerationServiceTest`): 205 run, 0 failed. Open: `storageAvailable()==false` at boot still opens logins (residual of M-1, Rick to confirm); R2-L1 kick during failed load says 'database not available'; R2-L2 retry attempts serialise on the IO thread (PERF-LOW). Next re-review: `git diff <coder commit after c451740>..HEAD -- src/main`.

## CookieCore `core-moderation-bans` (2026-10-27), VERDICT NOT READY (round 1)
Review doc: `docs/reviews/2026-10-27-core-moderation-bans.md`. Coder branch `951091b` (DONE). Tester branch `core-moderation-bans/plugin-tester` holds 20 new tests (suite 203 run, 1 skipped, `mise run check` BUILD SUCCESS) and the architecture update.
Areas read: `moderation/*` (ModerationService, BanList, ModerationListener, ModerationModule, the four commands, SilentReasonTail, BannedPlayerArgument, Actor), storage punishment methods + Migrations v3 + PunishmentStore/Type/NewPunishment/BanRecord, `text/Messages` (banScreen, deny*, thread safety), TimeText, CookieCore wiring, Perm, paper-plugin.yml, messages.yml moderation block.
Open: M-1 MEDIUM (to coder): `ModerationModule.java:60-74` `markLoaded()` on every path, so logins are allowed when the database is down at boot or the load fails (brief says deny until loaded). LOW L-1..L-13 (dead keys messages.yml:105,110; CommandKit.java:35 Javadoc `moderation.muted`; double message for the sender; denyStarting overwrites an earlier refusal; text->storage edge; busy not freed on Error; JdbcPlayerStore split before 4b). MariaDB not run: checklist in the review section 6.
Next re-review: `git diff 951091b..HEAD -- src/main` after the coder's M-1 fix; tests in full.

## CookieCore `core-scoreboard` (2026-10-27), round 2 VERDICT READY
Round 2 (coder fix `1c85bb8`, merged `965e853`, tester branch `7a7ca6d`): M-1 resolved (ScoreboardConfig takes FileSource + jar supplier; ScoreboardConfigTest 3), L-1 resolved (close() in refresh catch). Open LOW: L-4 one line in scoreboard.yml (outer gray still open; strict test @Disabled until fixed). Suite 160 run, 0 failures, 1 skipped. Re-review scope next time: `git diff 7a7ca6d..HEAD -- src/main` (L-4 line, L-2/L-3/L-5 if fixed).

## CookieCore `core-scoreboard` round 1 (2026-10-27), VERDICT NOT READY (branch review, plugin-coder)
Review doc: `docs/reviews/2026-10-27-core-scoreboard.md`. Coder code reviewed at `61fa3bb` (src/main untouched by me); tester commits `0624e44..34bd8d7`.
Areas checked by reading: `scoreboard/*` (SidebarService, Sidebar, SidebarDiff, BoardSink, SidebarPreferences, PlaceholderRegistry, ScoreboardConfig, ScoreboardLayout, CompiledLayout, ScoreboardModule), `text/Template`, `log/WarnOnce`, `api/ScoreboardPlaceholders`, `storage` v2 (`JdbcPlayerStore` sidebar methods, `Migrations`), `command/ScoreboardCommand`, CookieCore enable/reload/disable wiring, `PluginSettings.scoreboardEnabled`, `paper-plugin.yml`, `messages.yml`, `scoreboard.yml`.
Passed: main-thread rule, task lifecycle, leaks on quit/disable, no setScoreboard on quit, no per-tick reset (flicker), registry thread safety, WarnOnce thread safety, Template never parses values, storage v2 SQL.
Open: M-1 (MEDIUM, to coder) `ScoreboardConfig` has no seam: keep-last-good test impossible. LOW L-1..L-7, PERF-LOW-1 (every line re-renders when one value changes, ~0.1 ms per viewer per change).
Tests: 137 run / 5 failing before; 156 run / 0 failing after (`mise run check` BUILD SUCCESS, 21.7 s). Next re-review: `git diff 61fa3bb..HEAD -- src/main` for the M-1 seam, then add the three ScoreboardConfig tests.

## CookieCore `core-gamemode` (2026-10-27), VERDICT READY
Review doc: `docs/reviews/2026-10-27-core-gamemode.md`. Coder branch merged at `5be0617`; verified at `5f453ed` (tester tests and deprecation fix) with the docs commit `8374d9a`.
Areas: `gamemode/` (all 8 classes: GameModes, GameModeCommand, GameModeArgument, PlayerTargets, FlyCommand, FlyTracker, SpeedCommand, SpeedScale), `Perm.java`, `paper-plugin.yml` (gamemode/fly/speed nodes), `CookieCore.java` registration lines, `messages.yml` gamemode/fly/speed keys, `docs/architecture.md`.
Tests: 137 before, 160 after (+23, 0 skipped); `mise run check` BUILD SUCCESS.
Open LOW: L-1 `FlyTracker.java:33` `Player#isOnGround()` deprecated (use the `Entity` method), L-2 unknown mode/number errors no `ui_error`, L-3 `/gamemode` single player (`@a` refused, decision), L-4 `others` also needs the mode node (decision, stricter than brief), L-5 vanilla `/minecraft:gamemode` ungated by our nodes (by design), L-6 permission revocation only on next mode switch, L-7 `fly`/`speed` label takeover not logged, L-8 English command descriptions, L-9 kick -> `PlayerQuitEvent` unverified (in-game step 15; MEDIUM if it fails), L-10 `/fly` off mid-air fall damage.
Next re-review: `git diff 5f453ed..HEAD -- src/main` for the coder's L-1/L-7 fixes (tests run in full).

## CookieCore `core-tidy` (2026-10-26), VERDICT READY
Review doc: `docs/reviews/2026-10-26-core-tidy.md`. Verified at `5873b9c` (tester trim) plus `1f380de`/`a467149` (docs). Coder branch merged at `d21a33a`.
Areas: `paper-plugin.yml` (loader, `dependencies.server.LuckPerms`, permissions), `CookieCoreLoader` + `cookiecore-libraries.properties` (pom-filtered; jar checked), `PluginSettings.intOrDefault` (F-12), `CookieCore.SHUTDOWN_TIMEOUT_SECONDS`, `docs/architecture.md` Standards.
Tests: 230 (1 skipped) before, 137 (0 skipped) after the trim; `mise run check` BUILD SUCCESS.
Open LOW follow-ups: L-1 unquoted `3308.5` truncated silently (`Number` branch, `intValue`), L-2 stale `softdepend` Javadoc in `api/CookieCoreApi.java:9` (coder), L-3 empty libraries file gives no clear message, L-4 Aether null scope unverified, L-5 admins' shutdown-timeout key ignored. Known one-way dependency `config -> sound/storage/text` documented, not asserted.
Next re-review: `git diff a467149..HEAD -- src/main pom.xml src/main/resources` for the coder's L-1/L-2/L-3 fixes.

## CookieCore `core-foundation` (2026-10-21), VERDICT READY (twice)
Review doc: `docs/reviews/2026-10-21-core-foundation.md` (sections 1-8 first pass at `cd76015`, section 9 fix pass at `0786282`).
First pass areas: CookieCore main class, CoreService, Perm, api/, command/, config/, player/, sound/, storage/, text/, plugin.yml, config/messages/sounds.yml, pom.xml, mise.toml, CI workflow.
Fix pass (`0786282`): F-01, F-03..F-09 verified fixed. Tab completion 5-15 us at 10k..500k names. F-12 was open then; closed in core-tidy.
Open (still): F-13 INFO scheduler-cancelled lookup future, F-14 INFO unused `cachedPlayers()` (only tests call it now), F-02 ownership-check cannot run (no .claude/hooks), F-10/F-11 INFO.

## CookieCore `core-moderation-warns` (2026-10-28, round 1), VERDICT NOT READY (M-1)
Review doc: `docs/reviews/2026-10-28-core-moderation-warns.md` (tester commit `d4c7127` on `core-moderation-warns/plugin-tester`, coder tip `e0d2def`).
Reviewed: storage split (`JdbcPunishmentStore`, `JdbcSession`, `JdbcPlayerStore.close`), `ModerationService.warn`, `PunishmentHistory`, `MuteListener` whisper block, `ModerationModule` load order, `Perm`/`paper-plugin.yml` warn and history nodes, messages.yml.
Open: M-1 (failed mute load: `/mute` succeeds, mute not enforced, `usable` set anyway). LOW-4 (abstract `loadActiveMutes`/`deactivateMutes`/`history`, coder). Test gaps: warn service and PunishmentHistory mapping.
Next run (re-review): `git diff e0d2def..HEAD` on the coder branch only for M-1 fix; keep `storage/` and `moderation/` verdicts above.
