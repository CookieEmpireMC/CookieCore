# core-gamemode

**Status:** in progress  <!-- planned | in progress | review | done -->
**Created:** 2026-10-27 · **Depends on:** `core-foundation`, `core-tidy` (both merged into `main`)

## Goal
Fast, forgiving game mode commands for staff, with the comfort commands `/fly` and `/speed`, that give clear feedback and tab completion and never spam the target or the console.

## Player story
As staff I type `/gm c` (or `/gmc`, or `/gamemode creative Steve`) and get an immediate confirmation with a soft sound. Steve is told who changed his mode. Typing a mode I already have just says so. `/fly` toggles flight, `/speed 5` makes me faster and `/speed reset` puts it back.

## Acceptance criteria
- [ ] `/gamemode <mode> [player]` with alias `/gm`, and shortcuts `/gms`, `/gmc`, `/gma`, `/gmsp [player]`. `<mode>` accepts `survival|creative|adventure|spectator`, `0` to `3` and `s|c|a|sp`, with completion for the full names. The target is an online player (`ArgumentTypes.player()` resolved for the sender); console may target. Our command replaces the vanilla unqualified `/gamemode` as far as Paper allows (see the research note); `minecraft:gamemode` stays reachable.
- [ ] Permissions: `cookiecore.gamemode.survival|creative|adventure|spectator` (changing yourself), `cookiecore.gamemode.others` (changing others), `cookiecore.gamemode.*` as the wildcard; default **op**. A sender who lacks the node for that mode gets the no-permission message, and completion hides modes they cannot use.
- [ ] Feedback: a confirmation message with the mode name and `ui_confirm`; when changing someone else, the target gets a short notice naming the staff member and `ui_notify`, the sender a confirmation naming the target. Changing to the mode a player already has changes nothing and says so (no error sound). Unknown mode or unknown/offline target gives a precise error and `ui_error`.
- [ ] `/fly [player]` toggles `allowFlight` (and starts flying when in the air). Permissions `cookiecore.fly` and `cookiecore.fly.others`, default op. Creative/spectator already fly: the command says so instead of pretending. Messages for on/off/for-others.
- [ ] `/speed <1-10|reset> [walk|fly] [player]`: `1` is the vanilla default, `10` is the maximum, linear in between (walk `0.2 to 1.0`, fly `0.1 to 1.0`, exact mapping verified in the research note). Without `walk|fly` it affects the mode the player is in (flying means fly speed). Permissions `cookiecore.speed` and `cookiecore.speed.others`, default op. `reset` restores the vanilla defaults.
- [ ] All text in `messages.yml` (`gamemode.*`, `fly.*`, `speed.*`; mode names are keys too), sounds only through `SoundService`, no player text in Java.
- [ ] Nothing in the console log on success; nothing runs on a timer.
- [ ] Lean tests for the important parts (mode parser incl. numbers/aliases/invalid; speed mapping and clamping both ways; permission-per-mode logic; same-mode no-op; command tree shape and completion; the architecture rules still pass). No test per class.
- [ ] `mise run check` green; `ownership-check` clean for every merged branch.

## Out of scope
- Persisting fly/speed in our own database (the server already stores player abilities; the research note says what survives a restart).
- Per-world game modes, `/gamemode` for offline players, command blocks, vanish/god mode.
- Moderation commands (phase 4).

## Assets needed
| Kind | What | Owner |
|---|---|---|
| model / texture / glyph | none | model-designer |
| sound | none: existing `ui_confirm`, `ui_notify`, `ui_error` (vanilla keys, decision D7) | plugin-coder |
| effect | none | vfx-designer |

## Balance impact
none

## Research needed
Done by Citation Cindy in `docs/research/core-gamemode-apis.md` (replacing vanilla `/gamemode`, game mode argument, fly/speed ranges, `PlayerGameModeChangeEvent`).

## Open questions
None (defaults decided by the orchestrator, see the acceptance criteria).

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| command | `/gamemode` (`/gm`), `/gms` `/gmc` `/gma` `/gmsp`, `/fly`, `/speed` | plugin-coder | Brigadier, `command/` helpers |
| permission | `cookiecore.gamemode.survival`, `.creative`, `.adventure`, `.spectator`, `.others`, `.*`; `cookiecore.fly`, `cookiecore.fly.others`; `cookiecore.speed`, `cookiecore.speed.others` | plugin-coder | `Perm` constants and `paper-plugin.yml` entries, default op |
| message key | `gamemode.*`, `fly.*`, `speed.*` | plugin-coder | in `messages.yml` |
| sound event | `ui_confirm`, `ui_notify`, `ui_error` | plugin-coder | already in `sounds.yml` |
| config key | none | | no new config |

Shared files (`Perm`, `paper-plugin.yml`, `messages.yml`, `CookieCore`) are also touched by the parallel `core-scoreboard` feature: **append only**, do not reorder or reformat existing lines, so the second merge into `main` is a trivial conflict at most.

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| resource-pack-researcher | `docs/research/core-gamemode-apis.md` | — | feature branch (docs) |
| plugin-coder | everything under acceptance criteria except tests | contract, research note on the feature branch | `core-gamemode/plugin-coder` |
| plugin-tester | tests, review of the coder branch, `docs/architecture.md` update, integration review | coder DONE | `core-gamemode/plugin-tester` |

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| resource-pack-researcher | planned | feature branch | n/a | |
| plugin-coder | planned | `core-gamemode/plugin-coder` | | |
| plugin-tester | planned | `core-gamemode/plugin-tester` | | |

## Merge order
1. research note (direct on the feature branch)
2. plugin-coder
3. plugin-tester

## Needs human review
- [ ] Check in game: `/gm c`, `/gmc Steve`, completion, the no-permission case, `/fly`, `/speed 5`, `/speed reset`, and that vanilla `minecraft:gamemode` still works.
- [ ] Listen to the vanilla sounds on confirm/notify/error.
- [ ] `mise.toml` / `.gitattributes` only if touched (none expected).
