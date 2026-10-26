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
