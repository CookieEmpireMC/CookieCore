---
name: test-techniques
description: How CookieCore tests run without a server (no MockBukkit in pom); reuse for later phases.
metadata:
  type: reference
---
Verified 2026-10-26 in `src/test/java/dev/casperrs/cookieCore/` (stable while the pom has no MockBukkit; JUnit is 6.1.3 in the pom):
- `TestSupport`: `java.lang.reflect.Proxy` stand-ins for `CommandSender`/`Player` (records `sendMessage(Component)`, `playSound(Sound,..)`, `hasPermission(String)`) and `CommandSourceStack`. Works because Audience default methods are intercepted too.
- Real command trees run in a plain Brigadier `CommandDispatcher<CommandSourceStack>`: `Commands.literal/argument` are pure statics. `ArgumentTypes.*` and `MessageComponentSerializer.message()` need the server; the latter is a `ServiceLoader` lookup, so `src/test/resources/META-INF/services/io.papermc.paper.command.brigadier.MessageComponentSerializer` + `PlainMessageSerializer` fixes it.
- `YamlConfiguration` works without a server (`loadFromString`), BUT dotted keys (permissions) are split into sections: parse `paper-plugin.yml` with SnakeYAML (`new org.yaml.snakeyaml.Yaml().load`) instead.
- `paper-plugin.yml` and `cookiecore-libraries.properties` are Maven-filtered: `${version}` and `${h2.version}` resolve in `target/classes`; check the built jar with `unzip -p`.
- The loader's libraries are testable through `CookieCoreLoader.libraries()` (package-private static, same package as the descriptor test).
- H2 in memory: `jdbc:h2:mem:<uuid>;DB_CLOSE_DELAY=-1`; H2 DDL implicitly commits, so test rollback with DML + a bad statement, not CREATE TABLE.
- ArchitectureRulesTest is a source scan with a `-Dcookiecore.mainSources=<dir>` override: copy src/main/java to the scratchpad, add violations (multi-line files, imports must start a line), run `mise exec -- mvn -B -q test -Dtest=ArchitectureRulesTest -Dcookiecore.mainSources=<dir>` to prove the rules bite. Trimmed to 10 tests on 2026-10-26; the rules are the same, merged by topic.
- Run @Disabled tests once to confirm they fail: `mvn test -Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition -Dtest=...` (surefire passes it through).
- Timing a hot path: write a throwaway test under src/test, print with System.out, run with `-q`, delete it, never commit it.
- Perf harness for PlayerDirectory (throwaway `TmpPerfTest`, in-memory PlayerStore returning N PlayerRecords, `Runnable::run` executors, 2000-call JIT warm-up, then average 200 calls): N=500k `namesStartingWith` about 0.005-0.008 ms. Delete afterwards.
- `ConfigurationSection#getInt(path, def)` returns `def` for a YAML string even if it holds a number (`port: '3308'`), so parse the value yourself. `PluginSettings.intOrDefault` now does; unquoted `3308.5` still truncates (follow-up L-1).
- Bash tool: heredocs with backticks/apostrophes in markdown or Java text break ("unexpected EOF"); write the text with the Write tool.
- `Plugin` is an interface: a `Proxy` answering `getName` (identity equality via `proxy == args[0]`) is enough for `PlaceholderRegistry` tests (2026-10-27).
- Classes that take a `JavaPlugin` (`ScoreboardConfig`, `ConfigLoader`) cannot be tested: data folder, `getResource`, `saveResource` need the server. Ask the coder for a supplier seam instead (M-1 in the core-scoreboard review, 2026-10-27).
- `Sidebar`, `SidebarService`, `SidebarPreferences` need Bukkit's scoreboard manager or the scheduler; no unit test without MockBukkit (not in the pom). Review them by reading, list the in-game checks.
- Hot-path timing without JMH (2026-10-27, throwaway test, deleted): `Template.render` for 16 templates with 5 keys each takes about 0.10 ms warm, 0.23 ms cold, on the dev machine. Enough to size a per-second cost (PERF-LOW-1 core-scoreboard).

- Verified 2026-10-27 (core-gamemode), stable for 26.3.build.205:
  - `gamemode/FakePlayer`: a `Player` proxy with mode, allowFlight, flying and speed state. `setGameMode` fires `PlayerGameModeChangeEvent` through a hook first, then drops flight in survival and adventure like the server. Use it for tracker tests (`FlyTrackerTest`) and command tests.
  - `ArgumentTypes.player()` needs the server. The stand-in `io.papermc.paper.command.brigadier.argument.TestVanillaArgumentProvider` (test sources, Paper's package because `VanillaArgumentProvider` is package-private) is named in `src/test/resources/META-INF/services/io.papermc.paper.command.brigadier.argument.VanillaArgumentProvider`. It must be a real subtype: `findFirst()` fails with "not a subtype" otherwise, and a static `provider()` factory is not enough. It implements all 37 methods, returning null except `player()`/`players()` (a `PlayerSelectorArgumentResolver` proxy that resolves names from a static map).
  - `CommandSourceStack#getExecutor()` returns `org.bukkit.entity.Entity`, not `CommandSender`: `TestSupport.source(sender, executor)` takes an Entity; the console gets null.
  - javac 25.0.4 crash: test-compile dies with "Cannot load from object array because this.hashes is null" when a test calls `new PlayerQuitEvent(player, Component)` (2-arg). The 3-arg `(player, Component, QuitReason.DISCONNECTED)` compiles. To find such a crash, move test files one group at a time to the scratchpad and `mvn test-compile`.
  - `PlayerGameModeChangeEvent(Player, GameMode)` is deprecated for removal in 26.3; use `(Player, GameMode, Cause.PLUGIN, null)`.
  - `TestSupport.noStore()` is the shared empty `PlayerStore` (moved from `CommandTreeTest`).

Verified 2026-10-27 (core-moderation-bans), stable for 26.3.build.205:
  - Moderation tests use `moderation/ModerationFixture`: real `ModerationService`/`BanList`/`JdbcPlayerStore` on H2 through a `DriverManager` lambda (the storage test helper `H2` is package-private, so do not import it from another package). Synchronous executors; `deferStorage = true` queues the IO tasks for busy-set tests. `TestPlayer` is a Player proxy with a kick log and an online flag.
  - `ModerationService` answers `SINGLE_SUCCESS` before the database write finishes (asynchronous), so assert the outcome through messages, sounds, the ban list or the table, not the return value.
  - Vanilla selector for `/kick`: put the Player in the public static `io.papermc.paper.command.brigadier.argument.TestVanillaArgumentProvider.ONLINE` map.
  - Brigadier: `/x Steve -s` takes the `-s` literal over a greedy reason (the flag wins); `-sx`, `-S` and a trailing `-s` in a reason are text. Verified with the shipped Brigadier.
  - `AsyncPlayerPreLoginEvent`: the 2-, 3- and 4-arg constructors reach `Bukkit.createProfile` (NPE without a server); the 5- and 6-arg are `[removal]` in 26.3 (javac warning in `mise run check`). Use the 8-arg `(name, address, SocketAddress, uuid, transferred, PlayerProfile, hostname, PlayerLoginConnection)` with Proxy stand-ins for the profile (`getId`, `getName`) and the connection (javac probe, 2026-10-27: no warning).
  - Key scan in `MessagesTest` skips comment lines (Javadoc examples name keys too).
  - `ModerationModule` runs in tests with `Runnable::run` executors: the login-triggered retry then completes inside the refused login, so the next login sees the loaded bans (2026-10-27).
