---
name: reviews-ledger
description: Per reviewed area, the commit verified and what passed; re-review only the diff since.
metadata:
  type: project
---
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
