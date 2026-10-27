# Plugin-tester memory (CookieCore)

- [Review ledger](reviews-ledger.md) — core-scoreboard READY (round 2); core-gamemode READY at 5f453ed (10 LOWs incl. L-1 deprecated isOnGround); core-tidy READY; core-foundation READY
- [Test techniques for CookieCore](test-techniques.md) — server-free tests: Proxy senders/plugins, FakePlayer, Brigadier dispatcher, JavaPlugin seams, VanillaArgumentProvider stand-in, timing without JMH
- [Environment gotchas](env-gotchas.md) — mise run check ~22 s; single-class mvn run; sleep blocked; cd /c/... not C:\; rm -rf gate even for no-ops; ownership-check fails here
- [sources.md](sources.md) — verified lookups: paper-api jar (PluginMeta, MavenLibraryResolver, Commands.register, VanillaArgumentProvider, PlayerQuitEvent, isOnGround), MiniMessage lenient vs strict probe
