# Review: core-foundation (plugin-coder work, merged at `cd76015`)

Reviewer: Gandalf (plugin-tester) · 2026-10-21 · branch `core-foundation/plugin-tester` (tests and docs only, no `src/main` change)

**VERDICT: READY** (to merge as a foundation). Nothing blocks: no PERF-HIGH, no data-loss or security bug, `mise run check` is green. Four coder follow-ups are listed below (F-01, F-03, F-04, F-06); they are small, covered by tests (the disabled ones turn green when the fix lands) and should be done before phase 1 builds on `PlayerDirectory.lookup`. F-02 needs the user.

## 1. Target version
Paper `26.3.build.205-stable` (pinned, no range), Java 25 (`mise.toml` `temurin-25`, `java.version` 25, `--release 25`), `api-version: '26.3'`, JUnit 6.1.3, surefire 3.6.0. No 1.21.x leftovers. Nothing shaded: the jar holds only `dev/casperrs/cookieCore/**`, `plugin.yml`, `config.yml`, `messages.yml`, `sounds.yml`; Paper/Adventure/H2/MariaDB are `provided`, the drivers come from `plugin.yml` `libraries:` (versions asserted equal to the `pom.xml` properties).

## 2. Test results
`mise run check` (clean verify): **209 tests, 0 failures, 0 errors, 7 skipped** (the 7 are `@Disabled` tests that document findings F-03 (3), F-04 (2), F-07, F-08; I ran them once with the disable condition off and all 7 fail as described). CI (`.github/workflows/ci.yml`) runs `mise run check`.

| Test class | Covers |
|---|---|
| `DurationParserTest` (31) | single and combined (`7d12h`, `1w2d`), case/whitespace/leading zeros, rejects (`""`, `7`, `7x`, `1h2d`, `1d1d`, `0s`, `-5m`, `1.5h`, `7d 12h`, `36501d`, >9 digits, null), `36500d` accepted, 9 digits no overflow |
| `SoundConfigParserTest` (14), `SoundServiceTest` (9) | full entry, defaults, missing/invalid fallback, bad entries skipped, swapped pitch, volume <= 0, NaN/infinity, source aliases; fallback vs custom key, pitch range, unknown event warned once, invalid key, console, reload resets warnings |
| `PluginSettingsTest` (15, 1 disabled) | defaults, shipped `config.yml`, each bad `database.type`/`table-prefix`/`port`/`server.name`/timeout reports one problem and uses the default, `withDatabase`, password not in `describe()` |
| `MessagesTest` (12) | placeholders unparsed (a `<click:run_command>` in a name shows literally), `<prefix>`, missing key returns path and warns once, jar defaults vs `problems()`, every `REQUIRED_KEYS` key in the shipped file, **every message parses with a strict MiniMessage**, every `"command.*"` key used in code exists |
| `PluginDescriptorTest` (12) | `plugin.yml` name/api-version/softdepend/main class, `Perm.ALL` = declared permissions = all public constants, `cookiecore.admin.*` children, `libraries:` = `pom.xml`, pom pins Paper, no shading, only `provided`/`test` scopes; `sounds.yml` parses clean, contract events `ui_confirm`/`ui_error`/`ui_notify` have the agreed vanilla key, volume and pitch; config contract keys |
| `PlayerDirectoryTest` (30, 5 disabled) | case-insensitive and trimmed lookup, rename, name taken over, two live players with one name, warm-up never overwrites a live join, old DB row cannot steal a live name, most recent wins, saves queued behind warm-up, failing save logged, rejected io executor, `lookup` hits the store only on a miss and caches, no negative cache, completes through the main executor, failure completes exceptionally, memory-only when `init` throws, shutdown ordering, 8-thread stress test against the warm-up |
| `SchemaMigratorTest` (9), `JdbcPlayerStoreTest` (18, 1 disabled), `StorageThreadAndSettingsTest` (7) | H2 `mem:`: fresh DB ends at 1, twice is a no-op and keeps data, refuses a newer version, bad statement rolls back the DML and leaves the version, fixed migration then runs, auto-commit restored; upsert keeps `first_seen`, truncation to 32, `loadAll` order, `findByName` case-insensitive/most recent/not a pattern/not injectable, reconnect after `close`, prefix handling, failures become `StorageException`; single named daemon IO thread, ordered, flush on shutdown, timeout drops and logs |
| `CommandTreeTest` (22) | the real `/cookiecore` tree and `knownPlayer`/`duration`/`reason` through a plain Brigadier `CommandDispatcher`: permission per literal, usage, version output, reload (clean and with problems, sounds, problem text literal), `CommandKit.checked`, parse of `mute notch 7d12h being very rude`, errors carry the configured messages, suggestions (prefix, case, sort, cap 200, durations) |
| `YamlFilesTest` (6) | missing, valid, malformed (precise message), directory, empty, `toMap` |
| `ArchitectureRulesTest` (24) | see `docs/architecture.md` "Rules". Each rule was exercised against a deliberately broken copy of the sources (`-Dcookiecore.mainSources`) and fails there |

