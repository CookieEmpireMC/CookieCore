# APIs for chat, join/quit, MOTD, private messages, staff chat (phase 5, `core-chat-join`)

Citation Cindy, 2026-10-28. Target: Paper 26.3 (`pom.xml`: `paper-api 26.3.build.205-stable`; the Javadocs I read are a newer 26.3 build, 219). Builds on `core-moderation-bans-apis.md` (command override technique) and `core-paper-apis.md`. Tags: [JD] Paper 26.3 Javadoc, [SRC] Paper/LuckPerms source on GitHub, [DOC] docs page. **UNVERIFIED** = not confirmed, test in game.

## Summary
1. Chat: set only a renderer (`event.renderer(ChatRenderer.viewerUnaware(...))`), never `event.message(...)`; insert `event.message()` with `Placeholder.component`. The original signature is kept.
2. LuckPerms: `net.luckperms:api:5.5` (latest on Central), `provided`; prefix = `user.getCachedData().getMetaData().getPrefix()`; null user or null prefix falls back to `chat.yml` ranks. LP prefixes are legacy `&` strings, not MiniMessage.
3. `msg`: same override as `kick`; `tell` and `w` are vanilla aliases and are taken over too; `whisper`/`pm` are free labels. `minecraft:msg` stays vanilla. Add `pm`, `r` (and `reply`) to the `MuteListener` label set.
4. Join/quit: `joinMessage(Component)` / `quitMessage(Component)`, `null` = no message. First join: `Player#hasPlayedBefore()`.
5. Title: `player.showTitle(Title.title(t, s, fadeInTicks, stayTicks, fadeOutTicks))`.
6. MOTD: `PaperServerListPingEvent#motd(Component)`; the event is **async**.
7. Changes since 1.21: sync `ChatEvent` is deprecated; nothing else relevant found.

---

## 1. `AsyncChatEvent` and `ChatRenderer`
[JD] `io.papermc.paper.event.player.AsyncChatEvent extends AbstractChatEvent` (final). Methods (from `AbstractChatEvent`, [SRC]): `Set<Audience> viewers()` (mutable, lazy), `ChatRenderer renderer()`, `void renderer(ChatRenderer)`, `Component message()` (reflects `message(Component)`), `void message(Component)`, `Component originalMessage()`, `SignedMessage signedMessage()` ("changes made in this event will not update the signed message"), `isCancelled/setCancelled`, `getPlayer()`.
- **Thread** [JD]: async when caused by an incoming packet; synchronous if a plugin forces the player to chat. Code must not assume either. Docs [DOC]: using Bukkit API in the handler is unsafe.
- **Renderer** [JD]: `Component render(Player source, Component sourceDisplayName, Component message, Audience viewer)`. Viewer-independent variant: `ChatRenderer.viewerUnaware(ChatRenderer.ViewerUnaware r)` with `Component render(Player source, Component sourceDisplayName, Component message)`: rendered once per message instead of once per viewer. Our format has no viewer dependence, so use it.
- **What `message()` returns**: the player's text as a plain `Component` (what the client typed, as the user-supplied message). Insert it as a component, never as a string in the MiniMessage template: `Placeholder.component("message", message)` is not parsed, so `<red>hi` stays literal.
- **Signed messages / chat reporting** [SRC `ChatProcessor`]: Paper keeps the original signed `PlayerChatMessage` on every path. A custom renderer only replaces the *unsigned content* (the display text) and switches the chat type from `minecraft:chat` to Paper's `paper:raw`. Changing `message(...)` sets a `MESSAGE_CHANGED` flag and also goes via `withUnsignedContent`. So: do **not** call `event.message(...)`; only set the renderer. Whether the Minecraft client's report screen still accepts a report for such a message: **UNVERIFIED** (cannot be read from server code).
- Do not set the lazy `viewers()` unless needed (reading it can force population). Console is a viewer, so the console log shows the rendered line.
- **Priority**: `MuteListener` cancels at LOWEST. Use `@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)` for the format (a cancelled message never reaches the renderer; HIGH so we beat default-priority formatters; last renderer set wins).
```java
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
public void onChat(AsyncChatEvent e) {
    ChatSettings s = settings;                       // volatile immutable snapshot (async thread)
    e.renderer(ChatRenderer.viewerUnaware((source, displayName, message) ->
        MM.deserialize(s.format(),
            Placeholder.component("rank_prefix", ranks.prefix(source)),
            Placeholder.component("player", displayName),
            Placeholder.component("message", message),
            Placeholder.unparsed("server", s.serverName()))));
}
```
- **Thread rule for the coder**: the handler and renderer run on the chat thread. `ChatSettings`, `Messages` and the rank list must be immutable/volatile. `Player#hasPermission` for `PermissionRankSource`: Bukkit's `PermissibleBase` keeps a plain `HashMap`; `recalculatePermissions` is synchronized but `hasPermission` is not [SRC], so a read during a permission change can be wrong (never loops). With LuckPerms the permissible is LuckPerms' own. Acceptable for a prefix; the alternative is a main-thread cache refreshed at join. Thread-safety of a *plain* Paper permissible: **UNVERIFIED beyond that source read**.
- Sources: https://jd.papermc.io/paper/26.3/io/papermc/paper/event/player/AsyncChatEvent.html , https://jd.papermc.io/paper/26.3/io/papermc/paper/chat/ChatRenderer.html , https://docs.papermc.io/paper/dev/chat-events/ , https://raw.githubusercontent.com/PaperMC/Paper/main/paper-api/src/main/java/io/papermc/paper/event/player/AbstractChatEvent.java , https://raw.githubusercontent.com/PaperMC/Paper/main/paper-server/src/main/java/io/papermc/paper/adventure/ChatProcessor.java , https://raw.githubusercontent.com/PaperMC/Paper/main/paper-api/src/main/java/org/bukkit/permissions/PermissibleBase.java

