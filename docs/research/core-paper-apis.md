# Paper 26.3 APIs for CookieCore (command, login, chat, tab/scoreboard, LuckPerms)

Researched by Citation Cindy, 2026-10-20, for feature `core-foundation` (and phases 1, 2, 4a, 4b, 5 of the ROADMAP).

## Target version
Paper `26.3.build.205-stable` per the brief (Java 25, `api-version: '26.3'`). Javadocs read: paper-api `26.3.build.207-stable` (newest 26.3 build in the Paper Maven repo on 2026-10-20; 205 and 206 also exist; `<latest>` of the repo is a 26.3 pre-release alpha, so keep the pinned `-stable`). Adventure is 5.x since 26.2 (see `C:\dev\Cookie-Clicker\docs\paper-26x-reference.md`). Marked **UNVERIFIED** = could not be confirmed in docs or Javadocs; test it or check at compile time.

## Summary
1. Commands: one class per command with `LiteralCommandNode<CommandSourceStack> node()`, registered in `LifecycleEvents.COMMANDS` exactly like CookieClicker's `BakeryCommand`. Offline targets: do NOT use `ArgumentTypes.playerProfiles()` (it calls the Mojang API for unknown names); use a `CustomArgumentType.Converted` over `StringArgumentType.word()` backed by the in-memory `PlayerDirectory` cache. `ArgumentTypes.time()` cannot parse `7d12h` (and its `d` means in-game days), so write a duration argument yourself.
2. Login deny: `AsyncPlayerPreLoginEvent` (async, may block on the DB) with `event.disallow(Result.KICK_BANNED, Component)`. `PlayerConnectionValidateLoginEvent` is the replacement for the deprecated `PlayerLoginEvent`, but its thread is not documented: do not do DB work in it.
3. Chat: `AsyncChatEvent` is async (only sync if a plugin forces a chat); check `isAsynchronous()`, never call the Bukkit API in the handler, use in-memory state only (mute cache).
4. Tab/scoreboard: header/footer is `Audience#sendPlayerListHeaderAndFooter(Component, Component)`; all String-based Player/Team/Objective methods are deprecated in favour of Component ones; `Objective#numberFormat(NumberFormat.blank())` removes the red numbers.
5. LuckPerms: `net.luckperms:api:5.5` `provided`, `softdepend: [LuckPerms]`, `LuckPermsProvider.get()` after checking the plugin is enabled; read prefix from `CachedMetaData`, weight from `Group#getWeight()`.

## Version notes (26.x)
- Nothing in the areas below was removed in 26.1 to 26.3 as far as the Javadocs show (Javadocs list no removals; the String-based methods are only `@Deprecated`). Adventure 5 removed previously deprecated Adventure API (book builder, old click/hover usage), none of which Core needs.
- `PlayerLoginEvent` is deprecated since 1.21.6 (replacement: `PlayerConnectionValidateLoginEvent`; `PlayerServerFullCheckEvent` for full-server bypass).
- `Commands.restricted(...)` exists (docs say since 1.21.6) for confirm-before-run semantics; not needed for Core.

---

## 1. Brigadier registration

### Registration (matches CookieClicker)
```java
// in CookieCore#onEnable (any JavaPlugin; no paper-plugin.yml needed)
getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
        event -> event.registrar().register(command.node(), "CookieCore admin commands"));
```
- Imports: `io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents`, `io.papermc.paper.command.brigadier.{Commands,CommandSourceStack}`, `com.mojang.brigadier.tree.LiteralCommandNode`.
- Registrar overloads (Javadoc 26.3, all return `Set<String>` of registered labels): `register(LiteralCommandNode<CommandSourceStack>, String description, Collection<String> aliases)`, the same with a leading `PluginMeta`; `BasicCommand` variants take `(String label, String description, Collection<String> aliases, BasicCommand)`. The `(node, String description)` form used by CookieClicker compiles there (so it exists; its exact line is not in the extract I read).
- Docs: handlers registered here are re-run whenever commands must be rebuilt (including `/reload`), so no reload handling is needed. Do not put `commands:` in `plugin.yml` (ROADMAP D5).
- Thread: registration happens during startup on the main thread; command execution and the sync suggestion provider run on the main thread (the docs say the sync provider can use "most of the Paper API"). The docs say nothing explicit about the execute thread: treat executes as main thread and never block there.