Coverage gaps that need a server (to check in game, see section 6): `ConfigLoader` (needs a `JavaPlugin`), `ArgumentTypes.player()`/`CommandKit.onlinePlayer`, Paper's own wrapping of custom arguments (client validation and suggestions), the listeners and the main class. No MockBukkit is in `pom.xml`; I did not add one (would need a check that a release exists for 26.3).

## 3. Findings (most severe first)

### F-03 MEDIUM: `PlayerDirectory.lookup` futures can hang or throw at shutdown
`player/PlayerDirectory.java:140-155` with `CookieCore.java:130-134`.
- `runOnMainThread` silently drops the task when `isEnabled()` is false (and `isEnabled()` is already false inside `onDisable`). A lookup answered by the IO thread during disable never completes: `result.complete` sits in the dropped task. Whoever waits (`thenAccept` in a later `/ban`) is never called, and nothing is logged.
- Race: `isEnabled()` is true, then the plugin is disabled before `runTask` runs: the scheduler throws `IllegalPluginAccessException` inside the IO lambda. Only `StorageException` is caught there, so the exception escapes to the executor (swallowed) and the future never completes.
- `lookup()` after the IO executor was shut down throws `RejectedExecutionException` to the caller instead of returning a failed future.
Tests: `PlayerDirectoryTest.lookupFutureStillCompletesWhenTheMainThreadIsGone`, `lookupFutureCompletesExceptionallyWhenTheMainExecutorRejects`, `lookupAfterIoShutdownReturnsAFailedFuture` (`@Disabled`, F-03).
Fix (PlayerDirectory):
```java
CompletableFuture<Optional<PlayerRef>> result = new CompletableFuture<>();
try {
    io.execute(() -> {
        Optional<PlayerRef> found = null;
        Throwable error = null;
        try { found = store.findByName(name.trim()).map(this::merge); }
        catch (StorageException | RuntimeException e) { error = e; }
        completeOnMain(result, found, error);
    });
} catch (RejectedExecutionException e) {
    result.completeExceptionally(e);
}
return result;

private <T> void completeOnMain(CompletableFuture<T> f, T value, Throwable error) {
    try {
        main.execute(() -> { if (error != null) f.completeExceptionally(error); else f.complete(value); });
    } catch (RuntimeException gone) {          // plugin disabled / scheduler refused
        f.completeExceptionally(gone);         // completes on the IO thread, only in that case
    }
}
```
and `CookieCore.runOnMainThread`: when `!isEnabled()` throw `new RejectedExecutionException("CookieCore is disabled")` instead of returning silently.

