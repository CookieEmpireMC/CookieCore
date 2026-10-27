# APIs for `/kick`, `/ban`, `/tempban`, `/unban` (phase 4a, `core-moderation-bans`)

Citation Cindy, 2026-10-27. Target: Paper `26.3.build.205-stable` (pom), Javadocs 26.3 read, Paper server source from `main` (may be newer than build 205). Builds on `core-paper-apis.md` and `core-gamemode-apis.md` (not repeated). Tags: [JD] 26.3 Javadoc, [SRC] Paper source on GitHub, [BRIG] Mojang Brigadier source, [DB] H2/MariaDB docs or source. **UNVERIFIED** = not confirmed; test it.

## Summary
1. **Login deny:** `AsyncPlayerPreLoginEvent#disallow(Result.KICK_BANNED, Component)`, memory read only. It runs on a virtual "User Authenticator" thread, so blocking is legal, but Core's JDBC store is single-threaded by contract: never call it here. `PlayerLoginEvent` is not the only alternative (see 1), but nothing beats the async event.
2. **Kick online player:** `Player#kick(Component, PlayerKickEvent.Cause.BANNED|KICKED)` on the main thread. It fires a cancellable `PlayerKickEvent`; the quit event still follows, so `PlayerListener#onQuit` runs.
3. **Commands:** plain `Commands.register(node, desc, aliases)` overrides vanilla `kick`/`ban` as main labels; `pardon` as an alias of `unban` overrides vanilla too (source-verified). One tree serves both.
4. **`-s`:** a literal sibling of the greedy reason works and always wins over the reason. A reason whose first word is exactly `-s` is eaten as the flag.
5. **DDL:** `BIGINT AUTO_INCREMENT NOT NULL` + `PRIMARY KEY (id)` + separate `CREATE INDEX IF NOT EXISTS`; read the id with `RETURN_GENERATED_KEYS` + `getLong(1)`.
6. **Dates:** fixed `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT)`; remaining time from `Duration` parts.

---

## 1. Login deny (`AsyncPlayerPreLoginEvent`)
[JD] `org.bukkit.event.player.AsyncPlayerPreLoginEvent`: "asynchronous, and not run using main thread", fired "after the server has successfully completed Mojang authentication", also in offline mode; the player's locale is not available (translatables render as `Locale.US`).
```java
void disallow(AsyncPlayerPreLoginEvent.Result result, Component message)   // non-deprecated
void allow()                      Result getLoginResult()      void setLoginResult(Result)
Component kickMessage()           void kickMessage(Component)
UUID getUniqueId()   String getName()   PlayerProfile getPlayerProfile()   InetAddress getAddress()
// Result: ALLOWED, KICK_FULL, KICK_BANNED, KICK_WHITELIST, KICK_OTHER
// Deprecated, skip: String variants of disallow/kickMessage, getResult/setResult, PlayerPreLoginEvent.Result overloads
```
- **Thread** [SRC `ServerLoginPacketListenerImpl.java.patch`]: `callPlayerPreLoginEvents` runs in tasks of `authenticatorPool = Thread.ofVirtual().name("User Authenticator #", 0)...`: one virtual thread per login. After the event, `getLoginResult() != ALLOWED` leads to `disconnect(asVanilla(kickMessage()))`. So: many logins can run **concurrently**; blocking is allowed (virtual thread), but a single shared JDBC connection is not.
- **Cost of a map read:** a `ConcurrentHashMap<UUID, ActiveBan>#get` plus a `System.currentTimeMillis()` compare is nanoseconds; no scheduling needed. Building the screen only happens for banned players.
- **Alternatives** [JD]: `PlayerConnectionValidateLoginEvent` (`io.papermc.paper.event.connection`; "called when a player is attempting to log in for the first time, or is finishing up being configured"; `kickMessage(Component)`, `allow()` (skips ALL remaining validation, do not call), `isAllowed()`, `getKickMessage()`); thread undocumented and not visible in the patches I read: **UNVERIFIED**. `PlayerLoginEvent` is deprecated (since 1.21.6) and forbidden here. The async event is the right primary check. Optional second guard for the race window between pre-login and join (a ban made while the target is still in the configuration phase has no `Player` to kick): re-check `BanList` in `PlayerJoinEvent` (main thread) and kick via the `text/` helper. LOW priority; 5 lines.
- **Recommended:**
```java
@EventHandler(priority = EventPriority.HIGHEST)   // a later handler can call allow(); HIGHEST keeps a ban from being undone
public void onPreLogin(AsyncPlayerPreLoginEvent e) {
    if (!bans.loaded()) { e.disallow(Result.KICK_OTHER, messages.startingScreen()); return; }
    bans.active(e.getUniqueId(), clock.getAsLong())          // empty when absent or expired
        .ifPresent(b -> e.disallow(Result.KICK_BANNED, messages.banScreen(b, clock.getAsLong())));
}
```
- **Pitfall: `Messages` is documented "Main thread only"** (`warned` is a `HashSet`, `yaml` is not volatile). The screen is built on the authenticator thread, so the coder must make the read path thread-safe: `volatile` for `yaml`, `ConcurrentHashMap.newKeySet()` for `warned`, and keep `MM` (a static `MiniMessage`; thread-safety is not stated in its Javadoc but it holds no mutable state, and is used from async chat everywhere). Alternative: `BanScreen` templates snapshot the raw strings in `load()` into an immutable map.
- A denied login produces **no** `PlayerJoinEvent`/`PlayerQuitEvent` (no `Player` ever exists), and `players.last_seen` is not touched. Fine.
- `BanList.loaded()` must be a `volatile boolean` set after the IO-thread load; the denied "starting" screen uses `KICK_OTHER`.
- A vanilla-list ban (`/minecraft:ban`) is checked later by the server itself ([SRC] `PlayerList.canPlayerLogin`), so it keeps working independently.

