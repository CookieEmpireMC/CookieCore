# Review: core-chat-join (phase 5)

- **Reviewer:** Gandalf (plugin-tester), branch review
- **Reviewed:** `core-chat-join/plugin-coder`, tip `03dce54` (coder commits `0f7193a` CoreWiring move, `03dce54` chat layer; DONE reported)
- **Tester branch:** `core-chat-join/plugin-tester` in the coder's worktree. Commits: `643433c` (the two invalidated tests), `31e32ef` (chat tests), then the docs commit.
- **Target:** Paper 26.3 (`26.3.build.205-stable`), `api-version: '26.3'`, Java 25, LuckPerms API `5.5` (`provided`).

VERDICT: READY (round 2, section 10). Round 1 was NOT READY with two MEDIUM findings (M-1, M-2); both are fixed in `a49eeb4` and covered by tests in `9cbf4b6`. No HIGH, no PERF-HIGH, no open MEDIUM. The remaining LOW items are PR follow-ups.

## 1. Target version

- `pom.xml`: `paper-api 26.3.build.205-stable` (pinned `-stable`), `net.luckperms:api:5.5` `provided`, no new repository. Checked by `PluginDescriptorTest`.
- `paper-plugin.yml`: `api-version: '26.3'`, `loader`, `dependencies.server.LuckPerms` (`load: BEFORE`, `required: false`, `join-classpath: true`).
- APIs used compile against 26.3 with no new deprecation or removal warning: `AsyncChatEvent`, `ChatRenderer.viewerUnaware`, `PaperServerListPingEvent#motd`, `Player#showTitle` with `Title`, `Commands.register` (labels). The only warning is pre-existing (`FlyTracker.java:33`, `Player#isOnGround`, gamemode, from core-gamemode L-1).

## 2. Test results

- **Build was red at the start:** `ArchitectureRulesTest.forbiddenApisAreAbsent` (flagged every `net.luckperms` reference) and `PluginDescriptorTest.wildcardAdminNodeCoversAll...` (expected `op` for the two new nodes). Fixed in `643433c`: the LuckPerms rule is now `luckPermsIsReferencedOnlyByTheLuckPermsRankSource` (only `chat/LuckPermsRankSource.java`), and the descriptor test expects `false` for `cookiecore.chat.silentjoin` and `true` for `cookiecore.chat.msg`.
- **New tests (26 in total: 25 in five new classes, plus one added to `PluginSettingsTest`, `31e32ef`):**
  - `ChatSettingsTest` (6): the shipped `chat.yml` loads, and every text parses with a strict MiniMessage instance; a chat line shows its rank (or none) and keeps `<red>hi &c<bold>x` literal; rank order (first match, then default, then nothing); LuckPerms prefix wins over chat.yml (`RankSource.orElse`); a bad `format` and a bad rank are reported and not used; the shipped MOTD fills `<server>`, `<online>`, `<max>`.
  - `ChatConfigTest` (3): first load with a broken file uses the jar copy and says so; a reload with a missing part keeps the last good object and names the part; a reload with broken YAML keeps it too.
  - `ChatCommandsTest` (6): `/msg` both ways with literal text and `/r` back; a muted player is refused for `/msg` and `/r`; self, offline target, no partner and a partner who left each get their line; `/sc` toggle and staff-only lines with literal text; a muted staff member can still `/sc`; reply target is the last partner and quit drops it.
  - `JoinListenerTest` (5): join and quit lines; first join (`hasPlayedBefore` false) with welcome title and line; the silent-join node hides join and quit; blank welcome texts switch the welcome off; quit drops the reply target and the staff toggle.
  - `MuteExpiryNotifierTest` (5): a tempmute that runs out while online is announced once; permanent mute never; unmute never; a mute that ended while offline never; the task starts once and stops when the server is empty.
  - `PluginSettingsTest` (+1): `chat.enabled` is read; a bad value is reported and chat stays on.
- **Full run:** `mise run check` BUILD SUCCESS: 251 tests run, 0 failures, 1 skipped (`ScoreboardLayoutTest`, pre-existing). Before the chat tests the count was 225.
- `validate-assets`, `sound-check`, `balance-check`: this repo has no such tasks (no assets, no balance data). Not applicable.
- **Not covered by tests (in-game steps in section 6):** the renderer inside `ChatListener` (needs an `AsyncChatEvent`), the LuckPerms path, `MotdListener.onPing`, `ChatModule.staffReaders` (needs `Bukkit.getOnlinePlayers`), `ChatModule.register` label takeover, and the `chat.enabled: false` wiring (`CookieCore.onEnable` needs a server). The LuckPerms isolation is covered by the architecture test and by reading the factory.

