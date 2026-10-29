# core-tab

**Status:** in progress  <!-- planned | in progress | review | done -->
**Created:** 2026-10-29 · **Depends on:** `core-scoreboard` (#7) and `core-chat-join` (#12), both merged. Phase 1 of `docs/briefs/ROADMAP.md`, which the user skipped earlier and then asked for on 2026-10-29 ("Fase 1 met tab mag nu alsnog").

## Goal
A tab list that looks finished: a header and footer with the server name, online count and ping/TPS, and players sorted by rank with their prefix. It must not flicker and must not spam packets.

## Player story
I press Tab and see the server name on top, a line with how many players are online, and at the bottom my ping and the server's TPS. Staff and ranks come first, each name has its rank prefix, the rest are in alphabetical order. Nothing blinks while ping changes.

## Acceptance criteria
- [ ] `tab.yml` (admin-edited, MiniMessage, loaded and reloaded like `scoreboard.yml` and `chat.yml`: a broken file on reload keeps the last good values and reports the exact problem; on first load it falls back to the built-in copy): `header` and `footer` (lists of lines, joined with newlines, blank lines allowed) and `name-format` (default `<rank_prefix><player>`). Placeholders: `<player> <server> <online> <max> <ping> <tps> <rank_prefix>` plus every key registered in the existing `ScoreboardPlaceholders` registry (so CookieClicker can put cookies/CPS in the tab later). Templates are parsed once at load; values are inserted as plain components, never parsed as MiniMessage.
- [ ] `config.yml: tab.enabled` (default true). When false nothing is sent and no task runs; `/cookiecore reload` applies it live (unlike `chat.enabled`). No other new config keys; intervals are code constants.
- [ ] **No flicker, no packet spam:** one repeating task for all viewers (20 ticks, through the injected `repeatOnMainThread`, started with the first viewer and cancelled when none is left). Per viewer it caches the last rendered header, footer and list name and sends a packet **only when the rendered text changed** (`<ping>` and `<tps>` change often: round them to a stable display, for example ping in steps the text shows exactly and TPS to one decimal capped at 20.0, so a jitter inside the displayed value does not resend). A tick where nothing changed sends nothing.
- [ ] **Sorted by rank, no teams:** use Paper's player list order (`Player#setPlayerListOrder(int)`, confirm the exact semantics in the research note) instead of scoreboard teams, because every player has their own scoreboard (phase 2). Sort key: the player's rank order (lower = higher in the list). Rank order comes from the same source as the chat prefix: LuckPerms primary group weight when LuckPerms is present (higher weight first), else the index in `chat.yml: ranks`, else the default rank last; ties fall back to the vanilla name order. The order and list name are set on join and on `/cookiecore reload`; a rank change shows at the next join or reload (the same rule as the chat prefix).
- [ ] **Prefix in the list name:** `Player#playerListName(Component)` set from `name-format` with the shared rank prefix. Reuse the existing `RankSource` (chat package) rather than duplicating the LuckPerms/permission logic: if that needs a small move into a shared package (for example `rank`), do it as a behaviour-preserving refactor first and keep `net.luckperms` references in the one `LuckPermsRankSource` class (the architecture test stays green). Tab works with `chat.enabled: false`.
- [ ] Join and quit: a joining player gets header, footer, name and order in the same tick as the join; per-player cache entries are removed on quit (nothing grows with the number of logins). Players online during a `/reload` or plugin enable are handled like in the scoreboard module.
- [ ] Do not fight other plugins: if another plugin sets a different list name or header for a player, we only rewrite when our own rendered text changed, never in a loop. Document the behaviour in `docs/architecture.md`.
- [ ] Texts: header/footer/name-format live in `tab.yml`; any other player-facing text in `messages.yml` (hyphenated keys). No sounds. Scheduler only referenced from `CookieCore.java` (modules receive the existing functions); wiring in `CoreWiring` (mind the 200-line cap of `CookieCore.java`, now 193).
- [ ] Lean tests: the diff logic (what is sent on change, no change, quit, reload, toggle of `tab.enabled`); rank order from LuckPerms weight, from `chat.yml` ranks and default last; `tab.yml` parsing, unparsed values (`<red>` in a player name stays text), broken-file rules; rounding of ping/TPS; the architecture rules still pass. `mise run check` green.

## Out of scope
Per-world tabs, animated headers, ping bars or custom latency icons, vanish handling (phase 6 will hide vanished players from the list), team-based sorting, nick names, skins/heads in the header (resource pack).

## Assets needed
| Kind | What | Owner |
|---|---|---|
| model / texture / glyph | none (plain text; a logo glyph needs the resource pack, roadmap open question 2: default no) | model-designer |
| sound | none | sound-designer |
| effect | none | vfx-designer |

## Balance impact
none

## Research needed
Done by Citation Cindy in `docs/research/core-tab-apis.md` (gaps on top of `core-paper-apis.md` and `core-scoreboard-apis.md`): (1) Paper 26.3 `Player#setPlayerListOrder(int)` and `getPlayerListOrder()`: exact meaning (higher or lower first), default, scope (viewer-independent?), whether it interacts with scoreboard teams or spectators, and when it is sent; (2) `Player#playerListName(Component)`, `sendPlayerListHeaderAndFooter(Component, Component)` (and the separate header/footer setters): what is sent when, whether Paper deduplicates identical values, cost per call; (3) TPS and tick time: `Bukkit.getTPS()`, `getAverageTickTime()`, `getTickTimes()` thread-safety and cost, and what a sensible displayed value is; (4) `Player#getPing()` thread-safety and update rate; (5) reading the LuckPerms primary group and its weight from the cached user data without blocking (`getPrimaryGroup`, `GroupManager#getGroup`, `Group#getWeight`), and the permission-based fallback; (6) pitfalls: setting list name inside `PlayerJoinEvent`, a plugin enable after players are online, other plugins overwriting list names, name length limits for list names; (7) anything in 26.x that changed here since 1.21.

## Open questions
None blocking. Defaults decided by the orchestrator (overrule in review): English default texts; header = server name on one line and a short subtitle; footer = `Ping`, `TPS` and `Online` in one line; rank order = LuckPerms weight, else `chat.yml` rank order; tab updates once per second at most.

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| package | `dev.casperrs.cookieCore.tab` | plugin-coder | `TabModule`, `TabListener` (join/quit), `TabService` (the diff and the task), `TabSettings` + `TabConfig` (parsed `tab.yml`), `TabSink` (the Paper calls, easy to fake) |
| config file | `tab.yml: header, footer, name-format` | plugin-coder | MiniMessage |
| config key | `config.yml: tab.enabled` | plugin-coder | default true, live on reload |
| placeholder | `<player> <server> <online> <max> <ping> <tps> <rank_prefix>` + registered keys | plugin-coder | reuse `PlaceholderRegistry` |
| refactor | shared `RankSource` (and order) usable by `chat` and `tab` | plugin-coder | behaviour-preserving, only if needed |
| permission | none | | |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| resource-pack-researcher | `docs/research/core-tab-apis.md` | — | feature branch (docs) |
| plugin-coder | everything above except tests | contract, research note on the feature branch | `core-tab/plugin-coder` |
| plugin-tester | tests, branch review, `docs/architecture.md` | coder DONE | `core-tab/plugin-tester` |

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| resource-pack-researcher | planned | feature branch | n/a | |
| plugin-coder | planned | `core-tab/plugin-coder` | | |
| plugin-tester | planned | `core-tab/plugin-tester` | | |

## Merge order
1. research note (direct on the feature branch)
2. plugin-coder
3. plugin-tester

## Needs human review
- [ ] In-game on Paper 26.3: header/footer look right, ping/TPS change without flicker (two minutes), ranks sorted and prefixed, with and without LuckPerms, a newly joined player appears in the right place, `tab.yml` edit plus `/cookiecore reload`, `tab.enabled: false` plus reload, `/reload`-style restart with players online.
- [ ] Check the default texts in `tab.yml`.
- [ ] Built on the user's "Fase 1 met tab mag nu alsnog" without a separate brief-approval step; defaults are listed under Open questions.