### Sender, executor, permission
```java
Commands.literal("cookiecore")
    .requires(src -> src.getSender().hasPermission(Perm.ADMIN_VERSION))   // sender, not executor
    .executes(ctx -> { var sender = ctx.getSource().getSender(); ...; return Command.SINGLE_SUCCESS; })
```
`CommandSourceStack` (`@NonExtendable`): `Location getLocation()`, `CommandSender getSender()`, `@Nullable Entity getExecutor()`, `Player getPlayerOrThrow() throws CommandSyntaxException`, `Entity getEntityOrThrow()`, `withLocation`, `withExecutor`. Permission checks use `getSender()`; "act on" logic uses `getExecutor()` (differs under `/execute as`). Per-subcommand permission: put `.requires(...)` on each literal, not only on the root. The docs state a failed `requires` does not hide the command in the client; after a permission change call `Player#updateCommands()` (docs: thread-safe, can be called async, avoid calling often).

### Built-in arguments you need
| Need | Argument | Read with |
|---|---|---|
| greedy reason | `StringArgumentType.greedyString()` (must be last) | `StringArgumentType.getString(ctx, "reason")` |
| single word | `StringArgumentType.word()` (letters, digits, `+ - _ .`) | `getString` |
| online player(s) | `ArgumentTypes.player()` / `players()` -> `ArgumentType<PlayerSelectorArgumentResolver>` | `ctx.getArgument("t", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource())` -> `List<Player>` (`resolve` throws `CommandSyntaxException`) |
| profiles incl. offline | `ArgumentTypes.playerProfiles()` -> `ArgumentType<PlayerProfileListResolver>` | `.resolve(src)` -> `Collection<PlayerProfile>`; docs: unknown names go through Mojang API calls that can fail or lag, resolve async |
| ticks | `ArgumentTypes.time()` / `time(int min)` -> `ArgumentType<Integer>` | `IntegerArgumentType.getInteger(ctx, "time")`; units: plain or `t` = ticks, `s` = seconds x20, `d` = **in-game days** x24000 |
| UUID | `ArgumentTypes.uuid()` | `ctx.getArgument("id", UUID.class)` |

Selectors (`@a`) need `minecraft.command.selector`.

### Recommendation for the Core helper
- **Online-only target** (`/gamemode`, `/fly`): `ArgumentTypes.player()`.
- **Offline-capable target** (`/ban`, `/mute`, `/history`): a custom argument over `word()` with suggestions from the cache. `playerProfiles()` is the wrong tool: it hits Mojang for never-seen names, which the brief does not want, and the ROADMAP wants only "seen before" players.
- **Duration**: custom argument over `word()` (client validates it as a word), parsing `7d12h30m10s` itself. Do not reuse `time()`.

### Custom argument types (exact interface, from docs)
```java
public interface CustomArgumentType<T, N> extends ArgumentType<T> {
    T parse(StringReader reader) throws CommandSyntaxException;
    default <S> T parse(StringReader reader, S source) throws CommandSyntaxException; // preferred if overridden
    ArgumentType<N> getNativeType();                                                   // what the client validates
    default <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> ctx, SuggestionsBuilder b);
    // getExamples(): @NonExtendable, do not override
}
// CustomArgumentType.Converted<T, N> adds:
T convert(N nativeType) throws CommandSyntaxException;
default <S> T convert(N nativeType, S source) throws CommandSyntaxException;
```
Custom types cannot show red text for invalid input client-side (the client only validates the native type); errors appear after Enter.

