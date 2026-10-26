# Plugin-tester memory (CookieCore)

- [Review ledger](reviews-ledger.md) — core-gamemode READY at 5f453ed (coder 5be0617; 10 LOWs incl. L-1 deprecated isOnGround); core-tidy READY; core-foundation READY
- [Test techniques](test-techniques.md) — server-free tests: Proxy senders, FakePlayer, VanillaArgumentProvider stand-in (subtype + package), javac 25.0.4 crash with 2-arg PlayerQuitEvent
- [Environment gotchas](env-gotchas.md) — mise run check/test work in worktrees; cd /c/... not C:\\; rm -rf gate even for no-ops
- [sources.md](sources.md) — verified javap facts (PluginMeta, MavenLibraryResolver, Commands.register, VanillaArgumentProvider, PlayerQuitEvent, isOnGround deprecation)
