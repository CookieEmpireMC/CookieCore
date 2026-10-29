# core-tab: Paper 26.3 API research

**Target version:** Paper 26.3 (paper-api 26.3.build.207-stable per earlier notes; confirm in pom.xml). Java 25.
Gaps on top of `core-paper-apis.md`, `core-scoreboard-apis.md`, `core-chat-join-apis.md`.

## Summary
- `Player#setPlayerListOrder(int)`: **higher int is listed first**, default 0, must be >= 0, same for all viewers; teams/spectator only break ties. This is the opposite of the brief's "lower = higher".
- Neither `playerListName(Component)` nor `sendPlayerListHeaderAndFooter(Component, Component)` is deduplicated by Paper: each call sends packets (name/order: one per viewer), so the per-viewer "send only on change" cache is required. Setting them in `PlayerJoinEvent` is included in the first info packet.
- TPS: `min(20.0, Bukkit.getTPS()[0])` at one decimal; ping: `Player#getPing()` only changes about every 15 s (keep-alive), no extra rounding needed.
- LuckPerms order: `user.getCachedData().getMetaData().getPrimaryGroup()` then `GroupManager#getGroup(..).getWeight().orElse(0)`, no blocking. See "Proposed contract adjustments" (RankSource needs an `order`, tab must work without the chat module).
- Facts marked UNVERIFIED were not found in docs or source; Paper source was read from `main`, not the 26.3 tag.

## 1. Player#setPlayerListOrder / getPlayerListOrder
- Signatures (Paper main, `paper-api/.../org/bukkit/entity/Player.java`): `void setPlayerListOrder(int order)`, `int getPlayerListOrder()`. Javadoc: "relative order that the player is shown on the in-game player list"; `@param order new player list order, must be positive`.
- Implementation (`CraftPlayer`): `Preconditions.checkArgument(order >= 0, "order cannot be negative")`, so **0 is allowed, negatives throw `IllegalArgumentException`**. It stores `getHandle().listOrder = order`, and if `connection != null` sends `ClientboundPlayerInfoUpdatePacket(Action.UPDATE_LIST_ORDER, handle)` to **every online player who `canSee(this)`**. Before the connection exists (early join) it only stores the field ("Updates are possible before the player has fully joined").
- **Default = 0** (`ServerPlayer`: `public int listOrder = 0;`, Paper patch).
- **Direction: HIGHER value is listed FIRST.** Protocol rule (Minecraft Wiki, Player Info Update, action "Update List Priority", mask 0x40): "Players with higher priorities are sorted before those with lower priorities." Tie-breaks inside equal values: spectators after non-spectators, then scoreboard team name (case-sensitive ascending, teamless players first), then player name (case-insensitive ascending). So teams and spectator mode only matter between players with the SAME order value; a lower-order player never jumps ahead of a higher-order one. Client-side sort: it works for 1.21.2+ clients (the field does not exist for older clients; UNVERIFIED how ViaVersion-style clients behave).
- **Scope: one value per player, the same for every viewer** (it is a field on the subject, broadcast to all viewers who can see them). There is no per-viewer order API.
- **Consequence for the brief:** the brief says "lower = higher in the list" for the rank order. The Paper order is the opposite: **higher int = higher in the list**. So the sort key is simply the LuckPerms weight (higher first), clamped at >= 0. For the `chat.yml: ranks` fallback and "default rank last", compute `order = (ranks.size() - index)` (index 0 = highest = biggest int) and the default rank = 0. Offset the LuckPerms case if both must co-exist (not needed: use only one source at a time). Large ints are fine (VarInt).
- Sent when: immediately on each `setPlayerListOrder` call (one packet per viewer, no dedupe in Paper, so the service must skip unchanged values: compare with `getPlayerListOrder()`, which is a cheap field read). On join the stored value goes out with the initial player-info packet UNVERIFIED (not read in PlayerList), so set it as early as possible (login/join) and it is fine to call again once in `PlayerJoinEvent`.
- Does not need or interact with scoreboard teams (and the per-player scoreboards of phase 2 are irrelevant to it): the team name is only the third tie-break and is read from the viewer's own scoreboard.
- Sources: https://raw.githubusercontent.com/PaperMC/Paper/main/paper-api/src/main/java/org/bukkit/entity/Player.java ; https://raw.githubusercontent.com/PaperMC/Paper/main/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftPlayer.java ; https://raw.githubusercontent.com/PaperMC/Paper/main/paper-server/patches/sources/net/minecraft/server/level/ServerPlayer.java.patch ; https://minecraft.wiki/w/Java_Edition_protocol/Packets (Player Info Update).

