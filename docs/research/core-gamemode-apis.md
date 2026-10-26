# Paper 26.3 APIs for `/gamemode`, `/gmc`..., `/fly`, `/speed` (phase 3, `core-gamemode`)

Citation Cindy, 2026-10-26. Target: Paper `26.3.build.205-stable` (Javadocs 26.3). Adds to `core-paper-apis.md` section 1 (Brigadier basics, `player()` resolve, `CustomArgumentType`). Source tags: [JD] 26.3 Javadoc, [SRC] Paper `main` source on GitHub (may be newer than build 205), [DOC] docs.papermc.io, [WIKI] minecraft.wiki. **UNVERIFIED** = not confirmed in those; test in game.

## Summary
1. Replace vanilla with a normal `LifecycleEvents.COMMANDS` registration of the literal `gamemode`: Paper replaces the vanilla node (no paper-plugin.yml special case). Keep vanilla grammar `gamemode <mode> [<targets>]` so command blocks, console and `/execute` keep working.
2. `ArgumentTypes.gameMode()` EXISTS (`ArgumentType<GameMode>`) but accepts only the four vanilla names. For `0-3`, `s|c|a|sp` write a `CustomArgumentType.Converted<GameMode,String>` over `word()`.
3. `Player#setGameMode` fires `PlayerGameModeChangeEvent` (cause `PLUGIN`), is a silent no-op when cancelled or when the mode is unchanged, and resets `allowFlight`/`flying` for survival/adventure: re-apply `/fly` yourself.

## 1. Replacing the vanilla command
`Commands` [JD], all return `Set<String>` of registered labels "including aliases and namespaced variants":
```java
register(LiteralCommandNode<CommandSourceStack> node)
register(LiteralCommandNode<CommandSourceStack> node, @Nullable String description)
register(LiteralCommandNode<CommandSourceStack> node, Collection<String> aliases)
register(LiteralCommandNode<CommandSourceStack> node, @Nullable String description, Collection<String> aliases)
register(PluginMeta pluginMeta, LiteralCommandNode<CommandSourceStack> node, @Nullable String description, Collection<String> aliases)
// BasicCommand variants: register(String label, [String description,] [Collection<String> aliases,] BasicCommand)
// registerWithFlags(...) is @Internal: do not use.
```
Behaviour [JD + SRC `PaperCommands#registerWithFlagsInternal`, `registerIntoDispatcher`]:
- Main label overrides existing commands ("The main command/namespaced label will override already existing commands"). Registers `cookiecore:gamemode` first (namespace = `PluginMeta#namespace()`), then plain `gamemode` with override. An existing node that is vanilla (no API metadata, not a `BukkitCommandNode`) is always removed and replaced.
- Aliases are copies (not Brigadier redirects), registered bare and namespaced. [JD]: "Aliases will not override already existing commands (excluding namespaced ones)". Paper commit #11186 makes vanilla conflicts override even for aliases; an alias owned by another plugin (for example Essentials `gm`) is NOT taken. Check the returned set (`contains("gm")`) and log a warning once if an alias is missing.
- `minecraft:gamemode`: the sources I read do not mention it. **UNVERIFIED**: expected to stay vanilla (own permission `minecraft.command.gamemode`), so admins keep an escape hatch; test `/minecraft:gamemode` once.
- Console, command blocks, `/execute`: plugin nodes live in the same Brigadier dispatcher as vanilla [SRC], so they run through the replaced node. Sender is the console / `BlockCommandSender` (`hasPermission` true); `/execute as @a run gamemode ...` sets `getExecutor()`, not `getSender()`: use `getExecutor()` for "no target = myself", `getSender()` for permission checks.
- Datapack functions: [JD] only promises vanilla override "in all loaded datapacks" for commands registered in `PluginBootstrap`; registered from `onEnable` this is **UNVERIFIED**. Harmless if we keep vanilla grammar.
- `paper-plugin.yml` vs `plugin.yml`: no difference for `LifecycleEvents.COMMANDS` in the main class [DOC registration]; bootstrap registration needs `paper-plugin.yml`. Never also declare `commands:` for these labels (creates a competing `BukkitCommandNode`; the project rule already forbids it).
- The vanilla permission check disappears with the vanilla node: our `requires(...)` is the only gate (vanilla ops do not automatically pass; Paper `op` still has all default-op permissions only if our permission defaults to op). Declare permissions in `paper-plugin.yml` and test the string.
- One root literal per shortcut: `gamemode` (alias `gm`), `gmc`, `gms`, `gma`, `gmsp` each need their own node (the mode is fixed); build them from one helper `Commands.literal(label)`.

