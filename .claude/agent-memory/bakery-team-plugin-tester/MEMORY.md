# Plugin-tester memory (CookieCore)

- [Review ledger](reviews-ledger.md) — core-scoreboard NOT READY (M-1: ScoreboardConfig seam, 61fa3bb); core-tidy READY at 5873b9c; core-foundation READY
- [Test techniques for CookieCore](test-techniques.md) — server-free tests: Proxy senders/plugins, Brigadier dispatcher, JavaPlugin seams, timing without JMH
- [Environment gotchas](env-gotchas.md) — mise run check ~22 s; single-class mvn run; sleep blocked; rm -rf gate; ownership-check fails here
- [sources.md](sources.md) — verified lookups: paper-api jar (PluginMeta, MavenLibraryResolver), MiniMessage lenient vs strict probe (2026-10-26)