## 2. Kicking an online player
[JD/SRC `Player`, `CraftPlayer`]
```java
default void kick()                                              // translatable "multiplayer.disconnect.kicked"
default void kick(@Nullable Component message)                  // = kick(message, Cause.PLUGIN)
void kick(@Nullable Component message, PlayerKickEvent.Cause cause)   // not deprecated
@Deprecated void kickPlayer(@Nullable String message)           // skip
```
- **Thread:** `CraftPlayer#kick` calls `AsyncCatcher.catchOp("player kick")`: **main thread only**, off-main it throws. `message == null` becomes `Component.empty()`; a null connection is silently ignored.
- **Cause** [JD `PlayerKickEvent.Cause`, 28 constants]: use `BANNED` for ban/tempban, `KICKED` for `/kick`. `KICK_COMMAND` is deprecated since **26.2** ("use KICKED"). `PLUGIN` is the default of the one-arg form.
- **Event** [SRC `ServerCommonPacketListenerImpl.java.patch`]: a `PlayerKickEvent` (`reason()`, `leaveMessage()`, `getCause()`, `Cancellable`) fires, only for players in the game phase and only while the server is running. **If another plugin cancels it, the player is not kicked** (the disconnect `return`s early). The DB ban is already written, so they are denied at the next login; log a warning if `player.isOnline()` after the kick when it matters. Core should not listen to `PlayerKickEvent`; to hide the vanilla "left the game" line you could set `leaveMessage(null)`, but Core does not need to.
- **Quit cleanup:** after the disconnect packet is sent, `quitReason = KICKED` is set and the connection closes on the **next tick**; `PlayerList.remove` then fires `PlayerQuitEvent` with that reason [SRC `PlayerList.java.patch`]. So `PlayerListener#onQuit` (and any sidebar/tab cleanup) runs after a kick, one tick later. Do not assume the player is gone right after `kick(...)` returns (`isOnline()` stays true until then).
- Per the repo rule the call lives in `text/Messages` (e.g. `Messages.kick(Player, Component, Cause)`); the executor hands over on the main thread (Brigadier executes there).

## 3. Replacing `/kick`, `/ban`, `/pardon`
Same technique as `/gamemode` (see `core-gamemode-apis.md` section 1). Re-verified in [SRC `PaperCommands#registerWithFlagsInternal / registerIntoDispatcher`]:
- Main label: always registered with `override = true`, so `kick`, `ban`, `tempban`, `unban` replace vanilla/Bukkit/other plugins. Also registers `cookiecore:<label>` first.
- Alias: `registerCopy(alias, ...)` tries `override = false` but the code sets `override = true` when the existing node is vanilla (`apiCommandMeta == null && !(BukkitCommandNode)`). So `pardon` (vanilla) is **replaced**; an alias owned by another plugin or `BukkitCommandNode` is skipped. Bare and `cookiecore:` alias are decided independently.
```java
Set<String> labels = registrar.register(unban.node(), "Unban a player", List.of("pardon"));
// labels: unban, cookiecore:unban, pardon, cookiecore:pardon  -> if !contains("pardon") warn once
```
- **One tree for `/unban` and `/pardon`:** yes. Aliases are copies of the node (not redirects), so `requires` and `executes` are identical. Build one node with `kit.literal("unban", Perm.STAFF_UNBAN)`.
- Vanilla leftovers untouched: `ban-ip`, `banlist`, `pardon-ip` (vanilla lists, out of scope). `minecraft:kick` etc. stay vanilla (namespaced vanilla labels not touched by our register; **UNVERIFIED in game**, test once).
- Console/command blocks go through the same dispatcher: use `getSender()` for permission and the actor name (`CommandSender#getName()` is "CONSOLE"; the brief wants "Console", map it); a `Player` sender gives actor uuid, otherwise `NULL`.
- `/unban` completion: `KnownPlayerArgument` takes names from `PlayerDirectory`. Add a constructor parameter (a `Function<String, List<String>>` suggestion source) or a small sibling class `BannedPlayerArgument` that suggests `BanList.activeNames(prefix, limit)`; parsing still goes through the directory. `RequiredArgumentBuilder#suggests(...)` on top of a `CustomArgumentType` is **UNVERIFIED** in Paper's wrapper: use the argument-class route.