## 3. Findings

### MEDIUM (back to plugin-coder, round 2)

**M-1: a muted player can talk in public chat when their staff toggle is on but the permission is gone.**
- `moderation/MuteListener.java:83` lets a muted player through when `chatExemption.test(uuid)` is true. That predicate is `ChatModule.staffChannelActive` (`chat/ChatModule.java:79`, wired in `CoreWiring.java:68`). It reads only the toggle set, not the permission.
- `chat/ChatListener.java:46` takes the staff branch only when `cookiecore.staff.chat` is still held. Otherwise the line falls through to the public renderer.
- Scenario: a staff member toggles `/sc` on, is muted, and loses `cookiecore.staff.chat` (demotion). The toggle lives until quit, so their public chat goes through the mute.
- **Fix:** make the exemption check the player, not the UUID: `Predicate<Player>` in `MuteListener` (pass `event.getPlayer()`), and in `ChatModule` supply `p -> staff.active(p.getUniqueId()) && p.hasPermission(Perm.STAFF_CHAT)`. `ModerationModule.exemptFromChatMute` takes the same type. Regression test: a muted player with a stale toggle and no permission is cancelled (test the predicate through a small seam or the `MuteListener` with a stub event).
- **Policy:** none needed. "A muted staff member can still use /sc" stays as the brief says.

**M-2: `CookieCore.settings` is read from async threads without `volatile`.**
- `CookieCore.java:52` is `private PluginSettings settings;`, reassigned in `reload()` on the main thread. `CookieCore.java:94` passes `() -> settings.serverName()` into `ChatModule`, which `ChatListener.onChat` (AsyncChatEvent, `chat/ChatListener.java:260` via `serverName.get()`) and `MotdListener.onPing` (`chat/MotdListener.java:109`) call on the async threads.
- The brief requires settings to be volatile and immutable for these handlers. Reads are not guaranteed to see a reload.
- **Fix:** `private volatile PluginSettings settings;` (one word). Alternatively pass the server name as a `volatile String` snapshot.

### LOW (PR follow-ups; no fix round)

- **L-1** `chat/PermissionRankSource.java:45` (and `:34`): a cache miss in `prefix` calls `resolve`, which reads Bukkit permissions on the chat thread. `refreshAll` (`:34`) clears the cache before refilling on the main thread, so the window exists. A join always fills the cache, so a miss is rare. Fix: return `Optional.empty()` on a miss, or refill without the clear.
- **L-2** `chat/ChatListener.java:46`: `sender.hasPermission` is read on the chat thread. It is a read, and it is fixed by M-1 if the check moves into the shared predicate.
- **L-3** `chat/ChatModule.java:110`: `staffReaders()` also sends staff lines to the console. The brief says players with `cookiecore.staff.chat`. It is useful in the log, so keep it and say so in the PR, or drop it. Also extract `staffReaders(Collection<? extends Player>)` as package-private so the permission filter can be tested. **Decision for the orchestrator:** keep the console as a staff reader?
- **L-4** `chat/MotdListener.java:17`: `HIDDEN_COUNT = "?"` is player-facing text in code. The brief puts all player text in `messages.yml`. Move it to a key, or accept it as a symbol.
- **L-5** `chat/ChatSettings.java:111`: a null item in the `motd` list becomes the text "null" (`String.valueOf`). Edge case; reject empty items with a problem message.
- **L-6** `CookieCore.java:90`: fully qualified `dev.casperrs.cookieCore.storage.JdbcPunishmentStore` inline (already in the base branch). Import it; the line count has room now.
- **L-7** `chat/ChatListener.java:249-253` drops the staff line silently when the server is stopping. Acceptable, noted for the PR.

### PERF (per-call cost estimates, measured on the dev machine, warm, 2000 calls after 3000 warm-up calls)

- Public chat render (`Template.render`, 4 placeholders, `ChatListener`): about 10 us per message. At 50 messages per second that is 0.5 ms of chat-thread CPU per second. Plus `LuckPermsRankSource.prefix`: one `ConcurrentHashMap` lookup and `ChatText.legacy(raw)` (a legacy parse per message).
  - **PERF-LOW-1** `chat/LuckPermsRankSource.java:37`: cache the parsed `Component` for the last raw prefix string. Not urgent.
