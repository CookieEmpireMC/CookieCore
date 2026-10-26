---
name: reviews-ledger
description: Per reviewed area, the commit verified and what passed; re-review only the diff since.
metadata:
  type: project
---
## CookieCore `core-gamemode` (2026-10-27), VERDICT READY
Review doc: `docs/reviews/2026-10-27-core-gamemode.md`. Coder branch merged at `5be0617`; verified at `5f453ed` (tester tests and deprecation fix) with the docs commit `8374d9a`.
Areas: `gamemode/` (all 8 classes: GameModes, GameModeCommand, GameModeArgument, PlayerTargets, FlyCommand, FlyTracker, SpeedCommand, SpeedScale), `Perm.java`, `paper-plugin.yml` (gamemode/fly/speed nodes), `CookieCore.java` registration lines, `messages.yml` gamemode/fly/speed keys, `docs/architecture.md`.
Tests: 137 before, 160 after (+23, 0 skipped); `mise run check` BUILD SUCCESS.
Open LOW: L-1 `FlyTracker.java:33` `Player#isOnGround()` deprecated (use the `Entity` method), L-2 unknown mode/number errors no `ui_error`, L-3 `/gamemode` single player (`@a` refused, decision), L-4 `others` also needs the mode node (decision, stricter than brief), L-5 vanilla `/minecraft:gamemode` ungated by our nodes (by design), L-6 permission revocation only on next mode switch, L-7 `fly`/`speed` label takeover not logged, L-8 English command descriptions, L-9 kick → `PlayerQuitEvent` unverified (in-game step 15; MEDIUM if it fails), L-10 `/fly` off mid-air fall damage.
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