## 4. The `-s` flag in Brigadier
[BRIG `CommandNode#getRelevantNodes`]: if a node has literal children, it reads the next space-delimited token and, **if a literal child equals it, returns only that literal**; otherwise it returns the argument children. [BRIG `LiteralCommandNode`]: a literal matches only if followed by a space or end of input (`-sx` is not `-s`). Literals are looked up case-sensitively (`-S` is not the flag).
```java
// per command: tail(kit, silent -> run) returns fresh builders each call (never reuse one built node under two parents)
.then(kit.knownPlayer("target")
    .executes(ctx -> run(ctx, false, null))                              // /ban Steve
    .then(Commands.literal("-s")
        .executes(ctx -> run(ctx, true, null))                           // /ban Steve -s
        .then(kit.reason("reason").executes(ctx -> run(ctx, true, reasonOf(ctx)))))  // /ban Steve -s spam
    .then(kit.reason("reason").executes(ctx -> run(ctx, false, reasonOf(ctx)))))     // /ban Steve spam
// /tempban: same tail under kit.duration("time"), which sits under the player
```
- Parse order: `-s` first, then reason. `-s` is only a flag directly after the player (or duration). `spam -s` keeps `-s` inside the reason (greedy).
- **Ambiguity:** a reason that itself starts with the word `-s` (`/ban Steve -s is not allowed`) is always read as silent + reason `is not allowed`. This cannot be avoided with a literal; document it in usage text. `-sx`, `-S`, `--s` start a normal reason.
- Suggestions: the client offers `-s` next to the (empty) greedy node; no extra work. Custom player/duration arguments are `word` natively, so `-s` is not swallowed by them (a word cannot contain a space).
- The 200-character reason limit is checked in the executor (greedy accepts far more).