- MOTD render (`MotdListener.render`): about 9 us per ping. **PERF-LOW-2**: none needed.
- Expiry tick (`MuteExpiryNotifier.tick`, once per second while someone is online): about 20 us with 200 online players and nothing watched.
  - **PERF-LOW-3** `chat/MuteExpiryNotifier.java:89-90`: builds a `Set` of all online UUIDs on every tick even when `watched` is empty. Skip that block when `watched.isEmpty()`.
- Join, quit, `/msg`, `/r`, `/sc` and the staff toggle are O(1) or O(online) for a staff line (`staffReaders` loops online players on the main thread). No JDBC, no entity, no packet on the chat path (`chat/` has no `storage` calls besides the `MuteRecord` type).
- Scheduled work: one repeating task (20 ticks) that starts on the first join and stops itself when the server is empty. Cancelled in `stop()` on disable. This matches the "start on demand" rule.
- Memory: per-player state (`PermissionRankSource` cache, `ReplyTracker` entries, `StaffChat` toggle, `MuteExpiryNotifier.watched`) is removed on quit. Checked by `JoinListenerTest.quitForgetsTheReplyTargetAndTheStaffToggle` and by reading.
- **PERF-HIGH:** none.

## 4. Architecture

- **CookieCore.java** is 192 lines (cap 200). `Bukkit.getScheduler()` is called only in `CookieCore` (grep). Modules get `runOnMainThread` and `repeatOnMainThread` as functions.
- **CoreWiring regression check** (diffed against the base): the same listeners in the same order (moderation, mute, player, seen, scoreboard, `apply()`, fly), then the chat listeners, the staff mute exemption and `chat.apply()`. The same commands (admin, sidebar, gamemode, fly, speed, moderation) in `LifecycleEvents.COMMANDS`, then chat. Disable: the chat stops after the sidebar and before the API unregister. Reload: `chat.loadConfig` then `chat.apply()`, plus a restart note for `chat.enabled`.
- **4a and 4b still intact:** `ModerationModule.register` uses the moved `CommandKit.collectMissing` (same labels and warning); `/kick`, `/ban`, mutes and whisper blocking unchanged apart from the added labels `pm`, `r`, `reply` (`MuteListener.java`, with the in-command mute check kept).
- **LuckPerms isolation:** only `chat/LuckPermsRankSource.java` imports `net.luckperms`. `RankSources.create` checks `isPluginEnabled("LuckPerms")` first and catches `LinkageError | RuntimeException`. The descriptor has `load: BEFORE`, `required: false`, `join-classpath: true`. The architecture test covers the import rule.
- **Packages:** the `chat` package has 14 classes (limit about 25). Dependencies: `chat -> {command, moderation, sound, storage, text, config, Perm}`; nothing in `moderation` imports `chat`; the exemption is a `Predicate` seam. No cycle.
- **Shared helpers:** `Messages.deliver/title/isBlank/has` and `ChatText` are used by chat only, and each is small. `Messages.deliver` is a pass-through; acceptable.
- **Thread model** is written up in `docs/architecture.md` (Threading, Chat and server list).
- **Hardcoded text:** see L-4.
- **God class:** no. `ChatModule` is wiring plus the staff reader list (113 lines).

## 5. Contract and acceptance check