Minimal Core helpers (sketch for the coder):
```java
public final class DurationArgument implements CustomArgumentType.Converted<Duration, String> {
    private static final SimpleCommandExceptionType BAD =
        new SimpleCommandExceptionType(new LiteralMessage("Use a duration like 7d12h, 30m or 45s"));
    @Override public Duration convert(String raw) throws CommandSyntaxException {
        Duration d = DurationParser.parse(raw).orElseThrow(BAD::create); // pure function, unit-testable
        return d;
    }
    @Override public ArgumentType<String> getNativeType() { return StringArgumentType.word(); }
    @Override public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> c, SuggestionsBuilder b) {
        for (String s : List.of("30m", "1h", "1d", "7d", "30d")) if (s.startsWith(b.getRemainingLowerCase())) b.suggest(s);
        return b.buildFuture();
    }
}

public final class KnownPlayerArgument implements CustomArgumentType.Converted<PlayerRef, String> {
    private final PlayerDirectory directory; // in-memory map, ConcurrentHashMap by lower-cased name
    @Override public PlayerRef convert(String name) throws CommandSyntaxException {
        return directory.cached(name).orElseThrow(() -> UNKNOWN.create(name)); // no DB on main
    }
    @Override public ArgumentType<String> getNativeType() { return StringArgumentType.word(); }
    @Override public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> c, SuggestionsBuilder b) {
        directory.cachedNames().stream()
            .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(b.getRemainingLowerCase()))
            .limit(200).forEach(b::suggest);
        return b.buildFuture();
    }
}
```
- Errors that should reach the player as styled text: `io.papermc.paper.command.brigadier.MessageComponentSerializer.message().serialize(Component)` returns a Brigadier `Message` (verified: `static MessageComponentSerializer message()`, `Component -> Message`), usable in `SimpleCommandExceptionType` / `DynamicCommandExceptionType`. But CLAUDE rules (no text literals outside `text/`) mean the message must come from `Messages`; build the component there.
- The cache must be warm: load existing `players` rows into the map at enable (on the IO thread, before registering the command, or accept a short cold start), and `put` on every join. A cache miss for a staff command can fall back to `PlayerDirectory.lookupAsync(name)` in the execute (CompletableFuture on the IO thread, reply on main) instead of failing in `convert`. **UNVERIFIED:** which thread Brigadier runs `convert/parse` on for a player-typed command (assume main; never block).

### Suggestions (exact)
`RequiredArgumentBuilder#suggests(SuggestionProvider<CommandSourceStack>)`; provider: `CompletableFuture<Suggestions> getSuggestions(CommandContext<S> ctx, SuggestionsBuilder builder) throws CommandSyntaxException`.
```java
.suggests((ctx, b) -> {
    Bukkit.getOnlinePlayers().stream().map(Player::getName)
        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(b.getRemainingLowerCase()))
        .forEach(b::suggest);
    return b.buildFuture();
})
```
- Sync provider: Paper API usable. Async variant `CompletableFuture.supplyAsync(() -> { ...; return b.build(); })`: most Paper API NOT usable inside; only touch the in-memory cache.
- Tooltip: `b.suggest("text", MessageComponentSerializer.message().serialize(component))`. Helpers: `getRemainingLowerCase()`, `getInput()`, `getStart()`, `getRemaining()`; `suggest(int)` exists.
- CookieClicker's `BakeryCommand` suggests online names with `Bukkit.getOnlinePlayers().forEach(p -> b.suggest(p.getName()))` and no prefix filtering (the client filters anyway).

---

## 2. Denying a login with a component

| | `AsyncPlayerPreLoginEvent` | `PlayerConnectionValidateLoginEvent` |
|---|---|---|
| Package | `org.bukkit.event.player` | `io.papermc.paper.event.connection` |
| Thread | **async** (Javadoc: "not run using main thread") | **UNVERIFIED** (Javadoc does not say; it has `isAsynchronous()` only inherited) |
| Fired | after Mojang authentication, also offline mode | on first login attempt **and** again when configuration finishes (two phases) |
| Deny | `disallow(AsyncPlayerPreLoginEvent.Result, Component)` or `kickMessage(Component)` + `setLoginResult(Result)`; `allow()` | `kickMessage(Component)` (there is no `disallow`); `allow()` skips remaining server validation checks (do not call it casually: it also bypasses vanilla full/whitelist checks); `isAllowed()`, `getKickMessage()` |
| Data | `getUniqueId()`, `getName()`, `getPlayerProfile()`, `getAddress()`, `getRawAddress()`, `getHostname()`, `isTransferred()`, `getConnection()` (`PlayerLoginConnection`) | `getConnection()` (`PlayerLoginConnection` or `PlayerConfigurationConnection`; Javadoc: type not stable; disconnecting through it or sending packets is not supported) |
| Result enum | `ALLOWED, KICK_FULL, KICK_BANNED, KICK_WHITELIST, KICK_OTHER` | n/a |