## 2. playerListName, sendPlayerListHeaderAndFooter
- `Component Player#playerListName()` and `void Player#playerListName(@Nullable Component name)` (`null` = identical to `getName()`). The old String variants are deprecated.
- `CraftPlayer.playerListName(Component)`: stores `handle.listName = PaperAdventure.asVanilla(name)` and, if `connection != null`, loops over ALL online players and sends `ClientboundPlayerInfoUpdatePacket(Action.UPDATE_DISPLAY_NAME, handle)` to each viewer that `canSee(this)`. **No dedupe**: an identical value is sent again. Cost per call = one conversion plus one small packet per online viewer, so skipping unchanged text in our diff is the right design. Nothing is sent per tick by Paper itself.
- Header/footer: **not a `Player` method.** `Audience#sendPlayerListHeaderAndFooter(Component header, Component footer)` (plus `sendPlayerListHeader`, `sendPlayerListFooter`) is implemented by `CraftPlayer`: it stores header and footer and builds one `ClientboundTabListPacket(header, footer)` sent to that player only (the `connection == null` guard exists in the Bukkit-style helper). **No dedupe** seen in `CraftPlayer`; one packet per call. Read-back: `Component Player#playerListHeader()` / `playerListFooter()` (non-deprecated); the String getters `getPlayerListHeader()/getPlayerListFooter()` are deprecated. Reset: `Audience#clearPlayerListHeaderAndFooter()` (UNVERIFIED in the 26.3 Javadoc; confirm before use, or send `Component.empty()` both).
- Because the read-back getters exist, the service may ALSO compare against `playerListHeader()`/`playerListFooter()`, but the brief's own per-viewer cache of last rendered text is simpler and does not call into the NMS conversion; keep the cache and do not fight other plugins (they would see no loop because we only send on our own change).
- Newlines: header and footer are plain Components; a `\n` in a `Component.text` or `Component.newline()` between lines renders multiple lines in the client (standard Adventure behaviour; join lines with `Component.newline()` rather than a "\n" inside text).
- Sources: Player.java and CraftPlayer.java above; https://jd.advntr.dev/api/latest/net/kyori/adventure/audience/Audience.html

## 3. TPS and tick time
- `Server#getTPS()` / `Bukkit.getTPS()`: `double @NotNull []`, Javadoc "current server TPS (1m, 5m, 15m in Paper-Server)"; `CraftServer.getTPS()` is just `getServer().getTPS()`, no copy, no lock visible (so treat the returned array as read-only and read it on the main thread, which the service does anyway). Index 0 = 1-minute average. The value can be slightly above 20.0 on a catching-up server, so **cap at 20.0** for display.
- `Server#getAverageTickTime()`: `double`, milliseconds (MSPT), `0.0` if no data; implemented as the 5-second `MSPTData.avg()`. Cheap.
- `Server#getTickTimes()`: `long @NotNull []`, **nanoseconds**, a **clone** of the 5 s sample (allocates every call; empty array when no data). Do NOT use per tick; the plain average above is enough.
- Thread-safety / lock behaviour of `MinecraftServer.getTPS()` and `getMSPTData5s()`: UNVERIFIED (not read). Not an issue here because the single repeating task runs through `repeatOnMainThread`.
- Sensible display: `tps = min(20.0, getTPS()[0])` rounded to ONE decimal, formatted with `Locale.ROOT` (`String.format(Locale.ROOT, "%.1f", v)`), so jitter below 0.05 does not change the text. Optionally colour it outside the formatted value in the template, not in code. Optionally offer MSPT later; not in the brief.
- Sources: https://raw.githubusercontent.com/PaperMC/Paper/main/paper-api/src/main/java/org/bukkit/Server.java ; https://raw.githubusercontent.com/PaperMC/Paper/main/paper-server/src/main/java/org/bukkit/craftbukkit/CraftServer.java