### F-04 MEDIUM-LOW: an unchecked exception from the store escapes `PlayerDirectory.start()`
`player/PlayerDirectory.java:68-86` (only `StorageException` caught), `storage/JdbcPlayerStore.java:120-122` (`UUID.fromString`).
One corrupt `uuid` value makes `loadAll` throw `IllegalArgumentException`: the whole warm-up is lost, `storageUp` stays false (so joins are not saved either, silently), and on the real single-thread executor the exception is swallowed with no plugin log line (the `finally` still completes the future). Tests: `PlayerDirectoryTest.anUncheckedStoreFailureAlsoLeavesMemoryOnlyAndIsLogged`, `JdbcPlayerStoreTest.aCorruptRowIsReportedAsStorageExceptionOrSkipped` (`@Disabled`, F-04).
Fix: in `start()` also `catch (RuntimeException e)` with the same SEVERE log; in `JdbcPlayerStore.loadAll` skip (and count) rows whose uuid does not parse, or wrap in `StorageException`.

### F-01 PERF-MEDIUM: tab completion of `knownPlayer` is O(total known players) on the main thread
`command/KnownPlayerArgument.java:55-63`. Every keystroke streams the whole cache, lower-cases each name and sorts all matches before `limit(200)`. Measured here (single JIT-warm run, this laptop): 10,000 known players: 18 ms for `/x ` and 12 ms for `/x p`; 100,000: 157 ms; 500,000: 0.8 s; a longer prefix is 5 to 7 times cheaper. The cost grows with every player that ever joined, not with players online, and a 50 ms tick is exceeded at roughly 25,000 names; it hits only the staff member typing, but the whole server stalls for that tick. Becomes PERF-HIGH above about 10,000 known players.
Fix: make `byName` a `ConcurrentSkipListMap<String, PlayerRef>` (lower-cased key; `get/put/remove(key, value)` work the same) and add `PlayerDirectory#namesStartingWith(String lowerCasePrefix, int limit)` returning `byName.subMap(prefix, prefix + Character.MAX_VALUE).values().stream().limit(limit)`: O(log N + 200), already sorted, no per-name lower-casing. `cachedPlayers()` can then be dropped from the public surface.

### F-06 LOW: `/cookiecore reload` returns success when it reported problems
`command/CookieCoreCommand.java:82`. The sender sees `reload-failed` plus each problem and hears the error sound, but Brigadier gets `SINGLE_SUCCESS`, so `/execute store`, command blocks and console automation cannot tell. The comment says this is deliberate ("the reload itself ran"). Suggest `return 0` when `problems` is non-empty (the messages are already sent). Test `CommandTreeTest.reloadWithProblemsListsEachAndPlaysTheErrorSound` currently pins the `1`; change the last assertion with the fix.

### F-07 LOW: the database password is in `toString()`
`storage/DatabaseSettings.java` (record) and `config/PluginSettings.java` (record holding it). `describe()` is careful, but `"" + settings` or a debug log of the settings prints `password=...`. Nothing logs it today. Fix: override `toString()` in `DatabaseSettings` to mask the password. Test: `PluginSettingsTest.toStringNeverContainsThePassword` (`@Disabled`, F-07).

### F-08 LOW: `PlayerLookup.cached((UUID) null)` throws NPE
`player/PlayerDirectory.java:130` (`ConcurrentHashMap.get(null)`); the `String` overload is null-safe. It is the public API, so be consistent: `return uuid == null ? Optional.empty() : ...`. Test: `cachedWithANullUuidIsEmpty` (`@Disabled`, F-08).

### F-09 LOW: non-numeric config values fall back silently
`config/PluginSettings.java:41,55`. `database.port: abc` or `shutdown-timeout-seconds: "ten"` go through `getInt(path, default)`, which returns the default for a non-number with no problem reported, unlike out-of-range numbers. Suggest `c.isInt(path)` check when `c.isSet(path)` and report `"is not a number"`. `PluginSettingsTest.nonNumericPortFallsBackToDefault` pins the current behaviour.

### F-05 LOW (UNVERIFIED): `onDisable` does not refresh `last_seen` of online players
`CookieCore.java:94-111`. If Paper fires `PlayerQuitEvent` after plugins are disabled on `/stop`, players online at shutdown keep their join time as `last_seen`. I could not verify the order for 26.3 from the Javadocs. Cheap and harmless fix: at the start of `onDisable`, before `directory.shutdown()`, `Bukkit.getOnlinePlayers().forEach(p -> directory.seen(p.getUniqueId(), p.getName()))` (queued behind nothing, ahead of the `close`). Check in game: stop the server with a player online and read `last_seen`.