Recommendation (phase 4a): **ban check in `AsyncPlayerPreLoginEvent`**.
```java
@EventHandler(priority = EventPriority.HIGH)
public void onPreLogin(AsyncPlayerPreLoginEvent e) {
    Optional<Ban> ban = bans.activeBan(e.getUniqueId()); // may block: the event is off the main thread
    ban.ifPresent(b -> e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, messages.banScreen(b)));
}
```
- Safe off the main thread here: reading the event, database/JDBC through the Core IO executor (join the future on the event thread, with a timeout, never from main), pure Adventure/MiniMessage component building, in-memory caches.
- NOT safe: any `Player`/`World`/entity API (no `Player` exists yet), `Bukkit.getPlayer(...)`, scheduling work that needs a result back synchronously.
- The kick screen is rendered with `Locale.US` (the client locale is unknown at this stage); fine, Core text is English. Deprecated, avoid: `getResult/setResult`, all `String` `disallow`/`kickMessage` variants, `getKickMessage()`.
- `PlayerLoginEvent` is deprecated (since 1.21.6): do not use it. Use `PlayerConnectionValidateLoginEvent` only if a check is purely in-memory and must also run at the configuration phase.
- Kicking an **online** player: `Player#kick(Component)` / `kick(Component, PlayerKickEvent.Cause)` (non-deprecated; `kickPlayer(String)` is deprecated). Call on the main thread (standard Bukkit rule; **UNVERIFIED** for 26.3 in the Javadocs, which carry no thread note).
- Insert/update the `players` row on `PlayerJoinEvent` (main) by handing a record to the IO thread, not in the pre-login event (name/uuid are known there too, so doing it in `AsyncPlayerPreLoginEvent` after the ban check is also fine and keeps main free; either is OK, choose one).

---

## 3. Chat: `AsyncChatEvent` and `ChatRenderer`

```java
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
public void onChat(AsyncChatEvent e) {
    if (mutes.isMuted(e.getPlayer().getUniqueId())) {   // in-memory only (ConcurrentHashMap)
        e.setCancelled(true);
        return; // notify on the main thread via scheduler, or skip the notice
    }
    e.renderer(ChatRenderer.viewerUnaware((source, displayName, message) ->
        Component.textOfChildren(prefixOf(source), displayName, Component.text(": "), message)));
}
```
- `io.papermc.paper.event.player.AsyncChatEvent` is `final`, extends `AbstractChatEvent` (`PlayerEvent`, `Cancellable`). `AbstractChatEvent` (all `final`): `Set<Audience> viewers()` (mutable; lazily filled), `Component message()`, `void message(Component)` (NPE on null), `Component originalMessage()` (unchanged by `message(...)`), `SignedMessage signedMessage()` (unchanged by edits), `ChatRenderer renderer()`, `void renderer(ChatRenderer)` (NPE on null), `isCancelled()`, `setCancelled(boolean)`. A cancelled event is not executed but still reaches other plugins (use `ignoreCancelled = true` to skip).
- `io.papermc.paper.chat.ChatRenderer` (`@FunctionalInterface`): `Component render(Player source, Component sourceDisplayName, Component message, Audience viewer)` (called once per receiving audience). `ChatRenderer.viewerUnaware(ChatRenderer.ViewerUnaware)` renders once for all viewers; `ViewerUnaware#render(Player source, Component sourceDisplayName, Component message)`. `ChatRenderer.defaultRenderer()` is the vanilla format. Replaces the old `setFormat`.
- **Threading:** async when triggered by a player packet, **sync** if a plugin compels a player to chat. Check `Event.isAsynchronous()`. Calling the Bukkit API from the async handler is unsafe (Paper docs); use the scheduler to hop to main. The renderer also runs off main: it may only read thread-safe state (the rank/prefix cache), never LuckPerms DB loads or Bukkit entities.
- Mute (phase 4b) = cancel in the handler from a `ConcurrentHashMap<UUID, MuteEntry>` loaded on login (in `AsyncPlayerPreLoginEvent`) and updated on mute/unmute; private messages need their own check. To hide a message from some viewers without cancelling, remove audiences from `viewers()`.
- Messages with MiniMessage placeholders: the player's text is a `Component` (`message()`), so it is never parsed as MiniMessage; keep it that way (insert via `Placeholder.component`, not `parsed`).
- `AsyncPlayerChatEvent` (legacy): not checked, **UNVERIFIED**; do not use it.