## 4. Player#getPing
- `int Player#getPing()` (milliseconds). `CraftPlayer.getPing()` = `getHandle().connection.latency()`; it throws `UnsupportedOperationException` if the connection is null (not before the player has joined; guard against it in the sink, e.g. only call for players in the viewer map).
- Value = weighted average from keep-alive responses: `latency = (latency * 3 + sample) / 4` (`ServerCommonPacketListenerImpl.handlePong`). Vanilla sends a keep-alive about every 15 s (`15000L` check), so **the value only changes about every 15 s per player**, and the first reading after join is 0 until the first answer.
- Thread-safety: the field is a plain `int` (not volatile), so reading from another thread may be stale, not corrupt. Read it on the main thread (our task is); it is a field read, no cost.
- Javadoc says: only a qualitative indicator, not for anti-cheat.
- Display: the integer ms is already stable between keep-alives, so show it as is (`"%d"`); to cut resends further, optional rounding to 5 ms is not needed. Since the footer is only rewritten when the text changes, a ping change causes at most one packet per 15 s per player.
- Sources: https://raw.githubusercontent.com/PaperMC/Paper/main/paper-api/src/main/java/org/bukkit/entity/Player.java (getPing Javadoc) ; CraftPlayer.java above ; https://raw.githubusercontent.com/PaperMC/Paper/main/paper-server/patches/sources/net/minecraft/server/network/ServerCommonPacketListenerImpl.java.patch

## 5. LuckPerms primary group and weight
(Prefix reading from `CachedMetaData` and the class-isolation pattern are in `core-chat-join-apis.md`; only the new part is here.) LuckPerms API `net.luckperms:api:5.5`.
- Primary group name, two equivalent non-blocking ways, both only on a **loaded** user (`luckPerms.getUserManager().getUser(UUID)` returns `@Nullable User` for loaded users, never loads; online players are loaded before `PlayerJoinEvent`, see chat-join note):
  - `String User#getPrimaryGroup()` (Javadoc: "Gets the users current primary group. The result depends on which method is configured for primary group calculation. It may not be the same as any value set through setPrimaryGroup(String)").
  - `@Nullable String CachedMetaData#getPrimaryGroup()` ("Gets the name of the holders primary group"; null for Group holders) via `user.getCachedData().getMetaData().getPrimaryGroup()`. Use this one for consistency with the prefix read (same snapshot).
- How it is calculated: LuckPerms `config.yml` `primary-group-calculation` with options `stored`, `parents-by-weight`, `all-parents-by-weight`; the shipped file sets `parents-by-weight` ("just use the users most highly weighted parent"). Admin-changeable, so the plugin must not assume it; "primary group" is simply what LuckPerms reports.
- Weight: `GroupManager#getGroup(String name)` -> `@Nullable Group` (loaded groups only, no I/O, "null if not"); `OptionalInt Group#getWeight()` ("Gets the weight of this group, if present. A group with no weight set is treated as having a weight of 0 wherever LuckPerms orders groups by weight"). `GroupManager` has no documented thread-safety note on these direct-return methods; call them on the main thread like the rest.
- Sketch (isolated in the one class that touches `net.luckperms`, e.g. `LuckPermsRankSource`):
```java
int weightOf(UUID id) {
    User user = lp.getUserManager().getUser(id);          // null if not loaded
    if (user == null) return 0;
    String primary = user.getCachedData().getMetaData().getPrimaryGroup(); // may be null
    if (primary == null) return 0;
    Group g = lp.getGroupManager().getGroup(primary);     // null if unknown/unloaded
    return g == null ? 0 : g.getWeight().orElse(0);
}
// then: order = Math.max(0, weightOf(id))  (Paper rejects negative order)
```
- Sorting rule that falls out: higher weight = higher int = listed first (see Q1); a group without weight = 0 = same as Paper default, so "default group last" needs no special case; equal weights fall back to the client's team/name order.
- Permission-based fallback (no LuckPerms): the existing `PermissionRankSource` + `chat.yml: ranks`. Compute `order = ranks.size() - index` for a matched rank (index 0 = highest) and `0` for no rank, so the highest configured rank gets the largest int. No new API.
- Mixed case: if LuckPerms is present but a user is not loaded (should not happen after join), return 0 and let the next reload fix it. Do not call `loadUser(...).join()` on the main thread.
- Live updates (not required by the brief, optional later): `EventBus#subscribe(plugin, UserDataRecalculateEvent.class, ...)`, callback not guaranteed on the main thread (see core-paper-apis.md). The brief says rank change shows at next join or reload, so skip it.
- Sources: https://raw.githubusercontent.com/LuckPerms/LuckPerms/master/api/src/main/java/net/luckperms/api/{cacheddata/CachedMetaData,model/group/Group,model/group/GroupManager,model/user/User}.java ; https://raw.githubusercontent.com/LuckPerms/LuckPerms/master/bukkit/src/main/resources/config.yml