## 2. LuckPerms API
- **Coordinates** [Central]: `net.luckperms:api:5.5` (versions 5.0 to 5.5; 5.5 = latest/release, last updated 2025-05-25; POM has no dependencies). `provided` scope is what the mvnrepository snippet gives.
```xml
<dependency><groupId>net.luckperms</groupId><artifactId>api</artifactId><version>5.5</version><scope>provided</scope></dependency>
```
  Maven Central is the repository (no extra `<repository>` needed). The runtime plugin is a separate download; a third-party listing shows a LuckPerms 5.5.x Bukkit build covering Paper 26.3 (secondary evidence, **UNVERIFIED** officially).
- **Obtain the API** [SRC `LuckPerms.java`]: the services manager is "the preferred method"; `LuckPermsProvider.get()` is the fallback and throws `IllegalStateException` if not loaded. Bukkit [JD `ServicesManager`]: `@Nullable <T> T load(Class<T>)`, `@Nullable <T> RegisteredServiceProvider<T> getRegistration(Class<T>)`.
- **Prefix without blocking** [SRC]: `UserManager#getUser(UUID)` returns the *loaded* `User` or `null` (never loads, never blocks); `loadUser` returns a `CompletableFuture` and must not be waited on from the main thread. Cached data: `CachedDataManager#getMetaData()` "from the cache, using the most appropriate query options" (the online player's current options); class doc: "heavily cached and fast". `CachedMetaData#getPrefix()` is `@Nullable` (null when no prefix). Meta calls do no database access. Thread-safety is not stated in the API Javadoc; LuckPerms' own chat integrations call it from async chat. A cache miss on the context options (cached ~50 ms) runs context calculators on the calling thread (whether they touch Bukkit API: **UNVERIFIED**).
```java
final class LuckPermsRankSource implements RankSource {      // only class importing net.luckperms
    private final LuckPerms lp;
    LuckPermsRankSource() { RegisteredServiceProvider<LuckPerms> p = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (p == null) throw new IllegalStateException("LuckPerms API not registered"); lp = p.getProvider(); }
    @Override public Optional<Component> prefix(Player p) {
        User u = lp.getUserManager().getUser(p.getUniqueId());   // null = not loaded
        if (u == null) return Optional.empty();
        String raw = u.getCachedData().getMetaData().getPrefix();
        return raw == null || raw.isBlank() ? Optional.empty()
            : Optional.of(LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
    }
}
```
- **If the user is not loaded**: `getUser` returns `null` (the `PlayerAdapter#getUser` variant throws `IllegalStateException("Unable to get a user for ...")`, so prefer `UserManager`). Then fall back to `PermissionRankSource`; never block. LuckPerms loads the user in `AsyncPlayerPreLoginEvent` (LOW) and denies the login if loading fails; it unloads at `PlayerQuitEvent` MONITOR, so a quit listener at HIGH can still read it [SRC `BukkitConnectionListener`]. So "not loaded" only happens for a forced/synthetic chat or a race.
- **Format**: LuckPerms prefixes are legacy strings (`&c[Admin] `), not MiniMessage: deserialize with `LegacyComponentSerializer.legacyAmpersand()` [JD advntr] (static, exists in 4.26.1). Hex (`&#rrggbb`) needs `LegacyComponentSerializer.builder().character('&').hexColors().build()`: builder methods **UNVERIFIED** (page not read), test it. `chat.yml` prefixes stay MiniMessage.
- Sources: https://repo1.maven.org/maven2/net/luckperms/api/maven-metadata.xml , https://repo1.maven.org/maven2/net/luckperms/api/5.5/api-5.5.pom , https://raw.githubusercontent.com/LuckPerms/LuckPerms/master/api/src/main/java/net/luckperms/api/{LuckPerms,LuckPermsProvider}.java , .../api/model/user/UserManager.java , .../api/cacheddata/{CachedDataManager,CachedMetaData}.java , .../api/platform/PlayerAdapter.java , .../common/api/implementation/ApiPlayerAdapter.java , .../bukkit/listeners/BukkitConnectionListener.java , https://jd.papermc.io/paper/26.3/org/bukkit/plugin/ServicesManager.html , https://jd.advntr.dev/text-serializer-legacy/latest/net/kyori/adventure/text/serializer/legacy/LegacyComponentSerializer.html

## 3. Overriding `msg` / `tell` / `w` / `whisper` / `pm` / `r` / `me`
Technique in this repo: `ModerationModule#register` calls `commands.register(node, desc[, aliases])`, takes the returned label `Set<String>` and warns once for labels that are missing; `CommandKit.literal(name, perm)` builds the root. Mechanics [SRC `PaperCommands`]: the main label is always registered with `override = true` (plus `cookiecore:<label>`); an alias is registered with `override = false` *unless* the existing node has no `apiCommandMeta` and is not a `BukkitCommandNode` (= vanilla), in which case it is replaced. Aliases are copies of the node, not redirects.
- **Vanilla labels in 26.3**: `msg` with aliases `tell` and `w` only [Wiki]. `me` is its own command (no aliases) [Wiki]. `whisper`, `pm`, `r`, `reply` are **not** vanilla (Bukkit has none either; another plugin like Essentials may own them: **UNVERIFIED** which are free on the real server).
- So: `register(msgNode, "Private message", List.of("tell", "w", "whisper", "pm"))`: `msg` replaced (main), `tell`/`w` replaced (vanilla aliases), `whisper`/`pm` registered when nobody owns them. Expected set: `msg, cookiecore:msg, tell, cookiecore:tell, w, cookiecore:w, whisper, ..., pm, ...`; warn for any of `msg tell w whisper pm` not in it. `/r`: `register(replyNode, "Reply", List.of("reply"))` (alias `reply` is a proposal; `r` could collide with other plugins).
- **`minecraft:msg` stays vanilla** [SRC `Commands.java.patch`]: Paper adds `minecraft:<name>` for every vanilla command as a full copy (modern) or a flattened redirect to the original node (legacy); our `register` does not touch those names. Server-side behaviour in game: **UNVERIFIED** (test `/minecraft:msg`, `/minecraft:tell`). Vanilla `/msg` accepts multiple targets and sends a *signed/reportable* player-chat message; ours sends system messages, so DMs cannot be reported (a trade-off to put in the PR text).
- **`/me`**: do not override (brief). It is still blocked for muted players by `MuteListener` (preprocess event, LOWEST). It does not go through `AsyncChatEvent` (UNVERIFIED), so `chat.format`/ranks never apply to it.
- **Difference to `kick`**: `kick` was one label; here 4 of 5 labels are aliases, two of them vanilla. Same API call, bigger label list, and the `MuteListener` net needs updating: `WHISPER_LABELS = {msg, tell, w, whisper, me}` lacks `pm`, `r`, `reply`. Since `MessageCommand` checks the mute itself, either add them to the set or rely on the in-command check; `PlayerCommandPreprocessEvent` (not deprecated in 26.3 [JD]) cancels first and sends `moderation.muted-command` for the listed labels, so there is no double message. Also note: a group denied `cookiecore.chat.msg` can still use `/minecraft:msg`; if the server should be able to take `/msg` away, the same preprocess listener must cancel `minecraft:msg|tell|w` for senders without that permission (small addition, proposed below).
- Args: `kit.onlinePlayer("target")` (`ArgumentTypes.player()`, single player; `@a` would resolve to someone else's rules, so reject self/empty in the executor) then `kit.reason("message")` (greedy, last). The text goes in with `Placeholder.unparsed`.
- Sources: https://minecraft.wiki/w/Commands/msg , https://minecraft.wiki/w/Commands/me , https://raw.githubusercontent.com/PaperMC/Paper/main/paper-server/src/main/java/io/papermc/paper/command/brigadier/PaperCommands.java , https://raw.githubusercontent.com/PaperMC/Paper/main/paper-server/patches/sources/net/minecraft/commands/Commands.java.patch , https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerCommandPreprocessEvent.html

## 4. Join/quit messages and the first-join check
[JD] `PlayerJoinEvent`: `@Nullable Component joinMessage()`, `void joinMessage(@Nullable Component)` (null = no message); `PlayerQuitEvent`: `@Nullable Component quitMessage()`, `void quitMessage(@Nullable Component)`, `getReason()` -> `QuitReason` (`DISCONNECTED, KICKED, TIMED_OUT, ERRONEOUS_STATE`). String variants are deprecated. Join is fired after the player is placed in loaded chunks (main thread; teleporting inside it is undefined). Use priority `HIGH` so ours wins over default-priority plugins (the event is not cancellable).
- Silent: `if (player.hasPermission(Perm.CHAT_SILENTJOIN)) event.joinMessage(null)` (same on quit; LuckPerms unloads at MONITOR, so the check works at HIGH).
- **First join**: `Player#hasPlayedBefore()` [JD OfflinePlayer, SRC CraftPlayer]: false on construction, set true when saved player data is read, so it is exact inside `PlayerJoinEvent`. Use it. Our `PlayerDirectory` is a bad source here: `PlayerListener#onJoin` calls `seen(...)` at NORMAL and `cached(uuid)` is already filled by then (order-dependent), and it needs the database. Caveat: deleting the world's `playerdata` makes everyone "first" again.
- A kicked/banned player still fires Quit (reason `KICKED`); consider `chat.quit` only when `getReason() != KICKED`, or accept the line (decide in code).
- Sources: https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerJoinEvent.html , .../PlayerQuitEvent.html , .../PlayerQuitEvent.QuitReason.html , https://raw.githubusercontent.com/PaperMC/Paper/main/paper-api/src/main/java/org/bukkit/OfflinePlayer.java , https://raw.githubusercontent.com/PaperMC/Paper/main/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftPlayer.java

## 5. Titles
[JD advntr 4.26.1] `Audience#showTitle(Title)` (Player is an Audience); `Title.title(Component, Component)`, `Title.title(Component, Component, Title.Times)`, `Title.title(Component, Component, int fadeInTicks, int stayTicks, int fadeOutTicks)` (since 4.24.0); `Title.Times.times(Duration fadeIn, Duration stay, Duration fadeOut)`; `Title.DEFAULT_TIMES` = 10/70/20 ticks. Import `net.kyori.adventure.title.Title` (Player also has unrelated `BaseComponent` overloads). Keep fade times in `chat.yml` as ticks and use the int overload or `Ticks.duration(long)`. Skip the call when title and subtitle are both empty. Showing a title inside `PlayerJoinEvent`: **UNVERIFIED** in game (expected to work; if the client drops it, send it from a 1-tick `runOnMainThread` the project already injects).
- Sources: https://jd.advntr.dev/api/latest/net/kyori/adventure/title/Title.html , https://raw.githubusercontent.com/KyoriPowered/adventure/main/4/api/src/main/java/net/kyori/adventure/title/Title.java , https://jd.advntr.dev/api/latest/net/kyori/adventure/audience/Audience.html

## 6. MOTD
[JD] `com.destroystokyo.paper.event.server.PaperServerListPingEvent extends ServerListPingEvent` (Cancellable; cancel = no response). Inherited, not deprecated: `Component motd()`, `void motd(Component)`; `getMotd/setMotd(String)` deprecated. `int getNumPlayers()` (-1 when players are hidden), `int getMaxPlayers()`. **Async**: the `ServerListPingEvent` constructors call `super(true)` [SRC] (network thread). So: no Bukkit API in the handler; `<online>`/`<max>` from the event's own `getNumPlayers()`/`getMaxPlayers()`, `<server>` from the settings snapshot; settings volatile for `/cookiecore reload`. Two lines: `line1.appendNewline().append(line2)` (or `Component.join(JoinConfiguration.newlines(), ...)`). Default priority is fine (`@EventHandler`); do not cancel.
- Sources: https://jd.papermc.io/paper/26.3/com/destroystokyo/paper/event/server/PaperServerListPingEvent.html , https://jd.papermc.io/paper/26.3/org/bukkit/event/server/ServerListPingEvent.html , https://raw.githubusercontent.com/PaperMC/Paper/main/paper-api/src/main/java/org/bukkit/event/server/ServerListPingEvent.java

## 7. Changes since 1.21 that matter
- Sync `io.papermc.paper.event.player.ChatEvent` is `@Deprecated` ("forces chat to wait for the main thread"); use only `AsyncChatEvent` [JD]. `AsyncPlayerChatEvent` stays legacy. Do not listen to `ChatEvent`.
- Event/renderer/join/quit/title/MOTD signatures above are the 26.3 ones; String-based join/quit/MOTD methods are deprecated (use Component). `PlayerLoginEvent` is deprecated (already forbidden). `PlayerKickEvent.Cause.KICK_COMMAND` deprecated in 26.2.
- I found no Paper changelog page for 26.x chat/ping changes (docs.papermc.io/paper/dev/breaking-changes 404): **UNVERIFIED** that nothing else changed.
- Source: https://jd.papermc.io/paper/26.3/io/papermc/paper/event/player/ChatEvent.html

## 8. LuckPerms prefix on the chat thread
See section 2. Rules: use `getUser(UUID)` (never `loadUser(...).join()`), `getCachedData().getMetaData().getPrefix()`, null user or null/blank prefix -> `PermissionRankSource`. No database call happens. Resolve the prefix inside the renderer lambda or just before it (same thread). If a prefix must be known synchronously without any context calculation, use `getMetaData(lp.getContextManager().getStaticQueryOptions())` (loses world/gamemode contexts); not needed here.

## 9. No `NoClassDefFoundError` when LuckPerms is absent
- `provided` scope means the LP classes are on the compile classpath only; they are not in our jar. When LuckPerms is absent, any class that *loads and links* a reference to `net.luckperms.api.*` fails with `NoClassDefFoundError` (a `LinkageError`).
- **`join-classpath: true`** [DOC]: "whether your plugin should have access to their classpath" (Paper plugins are classloader-isolated unless a dependency is declared). With it, our classloader can load LuckPerms' classes when LuckPerms is installed; with `false` you would get the error even when it is installed. With `required: false` and LuckPerms missing there is nothing to join. `load: BEFORE` makes LuckPerms load before CookieCore (the declaration already in `paper-plugin.yml` is right; no change).
- **Pattern**: (1) every `net.luckperms` import lives in `LuckPermsRankSource` only; (2) it is reached only through the `RankSource` interface and one factory branch; no field, parameter, lambda or static initializer elsewhere has an LP type; (3) the factory instantiates it only after `Bukkit.getPluginManager().isPluginEnabled("LuckPerms")` and catches `LinkageError | RuntimeException`, logging once and using the fallback:
```java
static RankSource create(RankSource fallback, Logger log) {
    if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
        try { return new LuckPermsRankSource(); }           // first touch of LP classes
        catch (LinkageError | RuntimeException e) { log.warning("LuckPerms found but unusable: " + e); }
    }
    return fallback;
}
```
  The JVM verifier does not load the source class when the target type is an interface (HotSpot behaviour, **UNVERIFIED** in this build): the architecture test the brief asks for (no non-`LuckPerms*` class references `net.luckperms`) is the guard. Test without LP: start with LuckPerms absent and assert `LuckPermsRankSource` is never loaded (`ClassLoader#findLoadedClass` on a child-first test loader, or a bytecode scan of constant pools).
- Sources: https://docs.papermc.io/paper/dev/getting-started/paper-plugins/ , https://raw.githubusercontent.com/LuckPerms/LuckPerms/master/api/src/main/java/net/luckperms/api/LuckPermsProvider.java

## Pitfalls
- Never call `event.message(...)` in the chat handler (turns the message into a modified one); never put the message text into the format string.
- Settings read on async threads (chat, ping) must be immutable snapshots behind a `volatile`.
- `/msg` replacement loses reportable DMs; `minecraft:msg` remains an unrestricted vanilla path (permission and mute net needed there).
- `getPrefix()` can be `null` and contains `&` codes; trailing space is the admin's choice (add a space in `chat.format`, not in code).
- Expiry notice (`MuteExpiryNotifier`) is outside this research: it reads `MuteList` on the main thread via the injected `repeatOnMainThread`.

## Proposed contract adjustments
1. Add `pm`, `r` and `reply` to the label set in `MuteListener` (or rely on the in-command check and drop none); add optional `minecraft:msg|tell|w` handling for senders without `cookiecore.chat.msg` (LOW).
2. `/r` alias `reply` (proposal; contract only lists `/r`). Warn at startup for any of `msg tell w whisper pm r` that did not register.
3. `ChatListener` priority `HIGH` + `ignoreCancelled = true`; `MotdListener` handles an **async** event; `ChatSettings` must be a volatile immutable snapshot; the `Messages` read path used from async threads was already made thread-safe in 4a (verify it covers `chat.*`).
4. First join = `Player#hasPlayedBefore()`, not `PlayerDirectory`.
5. LuckPerms prefix strings are legacy `&`: `RankSource` returns a `Component` (or a legacy string to convert), `chat.yml` prefixes are MiniMessage.
6. `pom.xml`: `net.luckperms:api:5.5`, `provided`; no new `<repository>`.
7. Title fade times in `chat.yml` as ticks (int `Title.title(...)` overload, Adventure 4.24+).

## Hand-off
- **plugin-coder:** implement from sections 1 to 9 and the adjustments. **plugin-tester:** test renderer output with a `<red>hi` message, rank order, fallback when `RankSource` returns empty, reply tracking, label registration set, the architecture rule for `net.luckperms`. **Designers:** nothing (no assets; `chat_message` is a vanilla fallback in `sounds.yml`).
- Human review in game: `/minecraft:msg`, chat report screen on a rendered message, title on join, `tell`/`w` taken over, LuckPerms present and absent.
