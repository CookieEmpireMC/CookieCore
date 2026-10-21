---
name: reviews-ledger
description: Per reviewed area, the commit verified and what passed; re-review only the diff since.
metadata:
  type: project
---
## CookieCore `core-foundation` (2026-10-21), VERDICT READY (twice)
Review doc: `docs/reviews/2026-10-21-core-foundation.md` (sections 1-8 first pass at `cd76015`, section 9 fix pass at `0786282`).
First pass areas: CookieCore main class, CoreService, Perm, api/, command/, config/, player/, sound/, storage/, text/, plugin.yml, config/messages/sounds.yml, pom.xml, mise.toml, CI workflow.
Fix pass (`0786282`, branch `core-foundation/plugin-tester-2`): F-01, F-03..F-09 verified fixed (F-05 fix correct, Paper event order unverifiable without server). Tab completion measured 5-15 us at 10k..500k names. 230 tests, 1 skipped (F-12).
Open: F-12 LOW quoted number `port: '3308'` ignored silently (PluginSettings.intOrDefault; disabled test `aQuotedWholeNumberIsEitherUsedOrReportedNeverIgnoredSilently`), F-13 INFO scheduler-cancelled lookup future, F-14 INFO unused `cachedPlayers()`, F-02 ownership-check cannot run (no .claude/hooks), F-10/F-11 INFO.
Next re-review: `git diff 0786282..HEAD -- src/main pom.xml mise.toml`.