## 5. DDL and generated id (H2 2.5.252 default mode, MariaDB 3.5.10)
Evidence: H2 `Mode.java` sets `autoIncrementClause = true` for REGULAR (the default); MariaDB needs `AUTO_INCREMENT`; LuckPerms ships the same column shape in both its `h2.sql` and `mariadb.sql` (`id INT AUTO_INCREMENT NOT NULL`, separate `CREATE INDEX`). H2 keyword list and MariaDB reserved words checked: `type`, `active`, `silent`, `reason`, `id`, `created_at`, ... are **not** reserved in either. `CREATE INDEX IF NOT EXISTS` exists in both. Not run against MariaDB here (no server): the H2 round trip is testable in the suite, MariaDB at the human review.
```sql
CREATE TABLE IF NOT EXISTS core_punishments (
  id BIGINT AUTO_INCREMENT NOT NULL,
  type VARCHAR(16) NOT NULL,
  target_uuid VARCHAR(36) NOT NULL,
  target_name VARCHAR(32) NOT NULL,
  actor_uuid VARCHAR(36) NULL,
  actor_name VARCHAR(32) NOT NULL,
  reason VARCHAR(255) NOT NULL,
  created_at BIGINT NOT NULL,
  expires_at BIGINT NULL,
  silent BOOLEAN NOT NULL,
  active BOOLEAN NOT NULL,
  removed_at BIGINT NULL,
  removed_by_uuid VARCHAR(36) NULL,
  removed_by_name VARCHAR(32) NULL,
  remove_reason VARCHAR(255) NULL,
  PRIMARY KEY (id));
CREATE INDEX IF NOT EXISTS core_punishments_target ON core_punishments (target_uuid, active);
```
(prefix concatenated as in `Migrations`; two strings in one migration list; the second is not a table, so "exactly one table" holds. Index names are schema-wide in H2: keep the prefix in the name.)
- Times are **epoch milliseconds** (`PlayerRecord`: "times are epoch milliseconds", `System::currentTimeMillis` clock in `CookieCore`). `BOOLEAN` is `TINYINT(1)` on MariaDB; `setBoolean/getBoolean` work on both (as `sidebar_prefs.visible` already does).
- Insert and id (works unchanged on both; read the key by **index**, not column name, since H2 and MariaDB name it differently):
```java
try (PreparedStatement ps = c.prepareStatement(
        "INSERT INTO " + p + "punishments (type, target_uuid, target_name, actor_uuid, actor_name, reason, created_at, expires_at, silent, active) "
      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
    ps.setString(1, type); /* ... */
    if (actorUuid == null) ps.setNull(4, Types.VARCHAR); else ps.setString(4, actorUuid.toString());
    if (expiresAt == null) ps.setNull(8, Types.BIGINT); else ps.setLong(8, expiresAt);
    ps.executeUpdate();
    try (ResultSet keys = ps.getGeneratedKeys()) {
        if (!keys.next()) throw new SQLException("no generated key");
        return keys.getLong(1);
    }
}
```
  H2 [DB `JdbcConnection#prepareStatement(String,int)`]: `RETURN_GENERATED_KEYS` sets the generated-keys flag. MariaDB 3.x single-row insert returns one row with the id (secondary evidence only: Jira CONJ-953/957; multi-row inserts return only the first id: not our case). Always `RETURN_GENERATED_KEYS` on the **prepareStatement**; without it `getGeneratedKeys` is not guaranteed.
- Queries (parameterised; `active = TRUE` is valid on both):
  - Load at start: `SELECT id, target_uuid, target_name, actor_name, reason, created_at, expires_at FROM p+punishments WHERE type = 'BAN' AND active = TRUE AND (expires_at IS NULL OR expires_at > ?)`.
  - Unban: `UPDATE ... SET active = FALSE, removed_at = ?, removed_by_uuid = ?, removed_by_name = ?, remove_reason = ? WHERE target_uuid = ? AND type = 'BAN' AND active = TRUE` (all rows of that target, see below).
- **Rules:** insert `KICK` rows with `active = FALSE` (they must never reach the index or the load). An **expired tempban stays `active = TRUE`** in the table (no cleanup task by design): "already banned" and the login check must test `expires_at`, and a new ban after expiry leaves two active rows, so unban must update all active rows of the target. Names/reasons: `VARCHAR` counts characters on both; truncate names to `MAX_NAME_LENGTH` like `JdbcPlayerStore`. A database created with a non-utf8mb4 MariaDB charset rejects emoji in `reason` (strict mode errors); mention it in the review notes, not in code.
- `JdbcPlayerStore` holds one connection, single-threaded by contract: run all `PunishmentStore` calls on the same IO executor (a second class using its own connection on that thread is fine too). `SchemaMigrator` runs a migration's statements in a transaction with the version bump, but MariaDB DDL auto-commits; `IF NOT EXISTS` on both statements makes a re-run safe.

## 6. Dates and remaining time (pure `java.time`)
- **Zone/locale:** at login the client locale is unknown ([JD] above) and a server zone makes tests machine-dependent. Use UTC and say so on screen:
```java
private static final DateTimeFormatter END = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT).withZone(ZoneOffset.UTC);
String until = END.format(Instant.ofEpochMilli(expiresAt));          // 2026-11-03 12:00 UTC
```
  `DateTimeFormatter` is immutable and thread-safe (usable on the authenticator thread). Avoid `ofLocalizedDateTime(...)`: its output depends on the JDK's locale data. A server-zone variant (`ZoneId.systemDefault()` with pattern `... z`) is possible but needs no config key only if you accept machine-dependent tests: not recommended.
- **Remaining:** from `Duration.ofMillis(expiresAt - now)`, floor, two largest units, never "0":
```java
static String remaining(Duration d) {
    long s = Math.max(1, d.toSeconds());
    long days = s / 86_400, h = s % 86_400 / 3_600, m = s % 3_600 / 60;
    if (days > 0) return days + "d " + h + "h";
    if (h > 0) return h + "h " + m + "m";
    if (m > 0) return m + "m " + (s % 60) + "s";
    return s + "s";
}
```
  Lives in `text/` (player-facing); the unit letters match `DurationParser` input (`7d12h`), and the result is inserted via `Placeholder.unparsed("remaining", ...)`. A 100-year ban prints `36500d 0h`: acceptable; permanent bans use the `moderation.screen.ban` key without the time lines. Compute `remaining` at login time (fresh `now`), not at ban time.