### F-02 PROCESS (user): `mise run ownership-check` cannot run in this repo
`tools/ownership/check.py` needs `.claude/hooks/ownership_lib.py` (human-controlled), and CookieCore has no `.claude/hooks/` at all (so there is also no PreToolUse ownership hook). The orchestrator's merge gate therefore errors out here ("no .claude/ownership.json + .claude/hooks/ownership_lib.py found"). I verified by hand with CookieClicker's `ownership_lib.py` and CookieCore's `ownership.json` (temp copy outside the repo): the coder's three commits (`d051826..cd76015^2`) and my five commits on this branch are all inside their authors' globs. Needs human review: copy `.claude/hooks/` from CookieClicker (`ownership.py`, `ownership_lib.py`) and register the hook in `.claude/settings.json`.

### F-10 INFO: `ConfigLoader` is not unit-testable
`config/ConfigLoader.java` takes a `JavaPlugin` only for `getDataFolder`, `getResource`, `saveResource`. The "broken file keeps the last good values on reload" acceptance criterion is therefore verified only at file level (`YamlFilesTest`, `Messages`, `PluginSettings`) and has to be checked in game. If the loader took a small `ConfigFiles` interface (data folder, resource stream, save default) the criterion could be tested without a server. Optional.

### F-11 INFO (UNVERIFIED, untestable): `CommandKit.onlinePlayer` uses `getFirst()`
`command/CommandKit.java:112`. `PlayerSelectorArgumentResolver.resolve(...)` may return an empty list (for example `@p` with nobody online) instead of throwing; `getFirst()` would then throw `NoSuchElementException` (Brigadier prints a generic error). Defensive fix: check `isEmpty()` and `fail(ctx, "command.unknown-player", ...)`. Verify in game with `@p` from the console once a command uses it.

### Other observations (no action needed)
- `PlayerDirectory.merge` does not add an older stored player to `byId` when a live player already holds that name, so `cached(uuid)` for that old player is empty until they join. Harmless; mention if a later feature (history) needs UUID lookups of old holders.
- `live` (the set that protects joins from the warm-up) never shrinks; it is bounded by distinct players seen since the restart.
- `Perm` sits in the root package, so `command -> Perm -> (root)` is a package cycle by import; it is a constants leaf and exempted in `ArchitectureRulesTest`. Moving it to a `permission/` package would remove the exemption.
- Missing keys in a user's `messages.yml` after an update are reported at every enable and every reload as problems ("using the built-in default"), so `/cookiecore reload` keeps showing the red failure message until the admin merges the new keys. Intended, but consider downgrading that one to a log line.
- The reply for the contract keys matches the brief: `command.no-permission`, `unknown-player`, `reloaded`, `reload-failed`, `version`, plus `usage`, `invalid-duration`, `reload-problem`, `storage-up/-down`.

## 4. Converted Brigadier arguments (checked against the research note)
`KnownPlayerArgument` and `DurationArgument` are `CustomArgumentType.Converted<_, String>` over `StringArgumentType.word()`, exactly as `docs/research/core-paper-apis.md` section 1 prescribes. Confirmed with `javap` on the 26.3.build.205 jar: `Converted#parse(StringReader)` is `convert(getNativeType().parse(reader))`, so `convert(String)` receives one word; `listSuggestions` is the overridable default on `CustomArgumentType`; `Commands.literal/argument` are plain Brigadier builders (that is why the trees can be tested with a normal dispatcher). `MessageComponentSerializer.message()` resolves its provider through `ServiceLoader`, which is why the error messages are only built lazily at `create(...)` (fine) and why the test classpath registers a stand-in provider. Behaviour verified in `CommandTreeTest`: lookup is case-insensitive and cache-only (no DB, no Mojang call), unknown names and bad durations produce the configured messages with the input as `unparsed` text, the greedy reason keeps spacing and MiniMessage tags literally, suggestions are capped at 200. **Not verifiable without a server:** that Paper requests the server-side suggestions for these custom nodes and which thread `convert`/`listSuggestions` run on (both are safe: only the thread-safe cache is touched, no Bukkit call).

