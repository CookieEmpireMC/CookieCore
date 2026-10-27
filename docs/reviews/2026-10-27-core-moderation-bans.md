# Review: core-moderation-bans (phase 4a)

**Reviewer:** Gandalf (plugin-tester) · **Date:** 2026-10-27
**Branch reviewed:** `core-moderation-bans/plugin-coder` at `951091b` (DONE, coder stopped)
**Tester branch:** `core-moderation-bans/plugin-tester` (tests and this review)
**Target:** Paper 26.3 (`paper-api 26.3.build.205-stable`), Java 25. `api-version` and `mise.toml` as in `docs/architecture.md`.

**VERDICT (round 2): READY**, with one decision for Rick to confirm (section 9: a database that cannot be opened at boot still opens logins, as memory-only mode). Round 1 was NOT READY on M-1 only.

## 1. Target version
Paper 26.3.build.205-stable. Every Bukkit/Paper call the feature uses was checked against the API jar (`javap`): `AsyncPlayerPreLoginEvent` constructors (the 2-, 3- and 4-argument ones reach `Bukkit.createProfile`, the 5- and 6-argument ones are `[removal]` in 26.3, so the tests use the 8-argument form with stand-ins), `disallow(Result, Component)`, `Player.kick(Component, PlayerKickEvent.Cause)`, `PlayerKickEvent.Cause.BANNED/KICKED`. No deprecated API used. No new dependency; nothing shaded.

## 2. Test results
- `mise run test` on the tester branch: **203 run, 0 failed, 1 skipped** (the skipped one is the existing `@Disabled` scoreboard test, unchanged). Baseline on the coder branch was 183 run, 1 skipped.
- **20 new tests**, all in existing or new classes, lean:
  - `moderation/ModerationCommandTest` (5): each command hidden and refused without its own node; the `-s` tail (8 inputs: nothing, reason, `-s`, `-s reason`, `-sx`, `-S`, reason ending in `-s`, `-s -s`); `/unban` completion lists active bans only (expired tempban excluded); tempban stored with its end, second ban refused with the end date, self-ban refused, `0s` and over 100 years refused; kick, ban, unban through the dispatcher, unban recorded inactive with who removed it.
  - `moderation/ModerationServiceTest` (5): failed write kicks nobody and changes nothing (sender gets error sound); busy target refused until the first write lands (one row); no database: kick still works and logs once, ban refused; public ban announced to all but the target, notify sound only for staff with the node; silent ban reaches only staff with the notify node, sender not alerted twice.
  - `moderation/BanListTest` (2): expired tempban ignored and kept, `put` replaces; start-up load keeps the longer ban.
  - `moderation/ModerationListenerTest` (2): active ban and running tempban refused with the screen, expired tempban and strangers allowed; nobody allowed before the bans load.
  - `text/MessagesTest` (+2): ban screen shows reason and staff unparsed (click tags show literally, no click event); tempban screen shows time left and UTC end, `TimeText` never shows zero. Scan regex now includes `moderation`; strict parse has the new placeholders. The key scan now skips comment lines (see L-2).
  - `storage/JdbcPlayerStoreTest` (+3): round trip with generated ids, kicks inactive, only bans load; unban deactivates every active row of one player (including an expired one) and records who; a corrupt punishment row is skipped with one warning and the others load.
  - `storage/SchemaMigratorTest` (+1): a v2 database upgrades in place to v3 and keeps its players; `core_punishments` starts empty.
- Test helpers: `moderation/ModerationFixture` (real services on in-memory H2 through `DriverManager`, synchronous executors, a deferred storage queue, `TestPlayer` proxies with a kick log) and the existing `TestVanillaArgumentProvider.ONLINE` map for `/kick`. `TestSupport.Recorder`, `TestSupport.console`, `TestSupport.source` reused.
- Not tested without a server (by design, see section 6): `ModerationModule.register` (label takeover and the missing-label warning), `ModerationModule.load` against a real `PlayerDirectory` start, `PlayerJoinEvent` re-check, Paper's real `Commands` alias for `/pardon`, real selector parsing.
- `mise run check` result: see section 8.

## 3. Findings

