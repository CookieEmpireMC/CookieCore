---
name: reviews-ledger
description: Per reviewed area, the commit verified and what passed; re-review only the diff since.
metadata:
  type: project
---
## CookieCore `core-foundation` (2026-10-21), VERDICT READY
Reviewed at `cd76015` (feature/core-foundation after merging plugin-coder). Review doc: `docs/reviews/2026-10-21-core-foundation.md`.
Areas and result: CookieCore main class, CoreService, Perm, api/, command/, config/, player/, sound/, storage/, text/, plugin.yml, config/messages/sounds.yml, pom.xml, mise.toml, CI workflow. Thread-safety of PlayerDirectory OK (stress test). Ownership of coder commits OK (checked by hand).
Open findings for the coder: F-03 lookup futures hang at disable / RejectedExecution, F-04 RuntimeException escapes start(), F-01 PERF-MEDIUM suggestions O(N), F-06 reload returns success on problems, F-07 password in record toString, F-08 cached(null UUID) NPE, F-09 silent non-numeric config, F-05 onDisable no seen (unverified), F-02 ownership-check cannot run (no .claude/hooks), F-10/F-11 info.
7 `@Disabled` tests carry the finding ids (F-03 x3, F-04 x2, F-07, F-08): remove @Disabled after the coder's fix, re-review only `git diff cd76015..HEAD -- src/main pom.xml mise.toml`.
Tests: 209 run, 7 skipped, `mise run check` green.