## 6. Pitfalls
- **PlayerJoinEvent timing (verified in the Paper `PlayerList` patch):** the event is fired in `placeNewPlayer` BEFORE the `ClientboundPlayerInfoUpdatePacket`s are sent (new player to others, existing players to the new player; the join message also goes out after the event). The initial entry is built from the `ServerPlayer` fields (`listName`, `listOrder`), so **setting `playerListName` and `setPlayerListOrder` inside `PlayerJoinEvent` is part of the first info packet** (no flicker of the plain name). Because `connection` is already set at that point (the `connection == null` guard is for earlier), each setter also sends an UPDATE packet to the players already online (they do not know the new entry yet: the client most likely ignores it, UNVERIFIED but harmless). A normal-priority `PlayerJoinEvent` handler is fine.
- Header/footer: `ClientboundTabListPacket` position relative to the join event not found in the patch window (UNVERIFIED). Sending it from `PlayerJoinEvent` is the common practice; if a client ever misses it, resend with a 1-tick delayed task for that one player (the cache would be bypassed once). Do not add that unless seen in game.
- **Plugin enabled with players online (`/reload`, late enable):** setters work for online players (`connection != null`), so iterate `Bukkit.getOnlinePlayers()` once at enable. `/reload` is unsupported by Paper but the brief asks for it to work.
- **Other plugins:** neither `playerListName` nor header/footer fires an event and Paper does not dedupe, so the last writer wins. Our cache therefore only knows what WE sent. To stay out of fights: send on our own text change, and on `/cookiecore reload` clear the cache once to force a resend; never compare against `playerListName()`/`playerListHeader()` in a loop (that would turn into a tug of war with the other plugin).
- **Name length:** the list-name entry is an Adventure `Component` serialized as a text component (not the 16-char String of 1.7), so there is no 16-character cap; exact packet size limit UNVERIFIED (not read). Keep prefix + name short anyway; the vanilla tab column widens to the longest entry and cuts at the screen width. Header/footer: lines wider than the screen are clipped by the client, so keep lines short (about 40 characters).
- **Team prefix on top (phase 2 risk, UNVERIFIED, from vanilla client behaviour not read here):** the vanilla client applies a scoreboard team's prefix/suffix/colour to the displayed entry even when a custom list name is set. The per-player scoreboard module must therefore not add teams with prefixes, or the rank prefix is doubled. Worth one in-game check in the acceptance test.
- **Spectators:** at equal order they sort after non-spectators; a high order beats the spectator rule (Q1). Not a problem, just do not expect staff in spectator to drop below lower ranks.
- **Visibility:** `hidePlayer`/vanish: updates are only sent to viewers that `canSee` the subject; when a player is shown again the server re-sends the entry with current `listName`/`listOrder`. Phase 6 should not need extra code for this (UNVERIFIED, inferred from the `canSee` loop).
- `Player#getPing()` throws `UnsupportedOperationException` without a connection: only call it for players in the viewer map (added at join, removed at quit).
- Format numbers with `Locale.ROOT` to avoid a decimal comma in the cache key and display.
- Values (player names, prefixes) go into the Component as plain components / `Placeholder.component`, never `Placeholder.parsed` (already the project rule).