### MEDIUM (back to the coder)
**M-1. Banned players can join while the bans are not loaded (database down at start-up, or the load failed).**
`ModerationModule.java:60-74`. `load()` calls `bans.markLoaded()` on every path. When `directory.storageAvailable()` is false, nothing is logged by this module and logins are then allowed with no ban check. When the load throws, the failure is logged SEVERE and logins are allowed too. The brief's acceptance criterion is the opposite: "until the active bans are loaded at start-up, logins are denied with a short 'server is starting' screen instead of letting a banned player slip in". A database outage at boot is exactly the case that criterion is for.
*Fix:* keep `loaded` false while the database is configured but its bans could not be read; refuse with `moderation.starting` and retry the load on the next pre-login (event-driven, one attempt at a time, no repeating task); log once. Keep memory-only mode open (no database means no bans to enforce). If the fail-closed behaviour is not wanted, record the decision in the brief's open questions and log a warning at start-up instead, so an admin sees it.

### LOW (follow-ups on the PR, no fix round)
- **L-1** `messages.yml:105` and `:110`: `moderation.error.unknown-player` and `moderation.error.no-permission` are unused (the code uses `command.unknown-player` and `command.no-permission`). Remove them, or use them.
- **L-2** `command/CommandKit.java:35`: the Javadoc example uses `"moderation.muted"`, a key that does not exist. The key scan now skips comment lines, so the test passes; fix the example.
- **L-3** `moderation/ModerationService.java:162-172` (`afterKick`): when the target leaves while the KICK row is being written, the row exists (audit of a kick that did not happen) and the sender gets `not-online`. Acceptable; say so in the PR, or check online before the write.
- **L-4** `ModerationService.java:193-211` (`alert`): a non-silent action gives the sender both the confirmation and the broadcast (two lines). Consider skipping the sender in the broadcast.
- **L-5** `ModerationListener.java:33-37`: `denyStarting` overwrites a refusal made earlier by another plugin (whitelist, full server) with the "starting up" text. Check `getLoginResult() == ALLOWED` first.
- **L-6** `text/Messages.java:109` now imports `storage.BanRecord`: a new edge `text -> storage` that `docs/architecture.md` did not list (no cycle, `storage` imports nothing). Documented in the architecture update; the alternative is to pass a moderation-owned value.
- **L-7** `ModerationService.java:254-280` (`background`): busy is removed only in the main-thread hand-back, and only `StorageException` and `RuntimeException` are caught. An `Error` on the storage thread would leave the target busy until restart and never answer the sender. Use `finally`.
- **L-8** `text/Messages.java` `REQUIRED_KEYS`: no `moderation.*` key is listed, so a damaged `messages.yml` copy is not reported at start (the jar default still covers it).
- **L-9** `moderation/BannedPlayerArgument.java:41-44` and `BanList.activeNames`: every keystroke of `/unban` filters and sorts all active bans (PERF-LOW, see section 5).
- **L-10** `BanList`: expired tempban entries are kept for the life of the process, as the brief asks; memory grows with banned players per uptime (negligible, PERF-LOW).
- **L-11** `storage/JdbcPlayerStore.java` (307 lines) now implements three store interfaces. Under the 500-line rule, but split the punishment SQL into its own class before phase 4b adds mutes and warnings. `ModerationService.java` (304 lines) is fine.
- **L-12** `kick`, `ban`, `tempban`, `unban` return `SINGLE_SUCCESS` before the database write finishes (asynchronous). A command block or `/execute store` sees success even if the write later fails. Document it.
- **L-13** Expired tempban rows stay `active = TRUE` forever (brief-sanctioned). `/unban` on them says "not banned" and the audit still shows them active. Note in the PR.