---

## 4. Tab list and scoreboard (phases 1 and 2)

### Tab header/footer, list name, order
| Want | API (26.3) | Notes |
|---|---|---|
| header + footer | `Audience#sendPlayerListHeaderAndFooter(Component, Component)` (also `ComponentLike` overload); `sendPlayerListHeader`, `sendPlayerListFooter` | `Player` inherits it from `Audience`; `Player` has **no** method of that name of its own. Adventure docs: send both together with the combined method (a single-sided call may show a stale other half). Resets: `Audience#resetPlayerListHeaderAndFooter()` (**UNVERIFIED** signature) |
| current values | `@Nullable Component Player#playerListHeader()`, `playerListFooter()` | use to diff and skip identical sends |
| list name | `Component Player#playerListName()`, `void playerListName(@Nullable Component)` | `null` resets to the real name |
| order | `void Player#setPlayerListOrder(int)`, `int getPlayerListOrder()` | Javadoc text: "relative order that the player is shown"; the old doc said "must be positive" while the code accepts 0 and up (SPIGOT-8044, fixed, final wording not read). **Direction (higher first or last) UNVERIFIED**: test in game, or sort with team names (below) |
| per-player scoreboard | `void Player#setScoreboard(Scoreboard)`, `Scoreboard getScoreboard()` | |

Deprecated, do not use: `Player#setPlayerListHeaderFooter(String/BaseComponent...)` (use `Audience.sendPlayerListHeaderAndFooter`), `setPlayerListHeader(String)`, `setPlayerListFooter(String)`, `setPlayerListName(String)`, `getPlayerListName()`, `getPlayerListHeader/Footer()` (String), `getDisplayName()`/`setDisplayName(String)` (use `displayName()`).