## 7. Changes in 26.x since 1.21
- `setPlayerListOrder(int)` / `getPlayerListOrder()` exist in 26.3 and in the 1.21.5-era API (SPIGOT-8044 about the "must be positive" Javadoc was filed against 1.21.5). The sort-by-priority protocol field exists since the 1.21.2 protocol (Velocitab release notes: 1.21.2 introduced a new sorting protocol; older clients still sort by team only). First Paper release with the method: UNVERIFIED.
- The Javadoc still says "must be positive" but the code accepts `>= 0` and rejects negatives (read from Paper main, 2026-10-29; this is current main, which can be newer than 26.3.build.207: re-check the one `Preconditions.checkArgument` if it ever throws on 0).
- String-based header/footer/list-name methods are deprecated in favour of Component ones (`Audience#sendPlayerListHeaderAndFooter`, `Player#playerListHeader()/playerListFooter()/playerListName()`); use only the Component API.
- No 26.1 to 26.3 change specific to tab list API found. Not verified against the 26.x changelogs (no Paper breaking-changes page was reachable, see sources.md dead ends). All Paper source facts above were read from `main`, not from the 26.3 tag.
- The old trick of sorting via scoreboard team names (still correct as a tie-break and the only sort for pre-1.21.2 clients) is no longer needed for 26.3 clients.

