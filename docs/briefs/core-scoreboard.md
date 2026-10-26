# core-scoreboard

**Status:** in progress  <!-- planned | in progress | review | done -->
**Created:** 2026-10-27 · **Depends on:** `core-foundation`, `core-tidy` (both merged into `main`). Phase 1 (tab) is skipped by the user's choice; nothing here needs it.

## Goal
A clean sidebar for every player that never flickers, shows no red score numbers, costs next to nothing per second, and gives other plugins (CookieClicker later: cookies, CPS) a small, safe way to put their own values on it.

## Player story
I join and see a tidy sidebar with the server name and a few lines about me (name, ping, players online). The numbers update without the board blinking. If I do not want it, `/scoreboard toggle` hides it and the server remembers my choice after a restart.

## Acceptance criteria
- [ ] Each player gets **their own** `Scoreboard` (never the main one), one `SIDEBAR` objective with `NumberFormat.blank()` (no red numbers), the title from `scoreboard.yml`. The `Scoreboard` reference is kept strongly and dropped on quit.
- [ ] Lines are fixed unique entries (`l00` to `l14`) whose text is `Score#customName(Component)`, ordered by unique scores (`n - i`, ties would sort alphabetically). Only the `customName` of a **changed** line is set. No `resetScores` plus re-add, no objective re-registration, no title rewrite unless the title text changed. A tick where nothing changed sends nothing. The Team prefix/suffix technique stays a documented fallback only, to use if Luuk's in-game check shows `customName` does not render with `NumberFormat.blank()` (see the research note).
- [ ] One repeating task for all players (20 ticks), started when the first player gets a sidebar and cancelled when the last one leaves or toggles it off. Main thread only. Rendered text per line is cached per player so unchanged lines are skipped cheaply.
- [ ] `scoreboard.yml` (admin-edited, MiniMessage): `title` and `lines` (1 to 15, blank lines allowed). Built-in placeholders: `<player>`, `<server>` (from `config.yml: server.name`), `<online>`, `<max>`, `<ping>`. MiniMessage templates are parsed **once** at load; each second only values are substituted (values are inserted unparsed, never as MiniMessage). A broken `scoreboard.yml` on `/cookiecore reload` logs the exact problem and keeps the last good values, like the other files.
- [ ] `config.yml: scoreboard.enabled` (default true) is the only new config key. When false nothing is shown and no task runs.
- [ ] **Placeholder registry** in the public API: `CookieCoreApi#scoreboardPlaceholders()` returns `ScoreboardPlaceholders` with `register(Plugin owner, String key, Function<Player, Component> resolver)` and `unregisterAll(Plugin owner)`. Keys match `[a-z0-9_]+` and may not shadow a built-in; a duplicate key is rejected with a clear exception. A resolver that throws or returns `null` renders as empty text and is logged **once** per key. Registered placeholders become `<key>` in `scoreboard.yml`. Unregistering (or the owner plugin disabling) removes it on the next refresh. Documented as part of the only supported surface.
- [ ] `/scoreboard toggle` (alias `/sb toggle`) flips the sidebar for the sender. Permission `cookiecore.scoreboard.toggle`, default **true**. The choice is **persisted per player** in the database through a schema migration (v2), loaded off the main thread on join and saved off the main thread on change; default is visible.
- [ ] Messages in `messages.yml` (`scoreboard.*`), sounds only through `SoundService` (`ui_confirm` when shown, `ui_notify` when hidden). Player-facing text never in Java.
- [ ] Do not fight other plugins: if a player's scoreboard is no longer ours, do not overwrite it behind their back more than once per join (document the behaviour in `docs/architecture.md`).
- [ ] Lean tests for the important parts (diff logic: what is sent on change/no-change/line removal/toggle; placeholder registry rules; `scoreboard.yml` parsing and reload-keeps-last-good; migration v2; the toggle persistence round trip; the architecture rules still pass). No test per class.
- [ ] `mise run check` green; `ownership-check` clean for every merged branch.

## Out of scope
- Tab list, ranks and prefixes (phase 1, skipped), per-world or per-rank boards, animated titles, PlaceholderAPI support.
- Any CookieClicker code; it only uses the registry later.

## Assets needed
| Kind | What | Owner |
|---|---|---|
| model / texture / glyph | none | model-designer |
| sound | none: existing `ui_confirm` and `ui_notify` (vanilla keys, decision D7) | plugin-coder |
| effect | none | vfx-designer |

## Balance impact
none

## Research needed
Done by Citation Cindy in `docs/research/core-scoreboard-apis.md` (gaps on top of section 4 of `core-paper-apis.md`).

## Open questions
None (defaults decided by the orchestrator, see the acceptance criteria).

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| API | `dev.casperrs.cookieCore.api.ScoreboardPlaceholders` | plugin-coder | `register(Plugin, String, Function<Player, Component>)`, `unregisterAll(Plugin)` |
| API | `CookieCoreApi#scoreboardPlaceholders()` | plugin-coder | |
| config key | `config.yml: scoreboard.enabled` | plugin-coder | default true |
| config file | `scoreboard.yml: title, lines` | plugin-coder | MiniMessage, placeholders `<player> <server> <online> <max> <ping>` plus registered keys |
| permission | `cookiecore.scoreboard.toggle` | plugin-coder | default true; `Perm` constant and `paper-plugin.yml` entry |
| command | `/scoreboard toggle`, alias `/sb` | plugin-coder | Brigadier |
| message key | `scoreboard.shown`, `scoreboard.hidden`, `scoreboard.disabled` | plugin-coder | in `messages.yml` |
| sound event | `ui_confirm`, `ui_notify` | plugin-coder | already in `sounds.yml` |
| schema | migration v2: persisted per-player sidebar choice | plugin-coder | table name uses the configured prefix |

Shared files (`Perm`, `paper-plugin.yml`, `messages.yml`, `CookieCore`, `CookieCoreApi`) are also touched by the parallel `core-gamemode` feature: **append only**, do not reorder or reformat existing lines, so the second merge into `main` is a trivial conflict at most.

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| resource-pack-researcher | `docs/research/core-scoreboard-apis.md` | — | feature branch (docs) |
| plugin-coder | everything under acceptance criteria except tests | contract, research note on the feature branch | `core-scoreboard/plugin-coder` |
| plugin-tester | tests, review of the coder branch, `docs/architecture.md` update, integration review | coder DONE | `core-scoreboard/plugin-tester` |

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| resource-pack-researcher | planned | feature branch | n/a | |
| plugin-coder | planned | `core-scoreboard/plugin-coder` | | |
| plugin-tester | planned | `core-scoreboard/plugin-tester` | | |

## Merge order
1. research note (direct on the feature branch)
2. plugin-coder
3. plugin-tester

## Needs human review
- [ ] Check in game: sidebar looks right, no flicker while moving/pinging, no red numbers, `/scoreboard toggle` survives a relog and a restart.
- [ ] Listen to the two vanilla sounds (`ui_confirm`, `ui_notify`) on toggle.
- [ ] `mise.toml` / `.gitattributes` only if touched (none expected).