## 2. Arguments
`ArgumentTypes` [JD] has `ArgumentType<GameMode> gameMode()`. [DOC enums]: "works the same way as the first argument of the Vanilla `/gamemode`": the client validates the vanilla `gamemode` argument type, so only `survival|creative|adventure|spectator` are accepted and suggested; `0`, `c`, `sp` are red/rejected. Read: `ctx.getArgument("mode", GameMode.class)`. Impl [SRC]: `GameModeArgument.gameMode()` wrapped with `GameMode.getByValue(id)`.

Custom parser (for numbers and short names; `GameMode#getValue/getByValue` are `@ApiStatus.Internal`, so map yourself):
```java
final class GameModeArg implements CustomArgumentType.Converted<GameMode, String> {
  private static final Map<String, GameMode> BY_TOKEN = Map.ofEntries(
    entry("survival", SURVIVAL), entry("s", SURVIVAL), entry("0", SURVIVAL),
    entry("creative", CREATIVE), entry("c", CREATIVE), entry("1", CREATIVE),
    entry("adventure", ADVENTURE), entry("a", ADVENTURE), entry("2", ADVENTURE),
    entry("spectator", SPECTATOR), entry("sp", SPECTATOR), entry("3", SPECTATOR));
  public GameMode convert(String raw) throws CommandSyntaxException {
    GameMode m = BY_TOKEN.get(raw.toLowerCase(Locale.ROOT));
    if (m == null) throw BAD.create(raw);           // message key from Messages, not a literal
    return m; }
  public ArgumentType<String> getNativeType() { return StringArgumentType.word(); }
  public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> c, SuggestionsBuilder b) {
    for (String s : List.of("survival", "creative", "adventure", "spectator"))   // suggest names only
      if (s.startsWith(b.getRemainingLowerCase())) b.suggest(s);
    return b.buildFuture(); } }
```
`word()` accepts letters, digits, `+ - _ .` so `0`, `sp` pass the client check. Invalid input only errors after Enter (no live red text).

Targets: `ArgumentTypes.player()` / `players()` -> `PlayerSelectorArgumentResolver` (extends `SelectorArgumentResolver<List<Player>>`, no methods of its own) [JD]. Resolve inside the executor, which must declare `throws CommandSyntaxException`:
```java
List<Player> t = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
```
- `player()` [SRC] = `EntityArgument.player()` + `List.of(result.findSinglePlayer(src).getBukkitEntity())`; `players()` = `Lists.transform(result.findPlayers(src), ServerPlayer::getBukkitEntity)`.
- No match / offline name: `findSinglePlayer` throws a `CommandSyntaxException` from `resolve` (vanilla "No player was found"), which Brigadier shows to the sender in red; a usable error for `/gamemode creative Steve` while Steve is offline. [DOC] does not state this; exact text and the single-player rule for `@a` (vanilla rejects selectors that allow more than one match for `player()` at parse time) are **UNVERIFIED**. For `players()` an empty list may come back without exception: **UNVERIFIED**, so always handle `t.isEmpty()` with a Core message.
- `@a/@r/@s/@p/@e` selectors need `minecraft.command.selector` [DOC]; plain names do not. Suggestions list online names only. Multiple targets: loop, send one summary message, not one per player.
- Do not use `playerProfiles()` (Mojang lookups, see main note).