### Checked and passed
- **Order of effects:** database write first (`background`), then `BanList` (`afterBan`/`afterUnban`), then disconnect, confirmation, alert, log. A failed write kicks nobody and changes nothing (tested).
- **Permissions:** `kit.literal(...)` requires the node (hidden in the tree) and `kit.checked` repeats the check at run time. `paper-plugin.yml` and `Perm.ALL` agree (`PluginDescriptorTest` passes). `cookiecore.staff.*` exists in the yml only, as the contract allows.
- **`-s` tree:** the literal wins over the greedy reason for `-s` alone and `-s reason`; `-sx`, `-S` and a trailing `-s` are reasons (tested).
- **Ban index:** active, expired and the longer-ban merge on load are correct (tested). `put` replaces. The expiry compare is the same in memory (`expired`: `expiresAt <= now`) and in SQL (`expires_at > ?`).
- **Login thread:** `ModerationListener.onPreLogin` reads only `BanList` (volatile `loaded`, `ConcurrentHashMap`), `Messages` (volatile `yaml`, concurrent `warned`, MiniMessage is thread-safe), `TimeText` (immutable formatter). No JDBC, no Bukkit main-thread call. `Messages` thread-safety change is correct.
- **Player input:** names and reasons are `Placeholder.unparsed` everywhere; `<prefix>` is the only parsed value. Tested with click tags.
- **Silent alerts:** target excluded; sender excluded on silent; only `cookiecore.staff.notify` holders hear `[silent]` and the notify sound (tested).
- **Busy set:** main-thread only; add on command, remove on hand-back and on rejected executor (tested for the refusal, the pending write and the single row).
- **Reason limit:** 200 characters after strip, error above that. Duration limit (100 years) and zero refused by `DurationParser` (tested).
- **Architecture rules:** `ArchitectureRulesTest` passes (kick calls only in `text/`, storage has no Bukkit, no package cycle, message literals are keys).
- **Unused keys:** see L-1. All other `moderation.*` keys in code exist in `messages.yml`.

### SQL, H2 and MariaDB (read by hand, H2 run in tests)
- DDL: `BIGINT AUTO_INCREMENT`, `BOOLEAN`, `VARCHAR` sizes, `PRIMARY KEY (id)`, `CREATE INDEX IF NOT EXISTS ... (target_uuid, active)`. The index form with `IF NOT EXISTS` is MariaDB syntax (MySQL 8 rejects it); the target is MariaDB, so it is fine, but confirm on the real server (section 6).
- `RETURN_GENERATED_KEYS` read by index 1: correct for H2; for MariaDB Connector/J it should return the id too. **Not run against MariaDB.** Check the "Ban ID" on a real MariaDB ban screen is a real number, not 0.
- Booleans as `TRUE`/`FALSE` literals and `setBoolean`: accepted by both.
- `loadActiveBans` reads `getLong` then `wasNull()` for nullable `expires_at`: correct order.

## 4. Architecture
- New package `moderation` (11 classes plus `ModerationFixture` tests). Dependencies: `moderation -> {command, player, sound, storage, text, log, api, Perm}`; `root -> moderation`. No cycle (checked by the architecture test).
- `Messages` is now the only place that disconnects with a screen (`disconnect`, `denyBanned`, `denyStarting`), so `kick(...)` stays out of other packages.
- `storage` gained `PunishmentStore`, `PunishmentType`, `NewPunishment`, `BanRecord`: no Bukkit, a leaf package as before.
- `ModerationModule` is the wiring; `CookieCore.java` gained a few lines (the 200-line rule still passes).
- Open architecture point: L-6 (`text -> storage`) and L-11 (store class size).

## 5. Performance
Checked: login path, join path, command path, completion, storage, tasks.
- **Login (per connection, concurrent):** one `ConcurrentHashMap.get`, one volatile read, one `System.currentTimeMillis`. No allocation for a player who is not banned. For a banned player one screen is built (about three MiniMessage parses). Estimated well under 0.1 ms per attempt; not measured.
- **Join:** one map lookup. No allocation unless banned.
- **Commands:** a few map lookups and one storage round trip per action; alerts iterate online players once per action (O(players), rare).
- **Completion (`/unban`):** O(active bans) filter plus a sort per keystroke. PERF-LOW (L-9).
- **Tasks:** none added. No repeating task, no per-player task.
- **Storage:** one `loadActiveBans` at start-up on the IO thread; one insert or one update per action; no per-login query.
- **Findings:** no PERF-HIGH, no PERF-MEDIUM. PERF-LOW: L-9 (completion scan), L-10 (expired entries kept).

