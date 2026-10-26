---
name: reviews-ledger
description: Per reviewed area, the commit verified and what passed; re-review only the diff since.
metadata:
  type: project
---
## CookieCore `core-scoreboard` (2026-10-27), round 2 VERDICT READY
Round 2 (coder fix `1c85bb8`, merged `965e853`, tester branch `7a7ca6d`): M-1 resolved (ScoreboardConfig takes FileSource + jar supplier; ScoreboardConfigTest 3), L-1 resolved (close() in refresh catch). Open LOW: L-4 one line in scoreboard.yml (outer gray still open; strict test @Disabled until fixed). Suite 160 run, 0 failures, 1 skipped. Re-review scope next time: `git diff 7a7ca6d..HEAD -- src/main` (L-4 line, L-2/L-3/L-5 if fixed).

## CookieCore `core-scoreboard` round 1 (2026-10-27), VERDICT NOT READY (branch review, plugin-coder)
Review doc: `docs/reviews/2026-10-27-core-scoreboard.md`. Coder code reviewed at `61fa3bb` (src/main untouched by me); tester commits `0624e44..34bd8d7`.
Areas checked by reading: `scoreboard/*` (SidebarService, Sidebar, SidebarDiff, BoardSink, SidebarPreferences, PlaceholderRegistry, ScoreboardConfig, ScoreboardLayout, CompiledLayout, ScoreboardModule), `text/Template`, `log/WarnOnce`, `api/ScoreboardPlaceholders`, `storage` v2 (`JdbcPlayerStore` sidebar methods, `Migrations`), `command/ScoreboardCommand`, CookieCore enable/reload/disable wiring, `PluginSettings.scoreboardEnabled`, `paper-plugin.yml`, `messages.yml`, `scoreboard.yml`.
Passed: main-thread rule, task lifecycle, leaks on quit/disable, no setScoreboard on quit, no per-tick reset (flicker), registry thread safety, WarnOnce thread safety, Template never parses values, storage v2 SQL.
Open: M-1 (MEDIUM, to coder) `ScoreboardConfig` has no seam: keep-last-good test impossible. LOW L-1..L-7, PERF-LOW-1 (every line re-renders when one value changes, ~0.1 ms per viewer per change).
Tests: 137 run / 5 failing before; 156 run / 0 failing after (`mise run check` BUILD SUCCESS, 21.7 s). Next re-review: `git diff 61fa3bb..HEAD -- src/main` for the M-1 seam, then add the three ScoreboardConfig tests.

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
