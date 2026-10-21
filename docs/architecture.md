# Architecture

Maintained by the plugin-tester (Gandalf). Last updated: 2026-10-21 (`core-foundation` review, code at `cd76015`).

**Target:** Paper 26.3 (`26.3.build.205-stable`, pinned), Java 25, Maven, `api-version: '26.3'`. Same toolchain and rules as CookieClicker. Paper, Adventure/MiniMessage and the JDBC drivers (H2 2.5.252, MariaDB 3.5.10) are `provided`: nothing is shaded, Paper downloads the drivers from `plugin.yml` `libraries:` (versions are kept in sync with `pom.xml` by `PluginDescriptorTest`). The jar contains only `dev/casperrs/cookieCore/**` and the four resource files.

CookieCore is the server's base layer. Gameplay lives in CookieClicker; Core never depends on it. Other plugins use Core only through `dev.casperrs.cookieCore.api` (`CookieCoreApi`, via Bukkit's `ServicesManager`). Everything else is internal.

## Packages (`dev.casperrs.cookieCore`)
| Package | Role | Threading |
|---|---|---|
| (root) `CookieCore` | the only `JavaPlugin`: wiring and lifecycle (enable, reload, disable), main-thread hand-off (`runOnMainThread`). Max 200 lines (rule). `CoreService` = the `CookieCoreApi` implementation. `Perm` = every permission node as a constant (must match `plugin.yml`) | main |
| `api` | `CookieCoreApi`, `PlayerLookup`, `PlayerRef`. Only `java.*` types: no Bukkit, Brigadier, LuckPerms or storage types | any (reads memory) |
| `config` | `ConfigLoader` (enable and reload of `config.yml`, `messages.yml`, `sounds.yml`; a bad file keeps the last good values), `PluginSettings` (validated `config.yml`, bad values reported and replaced by defaults), `YamlFiles` | main (reload reads three small files) |
| `text` | `Messages`: the only place that turns `messages.yml` text into components (MiniMessage; placeholders `unparsed`, `<prefix>` is the one trusted `parsed` value). No player text anywhere else | main |
| `sound` | `SoundService` (the only place that plays sounds; events from `sounds.yml`, vanilla keys), `SoundConfigParser` (pure), `SoundEvents` (event names) | main |
| `command` | `CommandKit` (Brigadier building blocks: `literal(name, permission)`, `checked`, `knownPlayer`, `onlinePlayer`, `duration`, `reason`, `fail/done/say`), `CookieCoreCommand` (`/cookiecore version\|reload`), `KnownPlayerArgument` and `DurationArgument` (`CustomArgumentType.Converted` over `word()`), `DurationParser` (pure) | main |
| `player` | `PlayerDirectory` (case-insensitive name to UUID cache over the `players` table, `lookup` for misses) and `PlayerListener` (join and quit call `directory.seen`) | cache reads: any thread; DB: IO thread; futures complete on main |
| `storage` | `PlayerStore` / `JdbcPlayerStore` (H2 and MariaDB, plain SQL), `SchemaMigrator` + `Migrations` (versioned schema, `<prefix>schema_version`), `DatabaseSettings`, `DriverConnectionFactory`, `StorageThread` (the single IO thread). Leaf package: no Bukkit, no other Core package | IO thread only |

Dependency direction (enforced, no cycles): `root -> {command, player, sound, text, config, storage, api}`, `command -> {player, sound, text, config, api, Perm}`, `config -> {sound, text, storage}`, `player -> {storage, api}`, `storage` and `api` import nothing from Core. `Perm` is a constants leaf in the root package and exempt from the cycle check.

## Storage
One table so far: `<prefix>players(uuid PK, name, first_seen, last_seen)` (default prefix `core_`), schema version 1 in `<prefix>schema_version`. Each migration is a list of statements run in one transaction together with the version bump (MariaDB auto-commits DDL, so every statement is `IF NOT EXISTS`). A database written by a newer plugin is refused. Migrations are append-only: a feature adds exactly one list to `Migrations.all`.