## Proposed contract adjustments
1. **Direction is the opposite of the brief.** Paper: higher int = listed first. Replace "lower = higher in the list" with: `order = weight` (LuckPerms) or `ranks.size() - index` (chat.yml), `0` = default rank and Paper default, always `>= 0` (negative throws `IllegalArgumentException`; clamp).
2. **`RankSource` only returns a prefix.** Order needs a second method. Suggest a shared `rank` package (behaviour-preserving move) with `interface RankSource { Optional<Component> prefix(Player); int order(Player); }` (default method `order` = 0 would keep `StaffChat`/tests unchanged), `LuckPermsRankSource` implementing weight via Q5, `PermissionRankSource` implementing index-based order. `orElse` must combine both (order from the first source that has a rank, else the other).
3. **Tab must work with `chat.enabled: false`, but `PermissionRankSource` needs `ChatSettings` and is constructed inside `ChatModule`.** The rank list from `chat.yml` therefore has to be loadable/injectable without the chat module being active (the rank source lives in the shared package and gets a `Supplier<ChatSettings>` or a smaller `RankSettings` from `chat.yml`'s `ranks` section). The brief should state this explicitly.
4. **`PermissionRankSource.refresh(...)` caches on join/reload**; the tab service needs the same refresh points. Either tab reuses that same instance (shared, refreshed once), or it keeps its own main-thread-only computation (no cache needed: tab runs on the main thread, so `player.hasPermission` is safe and a per-join call is cheap).
5. **Add a "force resend on reload" rule** to the acceptance criteria wording: `/cookiecore reload` clears the per-viewer cache so edited `tab.yml` text and a changed rank are applied even if an edited template renders identical text (and so another plugin's overwrite is repaired once).
6. **Rounding/display facts for the footer** (acceptance bullet 3): ping is already stable between keep-alives (about 15 s), so no ping rounding is needed; TPS = `min(20.0, getTPS()[0])` formatted `%.1f`, `Locale.ROOT`. `getTickTimes()` allocates; do not use.
7. Placeholder `<online>`: `Bukkit.getOnlinePlayers().size()` (live view) and `<max>`: `Bukkit.getMaxPlayers()`; both main-thread. `Server#getName()` is the implementation name ("Paper"), NOT the server name: `<server>` must come from the existing server-name setting, not from Bukkit.

## Hand-off
- **plugin-coder:** implement per the brief with the adjustments above. API calls (all main thread, all Component-based):
  - `player.playerListName(Component)`, `player.setPlayerListOrder(int)` (skip if `getPlayerListOrder()` already equals), `player.sendPlayerListHeaderAndFooter(Component header, Component footer)`.
  - `Bukkit.getTPS()[0]`, `Bukkit.getMaxPlayers()`, `Bukkit.getOnlinePlayers().size()`, `player.getPing()` (only for players in the viewer map).
  - `LuckPermsProvider.get().getUserManager().getUser(uuid)`, `user.getCachedData().getMetaData().getPrimaryGroup()`, `getGroupManager().getGroup(name)`, `group.getWeight()` (`OptionalInt`), all inside the single LuckPerms-aware class.
  - Diff cache: `Map<UUID, Rendered(header, footer, listName, order)>` with string/Component equality on the rendered output (Components are value-comparable via `equals`, but compare the rendered plain/serialized text or the pre-component string key to be cheap); remove on quit; clear on reload; task starts with first viewer and stops with none.
  - Test seams: `TabSink` wraps `playerListName`, `setPlayerListOrder`, `getPlayerListOrder`, `sendPlayerListHeaderAndFooter`, `getPing`; a `TpsSource` / `Supplier<Double>` for TPS.
- **model-designer, sound-designer, vfx-designer:** nothing (plain text).
- **plugin-tester:** in-game check list items worth adding: (a) a player with a high LuckPerms weight listed above `Aaron`, default-weight players alphabetical, (b) spectator staff still above default players, (c) no doubled prefix once phase 2 teams exist, (d) `Player#getPing` 0 for the first seconds after join is expected.
- UNVERIFIED items the coder/tester should check in game: first-join packet order of the header/footer, client behaviour for update packets on an unknown entry, behaviour of older (pre-1.21.2) clients under ViaVersion, thread-safety of `getTPS()` (irrelevant on the main thread).

## Sources
- Paper Player API source (setPlayerListOrder, getPlayerListOrder, playerListName, getPing Javadoc, header/footer getters): https://raw.githubusercontent.com/PaperMC/Paper/main/paper-api/src/main/java/org/bukkit/entity/Player.java
- Paper CraftPlayer implementation (order >= 0 check, UPDATE_LIST_ORDER / UPDATE_DISPLAY_NAME loops, getPing, header/footer): https://raw.githubusercontent.com/PaperMC/Paper/main/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftPlayer.java
- ServerPlayer patch (listOrder default 0, listName): https://raw.githubusercontent.com/PaperMC/Paper/main/paper-server/patches/sources/net/minecraft/server/level/ServerPlayer.java.patch
- PlayerList patch (PlayerJoinEvent before player-info packets): https://raw.githubusercontent.com/PaperMC/Paper/main/paper-server/patches/sources/net/minecraft/server/players/PlayerList.java.patch
- ServerCommonPacketListenerImpl patch (latency formula, 15000 ms keep-alive): https://raw.githubusercontent.com/PaperMC/Paper/main/paper-server/patches/sources/net/minecraft/server/network/ServerCommonPacketListenerImpl.java.patch
- Server and CraftServer (getTPS, getAverageTickTime, getTickTimes, getMaxPlayers): https://raw.githubusercontent.com/PaperMC/Paper/main/paper-api/src/main/java/org/bukkit/Server.java , https://raw.githubusercontent.com/PaperMC/Paper/main/paper-server/src/main/java/org/bukkit/craftbukkit/CraftServer.java
- Minecraft Wiki, Java Edition protocol, Player Info Update ("higher priorities are sorted before", tie-breaks): https://minecraft.wiki/w/Java_Edition_protocol/Packets
- Adventure Audience: https://jd.advntr.dev/api/latest/net/kyori/adventure/audience/Audience.html
- LuckPerms API sources: https://raw.githubusercontent.com/LuckPerms/LuckPerms/master/api/src/main/java/net/luckperms/api/cacheddata/CachedMetaData.java , .../model/group/Group.java , .../model/group/GroupManager.java , .../model/user/User.java
- LuckPerms config (primary-group-calculation): https://raw.githubusercontent.com/LuckPerms/LuckPerms/master/bukkit/src/main/resources/config.yml
- SPIGOT-8044 (Javadoc "positive" vs 0): https://hub.spigotmc.org/jira/browse/SPIGOT-8044
- Velocitab sorting docs (1.21.2 protocol change, mentioned in search result): https://william278.net/docs/velocitab/sorting