## Pitfalls (short)
- Never touch JDBC or `Messages` (unfixed) from `AsyncPlayerPreLoginEvent`; many logins run concurrently.
- `Player#kick` off the main thread throws; a cancelled `PlayerKickEvent` leaves the player online.
- Order of effects in the brief holds: insert on the IO thread, then hop to main for index update + kick + messages. Update `BanList` only after the insert succeeded, but before the kick so a quick relog is denied.
- `-s` as the first reason word is always a flag; alias `pardon` can be silently taken by another plugin (check the returned set).
- The `PlayerQuitEvent` after a kick arrives one tick later; do not rely on the player being offline immediately.

## Contract changes I suggest
1. `punishments` timestamps are **epoch milliseconds** `BIGINT` (`created_at`, `expires_at`, `removed_at`), `KICK` rows `active = FALSE`; `reason`/`remove_reason` `VARCHAR(255)`, names `VARCHAR(32)`; second migration statement `CREATE INDEX IF NOT EXISTS <prefix>punishments_target ON ... (target_uuid, active)`.
2. Add to the text row: "`Messages` read path used by the ban screen must be thread-safe (login runs off the main thread): `volatile yaml`, concurrent `warned`; its class doc 'Main thread only' changes accordingly."
3. Kick causes: `PlayerKickEvent.Cause.BANNED` (ban/tempban) and `KICKED` (kick); never the deprecated `KICK_COMMAND`.
4. Login listener at `EventPriority.HIGHEST`, plus (optional, LOW) a `PlayerJoinEvent` re-check against `BanList` for the join race.
5. `/unban` completion needs a banned-names suggestion source on the player argument (new constructor parameter or sibling class), not `.suggests(...)`.
6. Usage note: a reason cannot start with the bare word `-s`.

## Sources
- Javadocs 26.3: https://jd.papermc.io/paper/26.3/org/bukkit/event/player/AsyncPlayerPreLoginEvent.html , .../AsyncPlayerPreLoginEvent.Result.html , https://jd.papermc.io/paper/26.3/io/papermc/paper/event/connection/PlayerConnectionValidateLoginEvent.html , https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerKickEvent.html , .../PlayerKickEvent.Cause.html
- Paper source (main): https://raw.githubusercontent.com/PaperMC/Paper/main/paper-api/src/main/java/org/bukkit/entity/Player.java , .../paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftPlayer.java , .../paper-server/src/main/java/io/papermc/paper/command/brigadier/PaperCommands.java , .../paper-server/patches/sources/net/minecraft/server/network/{ServerLoginPacketListenerImpl,ServerCommonPacketListenerImpl,ServerGamePacketListenerImpl}.java.patch , .../paper-server/patches/sources/net/minecraft/server/players/PlayerList.java.patch
- Brigadier: https://raw.githubusercontent.com/mojang/brigadier/master/src/main/java/com/mojang/brigadier/tree/{CommandNode,LiteralCommandNode}.java , .../CommandDispatcher.java
- H2: https://raw.githubusercontent.com/h2database/h2database/master/h2/src/main/org/h2/engine/Mode.java , .../h2/src/main/org/h2/jdbc/JdbcConnection.java , https://h2database.com/html/advanced.html (keywords) , https://h2database.com/html/commands.html
- MariaDB: https://mariadb.com/kb/en/reserved-words/ , https://mariadb.com/kb/en/create-index/ ; Connector/J behaviour (secondary): https://jira.mariadb.org/browse/CONJ-953 , https://jira.mariadb.org/browse/CONJ-957
- LuckPerms schemas (same DDL shape on both DBs): https://raw.githubusercontent.com/LuckPerms/LuckPerms/master/common/src/main/resources/me/lucko/luckperms/schema/{h2,mariadb}.sql

## Hand-off
- **plugin-coder:** implement from sections 1 to 6 and the contract changes above. Designers: nothing (vanilla sounds through `SoundService`, no assets). **plugin-tester:** test the `-s` tree via `CommandDispatcher#parse` (flag wins, `-sx`/`-S` become reason), the expiry rules of `BanList`, unban of multiple active rows, H2 round trip with `getGeneratedKeys`, and `remaining(...)`/`END` outputs; check in game that a cancelled-kick plugin is not installed and `minecraft:ban` works; check MariaDB once by hand.