## Threading
- **Main thread:** Bukkit/Paper API, commands, messages, sounds, listeners, reload.
- **One IO thread** (`StorageThread`, daemon, `CookieCore-Storage`): every JDBC call, in order, one connection. Nothing else creates threads.
- **`PlayerDirectory`:** `byName`/`byId` are `ConcurrentHashMap`s read without locks (commands, suggestions); writes (`seen`, warm-up merge, lookup merge) are `synchronized` together with the `live` set, so a live join is never overwritten by an older database row. `lookup(name)` answers from memory, else asks the IO thread and completes the future through the main-thread executor.
- No `Future.get()` / `CompletableFuture.join()` in production code (enforced).
- If the database cannot be opened, Core logs once and runs memory-only (`storageAvailable()` false, joins are cached but not saved, `lookup` answers from memory).

## Enable / disable order
Enable (each step needs the one before): `ConfigLoader` (settings, messages, sounds; broken files fall back to the jar copy and are reported) -> `StorageThread` + `JdbcPlayerStore` -> `PlayerDirectory.start()` (migrate and warm the cache on the IO thread; enable does not wait for it) -> listeners (+ `seen` for players already online after a `/reload`) -> Brigadier registration (`LifecycleEvents.COMMANDS`) -> `CookieCoreApi` into the `ServicesManager`, last.

Disable (reverse; each step wrapped in `safely` so one throw never skips the next): unregister the API -> `directory.shutdown()` (queues `store.close()` behind the pending writes) -> `StorageThread.shutdown` (waits `database.shutdown-timeout-seconds`, default 10, then drops what is left and logs it). `isEnabled()` is already false inside `onDisable`, so `runOnMainThread` drops late tasks (see review finding F-03).

## Config, messages, sounds, permissions
- `config.yml`: `server.name`, `database.*` (`database.*` needs a restart; reload reports a change). `messages.yml`: MiniMessage, keys listed in `Messages.REQUIRED_KEYS`; defaults come from the jar copy so a key added in an update still resolves, and the admin is told which keys their file lacks. `sounds.yml`: event -> `key`, `fallback`, `volume`, `pitch-min/max`, `source`; contract events `ui_confirm`, `ui_error`, `ui_notify` are vanilla keys (Core ships no resource pack).
- Permissions are plain Bukkit nodes `cookiecore.<area>.<action>`, declared in `plugin.yml` and as `Perm` constants; `cookiecore.admin.*` lists all admin nodes. `PluginDescriptorTest` keeps the three in sync. LuckPerms is a soft dependency and not used yet; its classes may only live in a `luckperms/` package (rule).

## Rules (enforced by `ArchitectureRulesTest`, a source scan; run against a broken copy with `-Dcookiecore.mainSources=<dir>` to see it fail)
- No `playSound` / `Sound.sound` outside `sound/`; no `spawnParticle` anywhere (Core has no effects engine).
- No Bukkit/Paper/Adventure/Brigadier import in `storage/`, and `storage/` imports no other Core package; JDBC only in `storage/`.
- No player-facing text built or sent outside `text/` (`sendMessage`, `Component.text`, `MiniMessage`, `LiteralMessage`, kick, title, action bar, ...); message calls take a dotted key, never a sentence; `Placeholder.parsed` exists once (the prefix).
- No NMS, CraftBukkit, reflection into the server, WorldGuard (`com.sk89q`), legacy `ChatColor`/`§`, `PlayerLoginEvent`, `AsyncPlayerChatEvent`, `System.out`, `printStackTrace`, hardcoded world names.
- No `join()` / `Future.get()` / `Thread.sleep`; threads, executors and async tasks only in `StorageThread`; scheduler only in `CookieCore`.
- `api/` imports only `java.*`; no mutable static fields; no static collections of Bukkit objects; `net.luckperms` only in `luckperms/`; no package cycles.
- Tests (not source scan): `plugin.yml` <-> `Perm` <-> `pom.xml` <-> `sounds.yml` <-> `messages.yml` agree, every message parses with a strict MiniMessage, every message key used in code exists.

## Tests (`src/test`)
Plain JUnit 6, no server. `TestSupport` builds `java.lang.reflect.Proxy` stand-ins for `CommandSender`/`Player`/`CommandSourceStack`; `PlainMessageSerializer` (registered through `META-INF/services` in the test resources) replaces the server's `MessageComponentSerializer`; commands are driven through a plain Brigadier `CommandDispatcher`; storage tests use `jdbc:h2:mem:`. Not covered without a server: `ConfigLoader` (needs `JavaPlugin`), `ArgumentTypes.player()` / `CommandKit.onlinePlayer`, Paper's own wrapping of custom arguments, listeners, the main class. Those are the in-game checks in the PR.