- **Package and classes:** `ChatModule`, `ChatListener`, `JoinListener`, `MotdListener`, `RankSource`, `LuckPermsRankSource`, `PermissionRankSource`, `ChatSettings`, `MessageCommand`, `StaffChatCommand`, `ReplyTracker`, `MuteExpiryNotifier` all exist. Extras: `ChatConfig`, `RankSources`, `StaffChat`.
- **chat.yml keys:** `format`, `staff-format`, `ranks`, `motd`, `welcome` (plus `default-prefix`). Shipped file loads with no problems (test).
- **config.yml:** `chat.enabled` (default true).
- **Commands:** `msg` (`tell`, `w`, `whisper`, `pm`), `r` (`reply`), `sc` (`staffchat`). `minecraft:msg` untouched.
- **Permissions:** `cookiecore.staff.chat` (op), `cookiecore.chat.silentjoin` (false), `cookiecore.chat.msg` (true); `Perm`, `paper-plugin.yml` and the test agree.
- **Message keys:** `chat.join`, `chat.join-first`, `chat.quit`, `chat.welcome-title`, `chat.welcome-subtitle`, `chat.welcome-line`, `chat.msg-to`, `chat.msg-from`, `chat.msg-self`, `chat.not-online`, `chat.no-reply`, `chat.reply-offline`, `chat.staff-on`, `chat.staff-off`, `chat.players-only`, `chat.usage.*`, `moderation.mute-expired`, all in `messages.yml`. `MessagesTest` checks code keys against the file and strict-parses every message.
- **Sounds:** `chat_message` added to `sounds.yml` (vanilla fallback); `ui_notify` for the expiry notice. `PluginDescriptorTest` checks every `SoundEvents` constant is in `sounds.yml`.
- **Rules:** no `PlayerLoginEvent`, `AsyncPlayerChatEvent`, `.kick(` outside `text/`, `join()`, `Placeholder.parsed` beyond the prefix, or `playSound` outside `sound/`. The architecture tests pass.
- **MiniMessage injection:** player text, `/msg` text, `/sc` text, the display name, the server name and LuckPerms prefixes never reach `deserialize`; they are unparsed placeholders or components. The chat-line test shows `<red>hi &c<bold>x` literal.
- **Renderer:** only `event.renderer(...)` is set, never `event.message(...)`. `ignoreCancelled = true` at HIGH, so a muted player (LOWEST cancel) never reaches it.
- **Join/quit:** HIGH priority, `joinMessage`/`quitMessage` with `null` for silent join. First join by `hasPlayedBefore()`, not `PlayerDirectory`.
- **Welcome:** `Title.title(..., fadeIn, stay, fadeOut)` in ticks from `chat.yml`. A blank text is left out; both blank means no title.
- **MOTD:** `PaperServerListPingEvent#motd(Component)`, no Bukkit call in the handler; `<online>` shows `?` when the count is hidden.
- **Expiry notice:** a repeating task through the injected `repeatOnMainThread`; no database call; per-player state in memory only.

## 6. In-game guide (Paper 26.3, for the PR)

Run with and without LuckPerms, and with `chat.enabled: false` (restart) once.

1. **Chat format:** a plain line shows the rank prefix (`OWNER`, `ADMIN`, ...) only after granting `cookiecore.rank.<name>`. Good: name, then the message as typed.
2. **Literal text:** type `<red>hi &c<bold>x`. Good: the line shows exactly that. Bad: red text, bold, or missing characters.
3. **LuckPerms prefix:** with LuckPerms and a prefix set for you, it replaces the chat.yml rank. Without LuckPerms the chat still works and no error is logged.
4. **Join and quit:** a new name shows "joined for the first time"; a known name "joined the game"; quit shows "left the game". Good: one line each for everyone, none for a player with `cookiecore.chat.silentjoin`.
5. **Welcome:** title and subtitle on screen for about 3 seconds, and the chat line. **Unverified:** a title sent from inside `PlayerJoinEvent` (check it shows). Blank the texts in `messages.yml` and check nothing shows.
6. **Server list:** two lines, the server name and `<online>/<max>`. Good: the counts change with players.
7. **/msg:** `/msg <name> hi`, `/tell`, `/w`, `/whisper`, `/pm`. Good: both sides see the line; the target hears a soft chime. Check `/minecraft:msg hi` still uses vanilla.
8. **/r:** answers the last player who messaged you. Good: works after both sides talked. If the partner left: "no longer online".
9. **Refusals:** `/msg yourself`, `/msg` to an offline player, `/r` with nobody (each its own message). Muted player: `/msg` and `/r` refused with the mute line.
10. **Without `cookiecore.chat.msg`:** `/msg` refused. Good: the group loses the command, the server still starts.
11. **Staff chat:** `/sc` turns the channel on (message), `/sc hi` sends one line, `/sc` turns it off. A non-staff player sees nothing. **Unverified:** the chat report screen with rendered messages (report a public message; the report must still open).
12. **Muted staff:** mute a staff member, they can still `/sc`, and their public chat is blocked. (After M-1 is fixed also: remove their permission while the toggle is on and check public chat stays blocked.)
13. **Tempmute expiry:** `/tempmute <name> 1m`, then wait. Good: "Your mute has ended" once. A permanent mute and an `/unmute` give nothing. A mute that ends while the player is offline gives nothing when they join.
14. **Reload:** break `chat.yml` (bad indent), `/cookiecore reload`. Good: the problem line names the file and what is wrong, and the chat keeps the last good format.
15. **Console:** the staff channel shows in the console log (L-3). Note it in the PR if kept.
16. **Profile:** `/spark profiler start --timeout 60` with about 10 players chatting and someone refreshing the server list. Good: the chat listener, MOTD and `MuteExpiryNotifier` each under 1% of the tick; no growth of the `ConcurrentHashMap`s after players leave (memory dump optional).

## 7. Known and accepted (not findings)