### Scoreboards
```java
ScoreboardManager m = Bukkit.getScoreboardManager();
Scoreboard sb = m.getNewScoreboard();                 // per player; keep a reference (server tracks it weakly)
Objective obj = sb.registerNewObjective("sidebar", Criteria.DUMMY, title /* Component */);
obj.setDisplaySlot(DisplaySlot.SIDEBAR);
obj.numberFormat(NumberFormat.blank());               // io.papermc.paper.scoreboard.numbers.NumberFormat
player.setScoreboard(sb);
```
- `Scoreboard#registerNewObjective(String name, Criteria criteria, @Nullable Component displayName)` and `(..., RenderType)`; all `String`-criteria and `String`-displayName overloads are deprecated. `Criteria.DUMMY` (`org.bukkit.scoreboard.Criteria`) is never changed by the server.
- `ScoreboardManager#getNewScoreboard()` / `getMainScoreboard()`. Main scoreboard is saved by the server and driven by `/scoreboard`: never draw on it; use one new scoreboard per player.
- `Objective#numberFormat()` / `numberFormat(@Nullable NumberFormat)` (null = default). `NumberFormat` factories (package `io.papermc.paper.scoreboard.numbers`): `blank()`, `fixed(ComponentLike)`, `styled(Style)`, `styled(StyleBuilderApplicable...)`, `noStyle()`. `blank()` is the "no red numbers" switch. All Objective methods throw `IllegalStateException` once unregistered.
- `Objective#displayName(@Nullable Component)`, `getScore(String entry)` (entry up to 32767 chars), `getScore(OfflinePlayer)`, `setDisplaySlot(@Nullable DisplaySlot)`, `setRenderType(RenderType)`, `unregister()`. Javadoc on `displayName` still says IllegalArgumentException above 128 characters and for null despite `@Nullable` (**UNVERIFIED** behaviour: keep titles short and non-null).
- `DisplaySlot` has `PLAYER_LIST`, `SIDEBAR`, `BELOW_NAME` and 16 `SIDEBAR_TEAM_<color>`. `PLAYER_LIST` shows a score next to names in the tab list.
- No flicker pattern (phase 2): unique invisible entries per line (for example colour codes as `String` entry), a `Team` per line with `prefix(Component)`/`suffix(Component)` holding the real text, and only change a team's prefix/suffix when the rendered text changed; `Objective#getScore(entry).setScore(n)` for ordering.
- `Team`: `prefix(Component)`, `suffix(Component)`, `color(NamedTextColor)`, `displayName(Component)`, `addEntry(String)`, `addEntries(String...)`, `addEntries(Collection<String>)`, `removeEntry(String)`, `setOption(Team.Option, Team.OptionStatus)` with `Option = NAME_TAG_VISIBILITY, DEATH_MESSAGE_VISIBILITY, COLLISION_RULE`, `setCanSeeFriendlyInvisibles(boolean)`, `getName()`. `Scoreboard#registerNewTeam(String)` throws `IllegalArgumentException` if the name exists; `getTeam`, `getEntryTeam`, `getObjective` return null if absent. Deprecated: all `String` prefix/suffix/displayName/`ChatColor` methods, `getPlayers()` (use `getEntries()`).
- **Tab sorting by rank:** vanilla orders tab entries by team name (alphabetical), which is the classic trick: team names like `010_admin`, `020_mod`, `100_default` so teams sort in rank order. This is vanilla behaviour, not stated in the Paper Javadoc (**UNVERIFIED here**). Teams are per `Scoreboard`: because each player gets their own scoreboard, every player's scoreboard must contain the same rank teams and entries for all online players, or those players sort wrongly/prefix is missing. Alternative or complement: `setPlayerListOrder(int)`, direction UNVERIFIED.
- **Threading:** neither the Scoreboard Javadocs nor the Paper docs give a thread rule. Treat every scoreboard/tab call as **main thread only** and compute strings/components off-thread, apply on main, at most once per second and only when changed. The Adventure header/footer call is a packet send; same rule (**UNVERIFIED**).

---

## 5. LuckPerms (soft dependency)

Latest `net.luckperms:api` on Maven Central: **5.5** (maven-metadata, 2026-10-20). The Bukkit plugin 5.5.x lists support up to 26.3 on Modrinth mirrors (indicative, not official).

```xml
<dependency>
    <groupId>net.luckperms</groupId>
    <artifactId>api</artifactId>
    <version>5.5</version>
    <scope>provided</scope>
</dependency>
```
Maven Central (`repo1.maven.org`), no extra repository needed. `plugin.yml`: `softdepend: [LuckPerms]`. Never reference `net.luckperms` classes from a class loaded when LuckPerms is absent: put all use behind an interface (`RankProvider`) with a `LuckPermsRankProvider` that is only instantiated after `Bukkit.getPluginManager().isPluginEnabled("LuckPerms")`; otherwise a `NoClassDefFoundError` appears. (Standard JVM behaviour.)

