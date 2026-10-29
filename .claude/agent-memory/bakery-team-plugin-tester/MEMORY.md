# Plugin-tester memory (CookieCore)

- [Review ledger](reviews-ledger.md) — core-tab NOT READY (M-1 rank order, 2026-10-29; re-review the M-1 fix); core-chat-join READY round 2 (open LOWs); core-moderation-mutes/bans/warns, core-scoreboard, core-gamemode, core-tidy, core-foundation
- [Test techniques for CookieCore](test-techniques.md) — server-free tests: Proxy senders/plugins, FakePlayer and ChatFixture, Brigadier dispatcher, JavaPlugin seams, VanillaArgumentProvider stand-in, ModerationFixture, join/quit event ctors, tab fake sink and TabService ctor, timing without JMH
- [Environment gotchas](env-gotchas.md) — mise blocked from Bash 2026-10-29: use mvn.cmd with JAVA_HOME (temurin-25.0.4) and the mise Maven; single-class mvn run; sleep blocked; cd /c/... not C:\; rm -rf gate (rm -f ok); worktree branch switch for tester
- [sources.md](sources.md) — verified lookups: paper-api jar (PluginMeta, MavenLibraryResolver, Commands.register, VanillaArgumentProvider, PlayerQuitEvent, PlayerJoinEvent, AsyncChatEvent, isOnGround, AsyncPlayerPreLoginEvent ctors, Player tab methods not deprecated), MiniMessage lenient vs strict probe
