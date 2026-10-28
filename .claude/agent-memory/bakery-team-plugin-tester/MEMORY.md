# Plugin-tester memory (CookieCore)

- [Review ledger](reviews-ledger.md) — core-chat-join READY after round 2 (2026-10-28; open LOWs listed); core-moderation-mutes/bans/warns, core-scoreboard, core-gamemode, core-tidy, core-foundation (see the file)
- [Test techniques for CookieCore](test-techniques.md) — server-free tests: Proxy senders/plugins, FakePlayer and ChatFixture, Brigadier dispatcher, JavaPlugin seams, VanillaArgumentProvider stand-in, ModerationFixture (H2 + real services), login and join/quit event ctors, timing without JMH
- [Environment gotchas](env-gotchas.md) — mise run check ~20 s; single-class mvn run; sleep blocked; cd /c/... not C:\; rm -rf gate even for no-ops (rm -f ok); worktree branch switch for tester; ownership-check fails here
- [sources.md](sources.md) — verified lookups: paper-api jar (PluginMeta, MavenLibraryResolver, Commands.register, VanillaArgumentProvider, PlayerQuitEvent, PlayerJoinEvent, AsyncChatEvent, isOnGround, AsyncPlayerPreLoginEvent ctors), MiniMessage lenient vs strict probe