Verified signatures (LuckPerms sources, master):
- `static LuckPerms LuckPermsProvider.get()` throws `IllegalStateException` if the API is not loaded yet; never returns null. (Alternative: `Bukkit.getServicesManager().load(LuckPerms.class)`; LuckPerms registers it there, **UNVERIFIED** in the sources I read, use `LuckPermsProvider.get()`.)
- `UserManager`: `@Nullable User getUser(UUID)`, `@Nullable User getUser(String)` (only **loaded** users; returns directly, safe for online players), `CompletableFuture<User> loadUser(UUID)` / `loadUser(UUID, @Nullable String)`, `Set<User> getLoadedUsers()`, `boolean isLoaded(UUID)`. Never `get()`/`join()` a future on the main thread; use `thenAcceptAsync(consumer, mainThreadExecutor)`.
- `user.getCachedData().getMetaData()` -> `CachedMetaData`: `@Nullable String getPrefix()`, `@Nullable String getSuffix()` (null when the stack is empty), `@Nullable String getPrimaryGroup()`, `@Nullable String getMetaValue(String key)`.
- `GroupManager#getGroup(String)` (nullable); `Group#getWeight()` -> `OptionalInt` (no weight = treated as 0 by LuckPerms); `Group#getName()`; `Group#getDisplayName()` nullable.
- Prefix is a **legacy `&`-coded string**: convert with `LegacyComponentSerializer.legacyAmpersand().deserialize(prefix)` (Adventure) before putting it in a Component. Never run it through MiniMessage as parsed text.
- Updates: `luckPerms.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, e -> ...)` (`getUser()`, `getData()`); keep the `EventSubscription` and `close()` it in `onDisable`. The callback is not guaranteed to be on the main thread: hand the new rank to a main-thread queue, then refresh tab/scoreboard once per second.
- Fallback without LuckPerms (D2): `permission.has("cookiecore.rank.<id>")` from `config.yml`; no LuckPerms classes on that path.
- Phase 1 should read rank for online players only through `getUser(uuid)` (cached) and fall back to `loadUser` for the join moment; players are loaded by LuckPerms before `PlayerJoinEvent`.

---