## 5. Architecture and performance
Architecture is clean and matches the brief: one `JavaPlugin` that only wires (143 lines), leaf `storage` and `api` packages, no cycles apart from the `Perm` leaf, no static state, no player objects held in fields, no tasks to cancel, every disable step wrapped and the storage flush last. `docs/architecture.md` is written. `PlayerDirectory` thread-safety: the `live` `HashSet` is only touched inside `synchronized merge`/`remember`; the maps are concurrent; an 8-thread stress test against a concurrent warm-up leaves a consistent index (no stale name resurrected, no lost rename). No problem found.

**Performance** (paragraph): hot paths are join/quit (one `seen`: a short `synchronized` block, one map update, one queued task; IO side one `isValid(2)` ping, one `UPDATE`, plus an `INSERT` for a new player: negligible even in a join storm) and staff tab completion (F-01). Nothing runs per tick, nothing per online player per second, no entities, no packets, no autosave. Main-thread file reads happen only on `/cookiecore reload` (three small YAML files). Other scaling notes, all LOW: `loadAll` loads every row ever stored into memory at enable (a few hundred bytes per player; fine to about 100k players, then consider capping the warm-up to the most recent N and rely on `lookup`), `findByName` uses `LOWER(name) = ?` (full scan, only on a cache miss), `JdbcPlayerStore.connection()` pings the connection before every operation (one extra round trip per operation on MariaDB).

## 6. In-game test guide (for the PR)
1. Boot on Paper 26.3: clean log, `Using H2 at ...`, `Loaded 0 known player(s)`, `Enabled`; `plugins/CookieCore/{config,messages,sounds}.yml` and `data/core.mv.db` appear.
2. As op: `/cookiecore`, `/cookiecore version` (version, server name, `h2`, `connected`), `/cookiecore reload` (green message + pling). Without the permission: the command is not completable and does nothing.
3. Break `messages.yml` (tab indentation), `/cookiecore reload`: red count + the YAML error line + bass sound, old messages still work; fix it, reload is green. Set `database.port: 0`: one reported problem. Change `database.type`: "restart the server" problem.
4. Join with two accounts, rename one (or edit the row), restart: read the `core_players` table; `first_seen` unchanged, `last_seen` updated on quit. Stop the server with a player online and check `last_seen` (F-05).
5. Switch to MariaDB (`database.type: mariadb`) once: same behaviour; stop the database and restart Core: SEVERE "Database unavailable ... memory only", `/cookiecore version` shows `memory only`, joins still work.
6. Listen to the three vanilla UI sounds (pling 1.4, bass 0.8, experience orb 1.2).
7. **spark:** `/spark profiler start --timeout 60` while 5 to 10 people (or bots) join and leave and staff mash tab on a test command using `knownPlayer`; then check the profile: `dev.casperrs.cookieCore.*` should stay far below 0.1% of the tick, and `KnownPlayerArgument.listSuggestions` under 1 ms with a few thousand known players (if it shows more, F-01).

## 7. Ledger
Reviewed at `cd76015` (feature/core-foundation after merging the coder): `CookieCore`, `CoreService`, `Perm`, `api/`, `command/`, `config/`, `player/`, `sound/`, `storage/`, `text/`, `plugin.yml`, `config.yml`, `messages.yml`, `sounds.yml`, `pom.xml`, `mise.toml`, CI workflow. Later reviews: `git diff cd76015..HEAD -- src/main pom.xml mise.toml` and re-review only that.

## 8. Hand-off
- **plugin-coder (cleanup pass):** F-03, F-04, F-01 (the three that change behaviour or cost), then F-06, F-07, F-08, F-09, optionally F-05, F-10, F-11. The `@Disabled` tests are mine: after a fix, tell me; I remove the `@Disabled` and re-review only the diff.
- **user:** F-02 (hooks for `ownership-check`), the in-game checks above, `mise.toml`/`.gitattributes` human review already listed in the brief.
