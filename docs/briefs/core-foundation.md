# core-foundation

**Status:** planned  <!-- planned | in progress | review | done -->
**Created:** 2026-10-20 · **Depends on:** none (phase 0 of [ROADMAP](ROADMAP.md))

## Goal
Give CookieCore a real plugin skeleton and the shared services every later phase builds on, so tab, scoreboard, gamemode and moderation are each a small, tight feature instead of each reinventing config, messages, sounds, commands and storage.

## Player story
As a server owner, I drop CookieCore on a Paper 26.3 server and it boots cleanly with a clear log, creates its config, and answers `/cookiecore version` and `/cookiecore reload`. As the next developer (or agent), I find one obvious place for messages, sounds, permissions, commands and database access, plus tests that stop me from breaking the rules.

## Acceptance criteria
- [ ] `pom.xml` for Paper `26.3.build.205-stable` (same as CookieClicker), Java 25, `provided` Paper, no shading of Paper/Adventure; `plugin.yml` with `api-version: '26.3'`, name `CookieCore`, soft-depend `LuckPerms`
- [ ] Package `dev.casperrs.cookieCore`; main class `CookieCore`; enable/disable order documented in `docs/architecture.md`, every disable step wrapped so one throw never skips the storage flush
- [ ] `config.yml` (server name, database settings H2 default / MariaDB, language-free), `messages.yml` (MiniMessage, placeholders unparsed), `sounds.yml` (vanilla fallbacks) loaded and validated; a broken file logs a precise error and keeps the last good values on `/cookiecore reload`
- [ ] `Messages` service (MiniMessage from `messages.yml`) and `SoundService` (the only place that plays sounds), both with unit tests
- [ ] `Perm` helper holding every permission node as a constant (`cookiecore.admin.reload`, `cookiecore.admin.version`), registered in `plugin.yml`
- [ ] Brigadier command helper (Paper lifecycle API): `/cookiecore version|reload`, permission-gated, with completion; helper makes later commands 10 lines each (target argument for online and cached offline players, duration argument, greedy reason)
- [ ] `storage` package: connection factory, single IO thread, schema versioning table (`schema_version`), migration runner, H2 default and MariaDB, same pattern as CookieClicker; one table `players(uuid, name, first_seen, last_seen)` filled on join
- [ ] `PlayerDirectory`: cached name to UUID lookup, case-insensitive, works for offline players seen before; all DB access off the main thread
- [ ] Small public API `CookieCoreApi` registered through `ServicesManager` (version, `PlayerDirectory` lookup only for now); documented as the only supported surface
- [ ] `ArchitectureRulesTest` (ArchUnit or reflection, like CookieClicker): no `playSound` outside `sound/`, no `spawnParticle` anywhere, no Bukkit calls from `storage/`, no text literals sent to players outside `text/`
- [ ] `mise run build`, `test`, `check`, `team`, `ownership-check` exist (ported from CookieClicker `mise.toml` / `tools/ownership`); `mise run check` is green and CI (`.github/workflows/ci.yml`) runs it
- [ ] `docs/architecture.md` created by the tester; `README.md` and `CLAUDE.md` updated by Rick to say what CookieCore is

## Out of scope
- Any tab, scoreboard, gamemode, chat or moderation behaviour (phases 1 to 5)
- LuckPerms API usage (phase 1 introduces it)
- Resource pack, models, effects, balance

## Assets needed
| Kind | What | Owner |
|---|---|---|
| model / texture / glyph | none | model-designer |
| sound | shared UI events: `ui_confirm`, `ui_error`, `ui_notify` (short, quiet, original; used by every later phase) | sound-designer |
| effect | none | vfx-designer |

## Balance impact
none

## Research needed
Citation Cindy, one note `docs/research/core-paper-apis.md` (verified against Paper 26.3 Javadocs, short, with exact signatures):
1. Brigadier command registration through `LifecycleEvents.COMMANDS`: custom argument types (player incl. offline, duration, greedy string), suggestion providers, requirements/permissions
2. `AsyncPlayerPreLoginEvent` vs `PlayerConnectionValidateLoginEvent` (or the 26.x equivalent) for denying a login with a component, and what is safe off the main thread
3. `AsyncChatEvent` and `ChatRenderer` basics (needed for phases 4b and 5)
4. Scoreboard/tab APIs for phases 1 and 2: `Player#sendPlayerListHeaderAndFooter`, `Player#playerListName`, `Team` sorting, `Objective#numberFormat`, per-player `Scoreboard`; note anything removed or renamed in 26.x
5. LuckPerms: how a plugin reads prefix and group weight through `net.luckperms:api` as `provided`/soft dependency

## Open questions
None blocking (defaults in [ROADMAP](ROADMAP.md) decisions D1 to D6).

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| sound event | `ui_confirm` | sound-designer | key `cookiecore:ui.confirm`, fallback `minecraft:block.note_block.pling`; short, soft, rising |
| sound event | `ui_error` | sound-designer | key `cookiecore:ui.error`, fallback `minecraft:block.note_block.bass`; short, dull |
| sound event | `ui_notify` | sound-designer | key `cookiecore:ui.notify`, fallback `minecraft:entity.experience_orb.pickup`; subtle ping |
| message key | `command.no-permission`, `command.unknown-player`, `command.reloaded`, `command.reload-failed`, `command.version` | plugin-coder | in `messages.yml` |
| config key | `config.yml: server.name`, `database.type` (`h2`\|`mariadb`), `database.file`, `database.host`, `database.port`, `database.name`, `database.user`, `database.password` | plugin-coder | |
| permission | `cookiecore.admin.reload`, `cookiecore.admin.version` | plugin-coder | `Perm` constants |
| service | `CookieCoreApi` (`dev.casperrs.cookieCore.api`) | plugin-coder | via `ServicesManager`; `PlayerDirectory` read-only |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| resource-pack-researcher | write `docs/research/core-paper-apis.md` (5 questions above) | — | feature branch (docs) |
| sound-designer | three UI sounds as `.ogg` plus `sounds.yml` and specs, pack folder `cookiecore` namespace | contract | `core-foundation/sound-designer` |
| plugin-coder | everything under acceptance criteria except sounds files and tests | contract, research note on the feature branch | `core-foundation/plugin-coder` |
| plugin-tester | review each branch, write `ArchitectureRulesTest` and service tests, create `docs/architecture.md`, integration review | owner DONE | (reviewed branch) |

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| resource-pack-researcher | planned | feature branch | | |
| sound-designer | planned | `core-foundation/sound-designer` | | |
| plugin-coder | planned | `core-foundation/plugin-coder` | | |
| plugin-tester | planned | | | |

## Merge order
1. research note (direct on the feature branch)
2. sound-designer
3. plugin-coder

## Needs human review
- [ ] Audition the three UI sounds (`ui_confirm`, `ui_error`, `ui_notify`)
- [ ] `mise.toml` changes (new tasks) and `.gitattributes` if touched
- [ ] Start a Paper 26.3 test server with the jar: clean boot log, `/cookiecore version`, `/cookiecore reload`, `players` table filled after a join