## 6. In-game test guide (for Luuk, Paper 26.3)
Back up the data folder first (the v2 to v3 upgrade check needs a v2 copy).

1. **Ban an online player:** `/ban <name> griefing the spawn`. The target is removed with the ban screen (By, Reason, Ban ID); they cannot rejoin. You hear `ui_confirm`; other staff with `cookiecore.staff.notify` hear `ui_notify`; everyone else sees the broadcast line. Console shows one INFO line `BAN: <name> by <you>, reason: ...`.
2. **Ban an offline player:** `/ban <name>` for a name that joined once. A name that never joined: "No player named ... has joined this server before."
3. **Tempban and rejoin:** `/tempban <name> 1m spam`. The screen shows "Time left" and "Ends ... UTC". Wait a minute and rejoin: the login works without a restart.
4. **Silent:** `/ban <name> -s spam`: players see nothing; a staff account with `cookiecore.staff.notify` sees `[silent]` and hears the notify sound; you see only your confirmation.
5. **Flag edge cases:** `/ban <name> -sx` (reason is `-sx`), `/ban <name> -S`, a reason over 200 characters (refused), `/ban <yourself>` (refused).
6. **Unban:** `/unban <name>` (also `/pardon <name>`): the player can join. `/unban <name>` again says "is not banned". Tab completion after `/unban ` lists only banned names.
7. **Kick:** `/kick <name> -s spam` for an online player: they see the kick screen; `/kick` of an offline player says "not online".
8. **Restart persistence:** ban someone, restart the server: they are still refused at login. Unban after the restart works.
9. **v2 upgrade:** start with a copy of a v2 database: players are kept, the log says "Loaded N active ban(s)", no errors.
10. **Vanilla:** `/minecraft:ban <name>` still runs the vanilla command (not gated by our node).
11. **Permissions:** a non-op with no `cookiecore.staff.*` node does not see `/ban` in tab completion and gets the vanilla unknown-command message.
12. **Sounds:** `ui_confirm`, `ui_error`, `ui_notify` sound as described (vanilla keys).
13. **Start-up (optional):** restart and join within the first seconds: the "server is starting up" screen (only visible with a slow database; skip if not reproducible).
14. **spark:** `/spark profiler start --timeout 60`, then log in a few banned and unbanned accounts. Good: the `moderation` share is about zero; the login handler is not in the top list.

**MariaDB by hand (not run in tests):** point a MariaDB test server at the plugin, then:
- Check the server version supports `CREATE INDEX IF NOT EXISTS` (MariaDB 10.1.4 or later). Start: `core_schema_version` is 3; `SHOW INDEX FROM core_punishments` lists `core_punishments_target`.
- Upgrade a copy of a v2 MariaDB database: players kept, the table appears, a restart does not fail on the index.
- Ban, tempban and unban one player. `SELECT id, type, target_name, active, expires_at, removed_by_name FROM core_punishments;`: kicks are `active = 0`, the ban is `active = 1` until the unban, then `0` with `removed_by_name` set. The "Ban ID" on the screen equals the `id` column (checks `RETURN_GENERATED_KEYS` on Connector/J).
- Restart: the ban still refuses the login.

## 7. Follow-up questions for the user
- M-1: fail-closed while the database cannot be read at start-up (recommended), or fail-open with a warning? Decide before the coder's fix round.
- L-12 and L-3: accept the asynchronous `SINGLE_SUCCESS` and the kick-audit row as documented behaviour?

## 8. Verification
- `mise run test` on the tester branch: 203 run, 0 failed, 1 skipped (pre-existing).
- `mise run check` on the tester branch: BUILD SUCCESS, 203 run, 0 failures, 1 skipped (pre-existing). The only compiler warning is the older `FlyTracker` deprecation from core-gamemode (L-1 there), not this feature.
- `validate-assets`, `sound-check`, `balance-check`: not present in CookieCore (no assets, no balance). Not applicable.
- Ownership: the coder branch touches only `src/main/**`, `messages.yml`, `paper-plugin.yml` and its own agent memory. No pom change, no `docs/` edit. `mise run ownership-check` cannot run in this checkout (see env notes); checked by hand with `git diff --name-only`.