## BakeryCommand (CookieClicker) conventions Core should copy
From `C:\dev\Cookie-Clicker\src\main\java\dev\casperrs\cookieClicker\command\BakeryCommand.java` and `CookieClicker.java:205-207`:
- Plain Brigadier with Paper's classes (`io.papermc.paper.command.brigadier.{Commands, CommandSourceStack}`, Mojang `Command`, `StringArgumentType`, `LiteralCommandNode`); no `BasicCommand`, no Cloud/ACF, no `plugin.yml` commands.
- A final class per command tree with constructor-injected services and `public LiteralCommandNode<CommandSourceStack> node()` returning `Commands.literal("bakery")...build()`.
- Permission as a `public static final String PERMISSION` and `.requires(src -> src.getSender().hasPermission(PERMISSION))` on the root; subcommands `.then(Commands.literal(..))`; arguments via `Commands.argument(name, StringArgumentType.word())`.
- Handlers are `private int x(CommandContext<CommandSourceStack> ctx)` methods referenced as `this::x`, returning `Command.SINGLE_SUCCESS` or `0` after sending an error message.
- Player resolution: `src.getExecutor() instanceof Player`, falling back to `src.getSender()`; non-player gets message key `commands.players-only`.
- All text through `Messages.send(sender, "key", Placeholder.unparsed(..))`, never literals; reload is a `Reloader` functional interface returning a `List<String>` of problems (empty = ok), printed per problem.
- Registration in the main class: `getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(command.node(), "<description>"))`.
- Message key names differ from the Core brief (`commands.reload.done` vs Core's `command.reloaded`): the brief's contract wins for Core.

## Pitfalls
- `ArgumentTypes.time()` `d` = 24000 ticks and returns an `int`: not a real-time ban duration.
- `playerProfiles()` may do network calls; a typo in a ban command would cost a Mojang request and may lag.
- Doing JDBC in any main-thread callback (suggestions, convert, execute, `PlayerJoinEvent`, `PlayerConnectionValidateLoginEvent`) is forbidden by the project rules; in `AsyncPlayerPreLoginEvent` it is allowed.
- Teams and objectives are per scoreboard; a player's own `Scoreboard` hides main-scoreboard teams, which breaks tab sorting if only the main scoreboard has the rank teams.
- Keep a strong reference to each player's `Scoreboard` (the server holds it weakly) and drop it on quit.
- LuckPerms classes referenced from always-loaded classes break boot when LuckPerms is absent.

## Sources
- Paper docs, command API: https://docs.papermc.io/paper/dev/command-api/basics/introduction/ , /basics/registration/ , /basics/executors/ , /basics/requirements/ , /basics/argument-suggestions/ , /basics/custom-arguments/ , /arguments/minecraft/ , /arguments/entity-player/ , /arguments/paper/
- Paper docs, chat: https://docs.papermc.io/paper/dev/chat-events/ ; lifecycle: https://docs.papermc.io/paper/dev/lifecycle/
- Javadocs 26.3: https://jd.papermc.io/paper/26.3/io/papermc/paper/command/brigadier/Commands.html , .../CommandSourceStack.html , .../argument/ArgumentTypes.html , .../argument/resolvers/ArgumentResolver.html , .../argument/resolvers/PlayerProfileListResolver.html , .../MessageComponentSerializer.html
- Login: https://jd.papermc.io/paper/26.3/org/bukkit/event/player/AsyncPlayerPreLoginEvent.html , .../AsyncPlayerPreLoginEvent.Result.html , https://jd.papermc.io/paper/26.3/io/papermc/paper/event/connection/PlayerConnectionValidateLoginEvent.html , https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerLoginEvent.html , https://jd.papermc.io/paper/26.3/io/papermc/paper/connection/PlayerLoginConnection.html
- Chat: https://jd.papermc.io/paper/26.3/io/papermc/paper/event/player/AsyncChatEvent.html , .../AbstractChatEvent.html , https://jd.papermc.io/paper/26.3/io/papermc/paper/chat/ChatRenderer.html , .../ChatRenderer.ViewerUnaware.html
- Tab/scoreboard: https://jd.papermc.io/paper/26.3/org/bukkit/entity/Player.html , https://jd.papermc.io/paper/26.3/org/bukkit/scoreboard/{Scoreboard,Objective,Team,Team.Option,Criteria,DisplaySlot,ScoreboardManager}.html , https://jd.papermc.io/paper/26.3/io/papermc/paper/scoreboard/numbers/NumberFormat.html , Adventure Audience: https://jd.advntr.dev/api/latest/net/kyori/adventure/audience/Audience.html , SPIGOT-8044: https://hub.spigotmc.org/jira/browse/SPIGOT-8044
- Paper API versions: https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml
- LuckPerms: https://repo1.maven.org/maven2/net/luckperms/api/maven-metadata.xml , https://raw.githubusercontent.com/LuckPerms/LuckPerms/master/api/src/main/java/net/luckperms/api/{LuckPermsProvider,model/user/UserManager,model/group/Group,cacheddata/CachedMetaData,event/user/UserDataRecalculateEvent}.java , https://luckperms.net/wiki/Developer-API-Usage
- Sibling repo: `C:\dev\Cookie-Clicker\docs\paper-26x-reference.md`, `...\command\BakeryCommand.java`, `...\CookieClicker.java`

## Hand-off
- **plugin-coder:** implement from sections 1 to 5. Things to decide in code: `Perm` constants and `.requires` per subcommand; `KnownPlayerArgument` and `DurationArgument` in `command/` with a pure `DurationParser` (unit-testable); `PlayerDirectory` with an in-memory `ConcurrentHashMap` (lower-cased name -> uuid) warmed from the DB; pin Paper at `26.3.build.205-stable` (207 exists; bump deliberately) and add `net.luckperms:api:5.5` `provided` only when phase 1 needs it (not in `core-foundation`). Use `AsyncPlayerPreLoginEvent` for the `players` upsert if you want to keep main free; test on a real server whether `PlayerConnectionValidateLoginEvent` ever runs async before using it.
- **model-designer / sound-designer / vfx-designer / balance-designer:** nothing. (No assets; sounds are vanilla keys through `sounds.yml`.)
- **plugin-tester:** `ArchitectureRulesTest`: `AsyncPlayerPreLoginEvent` handlers are the only place where blocking storage calls on a non-IO thread are acceptable; flag any `.join()`/`.get()` elsewhere; flag `net.luckperms` imports outside a `luckperms/` package; flag deprecated `setPlayerListHeaderFooter`, `PlayerLoginEvent`, String-based `registerNewObjective`.
- Proposed contract additions: none for IDs. Proposed brief note: `CookieCoreApi` should not expose Brigadier or LuckPerms types.