## 3. Game mode API
- `Player#getGameMode()` / `void setGameMode(GameMode)` (declared on `HumanEntity`) [JD/SRC]. `setGameMode(null)` -> `IllegalArgumentException("GameMode cannot be null")`; returns silently if the player has no connection.
- `setGameMode` fires `PlayerGameModeChangeEvent` with `Cause.PLUGIN` [SRC `CraftPlayer`]. `HumanEntity#setGameMode` returns `void`: a cancel is silent, so compare `getGameMode()` afterwards if you must confirm.
- `PlayerGameModeChangeEvent extends PlayerEvent implements Cancellable` [JD]: `GameMode getNewGameMode()`, `Cause getCause()`, `@Nullable Component cancelMessage()` / `cancelMessage(@Nullable Component)` (shown only for `COMMAND` and `GAMEMODE_SWITCHER`), `isCancelled/setCancelled`. Fired BEFORE the change: `getPlayer().getGameMode()` is still the old mode. Cancel = nothing changes [SRC `ServerPlayerGameMode#changeGameModeForPlayer`].
- `Cause`: `PLUGIN`, `COMMAND` (vanilla `/gamemode`; our replacement therefore reports `PLUGIN`), `DEFAULT_GAMEMODE` (`/defaultgamemode` or join with `force-gamemode`; player "might not be fully online"), `HARDCORE_DEATH`, `GAMEMODE_SWITCHER` (F3+F4, F3+N), `UNKNOWN` (plugin-fired event without cause).
- Same mode requested: `changeGameModeForPlayer` returns `null` -> no event, no change [SRC]. Tell the sender "already in <mode>" yourself by checking first.
- Recursion: calling `setGameMode` on the same player from inside a `PlayerGameModeChangeEvent` handler re-enters the method before the outer change is applied (**UNVERIFIED**, logically the outer mode wins afterwards). Do not do it; schedule it for the next tick. Core needs no listener except optionally the `/fly` re-apply below.

## 4. Flight and speed [JD Player + SRC `CraftPlayer`]
| Method | Behaviour |
|---|---|
| `void setAllowFlight(boolean)` / `boolean getAllowFlight()` | sets `abilities.mayfly`; turning it off also sets `flying=false`; always sends an abilities update |
| `void setFlying(boolean)` / `boolean isFlying()` | `setFlying(true)` throws `IllegalArgumentException("Player is not allowed to fly (check #getAllowFlight())")` unless `getAllowFlight()` |
| `void setFlySpeed(float)` / `float getFlySpeed()` | range -1..1 inclusive; else `IllegalArgumentException("Speed value (%s) need to be between -1 and 1")` |
| `void setWalkSpeed(float)` / `float getWalkSpeed()` | same range; also writes the `MOVEMENT_SPEED` attribute base (SPIGOT-5833) |
| `setFlyingFallDamage(TriState)` / `hasFlyingFallDamage()` | Paper: fall damage while `getAllowFlight()` is true; optional |
- Scale: NBT stores half the API value (`set` stores `value / 2`, `get` returns `* 2`). Vanilla NBT defaults are fly 0.05 and walk 0.1 [WIKI], so API defaults are **0.1 fly and 0.2 walk** (matches the brief). A `/speed reset` sets exactly `0.1f` / `0.2f`. Negative = reverse: reject `< 0` in the command (`FloatArgumentType.floatArg(0f, 1f)` or a 1..10 scale mapped to `n/10f`; the mapping is a design choice for the brief).
- Mode switch: [WIKI player.dat] `mayfly` is true in Creative/Spectator and false in Survival/Adventure, so switching to survival/adventure drops `allowFlight` and `flying` (the `GameType#updatePlayerAbilities` body itself was not readable: **UNVERIFIED**). `/fly` granted to a survival player is lost on `/gm s` -> re-apply with `PlayerGameModeChangeEvent` at `MONITOR`, `ignoreCancelled = true`, via a next-tick task, only for players in a small "fly on" `Set<UUID>`. Switching INTO creative/spectator makes `getAllowFlight()` true, so `/fly` there only reports it.
- Speeds are not part of the mode reset (**UNVERIFIED**, only mayfly/flying/instabuild/invulnerable are rewritten).
- Persistence: the `abilities` compound (`flying`, `flySpeed`, `walkSpeed`, `mayfly`) is in player.dat [WIKI]; in 26.x it is the `Abilities$Packed` record (mappings.dev 1.21.9). So custom speeds very likely survive relog and restart (**UNVERIFIED**, test), and a survival player left with `mayfly` may keep it until the next mode application on join (**UNVERIFIED**). Decision for the brief: Core stores nothing; optionally `setAllowFlight(false)` on `PlayerQuitEvent` for survival/adventure players in the fly set, and offer `/speed reset`.
- 26.x changes: none found in Javadocs for these methods; only the `Abilities$Packed` internal refactor.

