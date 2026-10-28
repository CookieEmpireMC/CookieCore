# core-chat-join

**Status:** review (branch verdict READY after one fix round; waits for the in-game check)  <!-- planned | in progress | review | done -->
**Created:** 2026-10-28 · **Depends on:** `core-moderation-bans` (#9), `core-moderation-mutes` (#10), `core-moderation-warns` (#11), all merged. Phase 5 of `docs/briefs/ROADMAP.md`.

## Goal
Chat and arrival feel finished: a readable chat with the player's rank, proper join/quit lines, a welcome, a server-list MOTD, private messages and a staff channel. It also closes the open end of phase 4b: the player is told when a tempmute ends.

## Player story
I join and see a short welcome title and a welcome line; everybody sees "Steve joined". In chat my name has my rank in front of it. `/msg Alex hi` and `/r` talk privately; if I am muted I cannot. Staff can use `/sc <text>` for a channel only they see. In the server list the server shows a two-line MOTD. When my tempmute runs out I get a message.

## Acceptance criteria
- [ ] **Make room first (no behaviour change):** `CookieCore.java` is at 199 of the 200 lines the architecture test allows. Move the listener and command registration out of it into a small wiring class (or the modules' own `register` methods) so the new modules fit. The scheduler stays referenced only from `CookieCore.java` (modules receive the existing `runOnMainThread`/`repeatOnMainThread` functions). Existing tests stay green.
- [ ] **Chat format:** Paper's `AsyncChatEvent` renderer (`ChatRenderer`) formats every message as `chat.format` from `chat.yml` (MiniMessage), with the placeholders `<rank_prefix>`, `<player>`, `<message>`, `<server>`. The message is inserted **unparsed** (no MiniMessage injection by players; the signed chat message component is kept, not re-parsed). Muted players are still handled by `MuteListener` (LOWEST) and never reach the renderer.
- [ ] **Ranks:** `chat.yml: ranks` is an ordered list of `{permission, prefix}`; the first permission a player has wins (`default` prefix when none). If the **LuckPerms** soft dependency is present, its meta prefix is used instead when the player has one (read from LuckPerms' cached meta, no database call on the chat path). Core still boots and works without LuckPerms and without the LuckPerms classes loading (isolate every LuckPerms reference in one class that is only loaded when the plugin is present).
- [ ] **Join/quit:** `PlayerJoinEvent`/`PlayerQuitEvent` messages replaced by `chat.join`, `chat.quit` (`messages.yml`); first-time players get `chat.join-first`. A player with `cookiecore.chat.silentjoin` (default false) joins and quits without a message.
- [ ] **Welcome:** on join the player gets a title and subtitle (`chat.welcome-title`, `chat.welcome-subtitle`) with fade times from `chat.yml`, and a welcome chat line (`chat.welcome-line`), all optional (empty text disables that part). No scheduler: use `Player#showTitle` with `Title.Times`.
- [ ] **MOTD:** server-list MOTD from `chat.yml: motd` (two MiniMessage lines, `<server>` and `<online>` allowed) through Paper's `PaperServerListPingEvent`; reloadable.
- [ ] **Private messages:** `/msg <player> <text>` (aliases `/tell`, `/w`, `/whisper`, `/pm`), `/r <text>` (reply to the last partner). They replace the vanilla commands for the unqualified labels, like `/kick` does (the 4a override technique); `minecraft:msg` stays vanilla. Online players only; sender sees `to <target>: ...`, target sees `from <sender>: ...` plus a quiet sound via `SoundService` on the main thread. The text is unparsed. Muted players are refused with the existing `moderation.muted-command` line. `/r` with no partner or an offline partner gives a clear message. Reply targets are kept in memory per player (UUID) and dropped on quit. The `MuteListener` whisper block for `/msg /tell /w /whisper` stays as the safety net for `minecraft:`-prefixed use; keep `/me` blocked.
- [ ] **Staff channel:** `/sc <text>` (alias `/staffchat`) and a toggle `/sc` with no text (staff messages go to the channel until toggled off; toggle state in memory, dropped on quit). Receivers: online players with `cookiecore.staff.chat`. Format `chat.staff-format` in `chat.yml`. Permission `cookiecore.staff.chat` default op. A muted staff member can still use staff chat.
- [ ] **Tempmute expiry notice:** a repeating task (period 20 ticks or more, started through the injected `repeatOnMainThread`, cancelled when no player is online) checks the **online** players' mutes in memory (`MuteList`) and sends `moderation.mute-expired` once when a tempmute has just run out; no database call, no per-player timers, never for permanent mutes. A mute that expired while the player was offline gets no message.
- [ ] Permissions: `cookiecore.staff.chat` (default op, in `cookiecore.staff.*`), `cookiecore.chat.silentjoin` (default false), `cookiecore.chat.msg` (default true, so a server can take `/msg` away from a group). `Perm`, `Perm.ALL` and `paper-plugin.yml` kept in sync.
- [ ] Config: new file `chat.yml` (admin-edited, loaded and reloaded like `scoreboard.yml`: a broken file on reload keeps the last good values and reports the exact problem; on first load it falls back to the built-in copy). `config.yml: chat.enabled` (default true) turns the whole chat layer off without removing the commands of other modules.
- [ ] All player text in `messages.yml` (`chat.*`, `moderation.mute-expired`), keys match `[a-z0-9]+(\.[a-z0-9-]+)+`; sounds only through `SoundService` (a new event is fine only as a vanilla fallback in `sounds.yml`, owned by the coder here per D7). No `.kick(` outside `text/`, no `PlayerLoginEvent`, no `join()`.
- [ ] Lean tests: renderer output with and without a rank and with a MiniMessage-looking player message; rank selection order; reply tracking and quit cleanup; muted refusal for `/msg`; staff-chat recipients and toggle; join/quit/first-join text; the MOTD placeholders; `chat.yml` broken-file rules; expiry notice sends exactly once and not for permanent mutes; the LuckPerms class is not loaded when the plugin is absent (architecture-style test). `mise run check` green.

## Out of scope
Tab list (phase 1, skipped), chat filters / anti-spam / caps, mentions, chat history, per-world chat, nicknames, Discord bridge, a `/ignore` command, `/me` as a feature (stays blocked for muted players, vanilla otherwise).

## Assets needed
| Kind | What | Owner |
|---|---|---|
| model / texture / glyph | none | model-designer |
| sound | none: vanilla fallbacks only (D7) | plugin-coder |
| effect | none | vfx-designer |

## Balance impact
none

## Research needed
Done by Citation Cindy in `docs/research/core-chat-join-apis.md`: (1) Paper 26.3 `AsyncChatEvent` and `ChatRenderer` (viewer-aware renderer, keeping signed messages valid, what `message()` returns, how to avoid breaking chat reporting); (2) the LuckPerms API (Maven coordinates and an existing stable version, `provided` scope, reading `CachedMetaData#getPrefix` from a `Player` without blocking, soft-dependency class loading under Paper's plugin loader with `join-classpath: true`); (3) overriding the unqualified `msg`/`tell`/`w`/`whisper` Brigadier commands the same way 4a did for `kick` (read `core-moderation-bans-apis.md` first), and what `minecraft:msg` still does; (4) join/quit message API and the first-join check (`Player#hasPlayedBefore` versus our own player directory); (5) `Player#showTitle` and `Title.Times`; (6) `PaperServerListPingEvent#motd(Component)`; (7) anything in Paper 26.3 that changed for chat or server-list events since 1.21.

## Open questions
None blocking. Defaults decided by the orchestrator (overrule in review): English default texts; the prefix list in `chat.yml` ships with `owner/admin/mod/helper/member` as examples bound to `cookiecore.rank.<name>` permissions (they do nothing until someone grants them); LuckPerms wins over `chat.yml` when it has a prefix; staff chat has a toggle plus a one-shot form; private messages are online-only; no `/ignore`.

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| package | `dev.casperrs.cookieCore.chat` | plugin-coder | `ChatModule`, `ChatListener` (renderer), `JoinListener`, `MotdListener`, `RankSource` (+ `LuckPermsRankSource`, `PermissionRankSource`), `ChatSettings` (parsed `chat.yml`), `MessageCommand` (`/msg`, `/r`), `StaffChatCommand`, `ReplyTracker`, `MuteExpiryNotifier` |
| config file | `chat.yml: format, staff-format, ranks, motd, welcome` | plugin-coder | MiniMessage, placeholders as above |
| config key | `config.yml: chat.enabled` | plugin-coder | default true |
| command | `/msg` (`/tell`, `/w`, `/whisper`, `/pm`), `/r`, `/sc` (`/staffchat`) | plugin-coder | Brigadier |
| permission | `cookiecore.staff.chat`, `cookiecore.chat.silentjoin`, `cookiecore.chat.msg` | plugin-coder | `Perm` + `paper-plugin.yml` |
| message keys | `chat.join`, `chat.join-first`, `chat.quit`, `chat.welcome-*`, `chat.msg-to`, `chat.msg-from`, `chat.no-reply`, `chat.reply-offline`, `chat.staff-on`, `chat.staff-off`, `moderation.mute-expired`, usage keys | plugin-coder | `messages.yml`, hyphens |
| sound event | `ui_confirm`, `ui_notify` (existing), `chat_message` (new vanilla fallback) | plugin-coder | `sounds.yml` |
| dependency | `net.luckperms:api` | plugin-coder | `provided` scope, soft dependency already declared in `paper-plugin.yml`; version from the research note |

## Decisions folded in from the research (`docs/research/core-chat-join-apis.md`)
- Chat: set only `event.renderer(ChatRenderer.viewerUnaware(...))`, never `event.message(...)`; the player's message goes in with `Placeholder.component`, so `<red>hi` stays literal. `ChatListener` runs at HIGH with `ignoreCancelled = true` (`MuteListener` cancels at LOWEST). `ChatSettings` is a volatile immutable snapshot (the event is async); make sure `Messages` reads are safe from async threads.
- `RankSource` returns a `Component`. LuckPerms prefixes are legacy `&` strings (`LegacyComponentSerializer.legacyAmpersand()`); `chat.yml` prefixes are MiniMessage. Use `UserManager#getUser(UUID)` (null if not loaded; never `loadUser().join()`, never `PlayerAdapter#getUser`) and fall back to `PermissionRankSource` on null/blank. The `net.luckperms` imports live only in `LuckPermsRankSource`, reached through a factory that checks `isPluginEnabled("LuckPerms")` and catches `LinkageError | RuntimeException`; add an architecture test that no other class references `net.luckperms`. `PermissionRankSource` on the async chat thread can read stale permission data (Bukkit `PermissibleBase` is not synchronized): follow the note's alternative (resolve the rank on the main thread at join/permission change and cache it, or read it in a main-thread step before the renderer runs).
- `pom.xml`: `net.luckperms:api:5.5`, scope `provided`, no new repository (a human-review item).
- Commands: `register(node, desc, List.of("tell","w","whisper","pm"))` takes over vanilla `msg`/`tell`/`w`; add `/reply` as alias of `/r`; at start-up warn for any of `msg tell w whisper pm r reply` that did not register. `minecraft:msg` stays vanilla. Add `pm`, `r`, `reply` to the `MuteListener` label set (and keep the in-command mute check).
- Join/quit: `joinMessage(Component)` / `quitMessage(Component)` at HIGH, `null` = no line. First join is `Player#hasPlayedBefore()`, **not** `PlayerDirectory` (order-dependent).
- Welcome title: `Title.title(t, s, fadeInTicks, stayTicks, fadeOutTicks)`; fade times in `chat.yml` as ticks.
- MOTD: `PaperServerListPingEvent#motd(Component)` is async: no Bukkit API in the handler; `<online>` from `getNumPlayers()` (-1 if hidden), `<max>` from `getMaxPlayers()`.
- PR text must say: replacing vanilla `/msg` makes private messages system messages, which cannot be reported by the client.
- Unverified until tested in game: the chat report screen with rendered messages, `/minecraft:msg` staying vanilla, a title sent from inside `PlayerJoinEvent`, which of `whisper/pm/r/reply` are free on the real server.

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| resource-pack-researcher | `docs/research/core-chat-join-apis.md` | — | feature branch (docs) |
| plugin-coder | everything under acceptance criteria except tests, in the commit order: make room, chat+ranks, join/welcome/MOTD, messages+staff chat, expiry notice | contract, research note on the feature branch | `core-chat-join/plugin-coder` |
| plugin-tester | tests, branch review, `docs/architecture.md`, integration review | coder DONE | `core-chat-join/plugin-tester` |

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| resource-pack-researcher | done | feature branch | n/a | `docs/research/core-chat-join-apis.md` committed (one stalled run, resumed) |
| plugin-coder | done (merged, one fix round) | `core-chat-join/plugin-coder` | READY | `CoreWiring` extracted (`CookieCore.java` 199 to 193 lines); `chat` package (14 classes); steps 2 to 5 in one commit. Added `chat.yml: default-prefix`; `motd` is a list of 1-2 lines; `welcome` holds only fade ticks (texts in `messages.yml`); `/reply` alias; keys `chat.not-online`, `chat.msg-self`, `chat.players-only`, `chat.usage.*`, `chat.motd-hidden-count`; `chat.enabled` is start-up only; players only for `/msg` and `/sc` (console reads staff chat). Cut: `minecraft:msg` restriction for senders without `cookiecore.chat.msg`; multi-target selectors in `/msg` use the first player. |
| plugin-tester | done (merged) | `core-chat-join/plugin-tester` | READY | 258 tests; `docs/reviews/2026-10-28-core-chat-join.md` (two rounds); fixed M-1 (muted staff with toggle on and permission gone) and M-2 (non-volatile `settings`) |

## Merge order
1. research note (direct on the feature branch)
2. plugin-coder
3. plugin-tester

## Needs human review
- [ ] In-game on Paper 26.3: chat looks right with and without LuckPerms, a message like `<red>hi` shows as plain text, join/quit/first-join lines, welcome title, server-list MOTD, `/msg` and `/r`, `/minecraft:msg` still vanilla, `/sc` and its toggle, a muted player cannot `/msg` but can `/sc` if staff, the tempmute expiry message arrives.
- [ ] LuckPerms is a new `provided` Maven dependency in `pom.xml` (human-review item).
- [ ] Check the texts and the sample ranks in `chat.yml` / `messages.yml`.
- [ ] Built on the user's "Ga aan de slag!" without a separate brief-approval step; defaults are listed under Open questions.