Title from join and the chat report screen are unverified in game (section 6). No console sender for `/msg` and `/sc`. `/minecraft:msg` is not restricted for senders without `cookiecore.chat.msg`. Permission changes show after rejoin or reload. `/msg @a` uses only the first resolved player (vanilla selector).

## 8. Fixes per owner

- **plugin-coder (round 2):** M-1 (exemption checks the permission too), M-2 (`volatile` settings). Optional with the same change: L-1, L-2, L-4, L-5, L-6, PERF-LOW-3.
- **Decisions for the orchestrator:** keep the console as a staff-chat reader (L-3)? Otherwise none.
- **Re-review after the fix:** `git diff 03dce54..HEAD -- src/main` on `core-chat-join/plugin-coder`, plus the regression test for M-1. Tests in full.

## 9. Architecture doc

`docs/architecture.md` updated on this branch: the `chat` package row, `CoreWiring` and the root row, `ChatText` and the `Messages` helpers, the chat and server-list thread model, the enable and disable order, LuckPerms rules, the dependency direction and the test list.

## 10. Round 2 (coder commit `a49eeb4`, tester tests `9cbf4b6`)

Reviewed: `git show a49eeb4` (9 files in `src/main` and `messages.yml`, +50 -26), read in full, with the tests run in full (258 run, 0 failed, 1 skipped; `mise run check` BUILD SUCCESS).

- **M-1 fixed.** `MuteListener.chatExemption` is `volatile Predicate<Player>`, tested with `event.getPlayer()`. `ChatModule.staffChannelActive(Player)` is `staff.active(uuid) && hasPermission(Perm.STAFF_CHAT)`. Checked the three cases: muted with the toggle and no permission is cancelled by `MuteListener` and gets the muted line; muted with the permission is not cancelled by `MuteListener` and reaches the staff branch; unmuted without the permission gets the public format. Tests: `ChatListenerTest` (three cases, real `AsyncChatEvent`). Gap, LOW: `ChatModule` needs a `JavaPlugin`, so the test writes the same expression itself. A package-private static `staffExempt(StaffChat, Player)` would let the test call the production line. The policy is still guarded by in-game step 12.
- **M-2 fixed.** `CookieCore.settings` is `private volatile`. `ArchitectureRulesTest.settingsAreVolatileBecauseTheChatAndPingThreadsReadThem` checks it by reflection.
- **L-1 fixed.** `PermissionRankSource.refreshAll` builds the new map aside and swaps a volatile field (no empty window for the chat thread); a cache miss returns the chat.yml default prefix (`settings.prefixFor(p -> false)`), so no Bukkit permission is read on the chat thread. Test: `ChatSettingsTest.theRankCacheIsSwappedAndAMissNeverReadsPermissions` (a Player proxy fails on `hasPermission`). Trade-off, accepted: a player who is not cached yet shows the default prefix until the next refresh, instead of their rank.
- **L-3 decision applied.** The console stays a staff-chat reader. `ChatModule.staffReaders(Collection, Audience)` is package-visible static. Test: `ChatListenerTest.staffReadersArePermittedPlayersAndTheConsole`.
- **L-4 fixed.** The hidden-count text is `messages.yml: chat.motd-hidden-count` (`"?"`), read through `Messages.raw` on the ping thread (volatile `yaml`, concurrent warn set; a missing key logs once and would show the path, which the jar copy prevents).
- **L-5 fixed.** A motd item that is blank or not text gives one problem line and keeps the default text (`motd` null). Test: `ChatSettingsTest.aMotdItemThatIsNotTextKeepsTheDefaultAndSaysSo`.
- **PERF-LOW-3 fixed.** `MuteExpiryNotifier.tick()` returns before building the set when `watched` is empty. The existing notifier tests cover the behaviour (nothing changes when nothing is watched); no new test.
- **Still open, LOW, PR follow-ups:** L-2 (`hasPermission` read on the chat thread in `ChatListener.java:46` and in `staffChannelActive`; reads only, accepted), L-6 (`CookieCore.java:90` inline fully qualified class, pre-existing), L-7 (staff line dropped silently during shutdown, accepted), PERF-LOW-1 (LuckPerms prefix parsed per message, `LuckPermsRankSource.java:37`).
- **Regression check:** the CoreWiring order and the disable order are unchanged by `a49eeb4`; `CookieCore.java` is still under 200 lines; the chat package stays 14 classes; no new Bukkit call on an async path.

VERDICT: READY (round 2). In-game step 12 (muted staff with the permission removed) must still be checked on a server, since no unit test reaches the `ChatModule` line.