## Pitfalls
- `ArgumentTypes.gameMode()` alone breaks `/gm 1` and `/gmc`-style habits; the custom type must keep full vanilla names for compatibility.
- Do not store `GameMode#getValue()` anywhere (internal API).
- `setFlying(true)` without `setAllowFlight(true)` throws; an airborne survival player who loses `allowFlight` falls and takes fall damage: warn or ground them before `/fly off`.
- Our permission check replaces the op-level check of vanilla: console and command blocks pass `hasPermission`, normal ops do not unless the permission defaults to op.
- `getExecutor()` may be null or a non-player: reply with a "players only" Core message instead of casting.
- All text through `Messages`; sounds only through `SoundService` (`ui_confirm` on success, `ui_error` on failure).

## Sources
- https://jd.papermc.io/paper/26.3/io/papermc/paper/command/brigadier/Commands.html , .../argument/ArgumentTypes.html , .../argument/resolvers/selector/PlayerSelectorArgumentResolver.html , .../org/bukkit/event/player/PlayerGameModeChangeEvent.html (+ `.Cause.html`)
- https://docs.papermc.io/paper/dev/command-api/arguments/enums/ , .../arguments/entity-player/ , .../basics/registration/
- https://raw.githubusercontent.com/PaperMC/Paper/main/paper-server/src/main/java/io/papermc/paper/command/brigadier/PaperCommands.java , .../argument/VanillaArgumentProviderImpl.java , .../org/bukkit/craftbukkit/entity/CraftPlayer.java , .../CraftHumanEntity.java , patches `ServerPlayer.java.patch`, `ServerPlayerGameMode.java.patch`, `paper-api/.../entity/Player.java`, `.../GameMode.java`
- https://ayakael.net/mirrors/papermc/commit/67d414a9274232ad68deb3d9b08bf1f7bc699108 (#11186) , https://minecraft.wiki/w/Player.dat_format

## Hand-off
- **plugin-coder:** one helper builds the `gamemode` tree (`<mode> [<targets>]`, alias `gm`) and the four shortcut roots; `GameModeArg` as above; `/fly [targets]` toggle, `/speed <0..1 or 1..10> [fly|walk] [targets]` + `reset`; `/fly` re-apply listener + fly `Set<UUID>` (cleared on quit); log once if `register(...)` did not return `gamemode` / `gm`; permissions in `paper-plugin.yml` + `Perm` + test; all strings via `Messages`, sounds via `SoundService`.
- **Skip:** `playerProfiles()`, `commands:` in the descriptor, any persistence of fly/speed in the DB, listeners that call `setGameMode` re-entrantly.
- **plugin-tester:** `CommandTreeTest` covers `GameModeArg` token map (12 tokens, case-insensitive, junk rejected), range check of speed input, every command node has `requires`; in game: `/minecraft:gamemode`, command block, `/execute as`, offline name error.
- **Designers:** nothing (existing sounds `ui_confirm`, `ui_error`).