## 9. Round 2 (2026-10-27): fix for M-1 and LOW fixes, reviewed at HEAD
Reviewed `git diff 2531cd6..HEAD -- src/main` (coder commits `d52a5a5`, `c451740`). Tests added in the tester branch: `ModerationModuleTest` (1: a failed load keeps logins closed, the next login queues the retry, the retry works and the banned player is then refused) and `ModerationServiceTest` (+1: an Error on the storage thread frees the target and tells the sender, the next command goes through). Suite: 205 run, 0 failed, 1 skipped; `mise run check` BUILD SUCCESS. The only warning is the older `FlyTracker` deprecation from core-gamemode.

**M-1 (fail-closed load), resolved as decided by Rick:**
- `ModerationModule.load()` queues at most one load: `AtomicBoolean loading` is set before `io.execute` and cleared in `loadNow`'s `finally`, and also when `io.execute` rejects. No path leaves the flag set while the task still runs. A task dropped by `shutdownNow` leaves it set, which is harmless at shutdown.
- `loadNow` (storage thread): memory-only (`storageAvailable()` false) opens the login (`markLoaded`, as decided). A successful load merges the bans, sets `usable` (volatile) and then `loaded` (volatile): both are written after `bans.load`, so a login or command that reads them sees the bans. A failure (exception) leaves `loaded` false: logins get `moderation.starting` and the listener queues one retry.
- `ModerationListener.onPreLogin` runs `retryLoad` (`load()`: an atomic flag and a queue offer, no JDBC, no Bukkit call) on the login thread, then refuses with the starting screen only when the event is still ALLOWED (L-5 done).
- The SEVERE log is written once (`loadFailureLogged`, storage thread only); later retries are silent.
- /kick: while the load has failed, `usable` is false, so `kickOut` takes the unrecorded path: the kick happens and logs once. Read, not run in game.
- /ban, /tempban, /unban: `storageUp` is `usable && directory.storageAvailable()`, so they refuse with `storage-down`. Read, not run in game.

**Background and busy (L-7 done):** `background` now catches `Throwable`; `finish` runs on the main thread in a `finally` that removes the busy entry, reports the failure, and rethrows an `Error` afterwards. `StorageThread` is a `newSingleThreadExecutor`, which replaces its worker after an uncaught Error, so rethrowing does not stop the storage thread (checked in `StorageThread.create`). Tested.

**LOW fixes verified:** L-1 (the two unused keys removed from messages.yml, no code use left), L-2 (CommandKit Javadoc example now uses `moderation.banned`), L-5 (early return on a refusal from another plugin).

**New LOW from round 2:**
- R2-L1 `ModerationService.kickOut` during a failed load (database up): the kick is not recorded and the one-time warning says "The database is not available", which is misleading. Tell the admin the real reason, or record the kick when storage is up.
- R2-L2 (PERF-LOW): during a database outage every refused login queues one load, and each attempt waits for a connect timeout on the single storage thread, delaying the queued player saves behind it. At most one attempt is in flight, so no flood; a small minimum interval between attempts (from the clock, no timer) would bound it.

**Decision for Rick to confirm (residual, not a code defect against the decision):** the start-up path `storageAvailable() == false` opens logins. The architecture doc calls that memory-only mode, but it is also what a database that cannot be opened at boot produces. So a banned player can join during such a boot outage, and nothing in the log says so for the login side. Rick's decision covered only "database open but load throws". If "configured but unreachable at boot" should also be closed, that is a new MEDIUM for the coder (the same starting screen, the same retry).

**Still open LOW (PR follow-ups, no fix round):** L-3 (kick audit row when the target leaves during the write), L-4 (sender gets the confirmation and the broadcast), L-6 (`text -> storage` edge, documented), L-8 (no `moderation.*` keys in `REQUIRED_KEYS`), L-9 (completion scan per keystroke), L-10 (expired entries in BanList), L-11 (JdbcPlayerStore split before 4b), L-12 (async SINGLE_SUCCESS), L-13 (expired tempban rows stay active), R2-L1, R2-L2.

**Verdict round 2:** READY, conditional on Rick confirming the boot-outage decision above. Re-review scope next time: the coder's changes after `c451740`.

