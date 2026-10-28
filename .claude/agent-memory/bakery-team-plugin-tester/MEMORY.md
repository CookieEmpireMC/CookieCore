# Plugin-tester memory (CookieCore)

- [Review ledger](reviews-ledger.md) — core-moderation-warns NOT READY (M-1, 2026-10-28); core-moderation-bans round 2 READY (conditional on boot-outage decision); core-scoreboard READY; core-gamemode READY; core-tidy, core-foundation READY
- [Test techniques for CookieCore](test-techniques.md) — server-free tests: Proxy senders/plugins, FakePlayer, Brigadier dispatcher, JavaPlugin seams, VanillaArgumentProvider stand-in, ModerationFixture (H2 + real services), login event ctors, timing without JMH
- [Environment gotchas](env-gotchas.md) — mise run check ~22 s; single-class mvn run; sleep blocked; cd /c/... not C:\; rm -rf gate even for no-ops; ownership-check fails here
- [sources.md](sources.md) — verified lookups: paper-api jar (PluginMeta, MavenLibraryResolver, Commands.register, VanillaArgumentProvider, PlayerQuitEvent, isOnGround, AsyncPlayerPreLoginEvent ctors), MiniMessage lenient vs strict probe
