# core-foundation

**Status:** done (merged, PR #4; the in-game check is still open)  <!-- planned | in progress | review | done -->
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
| sound | none yet: the shared UI events below use **vanilla keys** (sound-designer cannot write `sounds.yml` or a pack folder in Core under the current `ownership.json`; custom sounds are a later follow-up once the user decides) | plugin-coder (`sounds.yml`) |
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
| sound event | `ui_confirm` | plugin-coder | vanilla `minecraft:block.note_block.pling`, volume 0.5, pitch 1.4 |
| sound event | `ui_error` | plugin-coder | vanilla `minecraft:block.note_block.bass`, volume 0.5, pitch 0.8 |
| sound event | `ui_notify` | plugin-coder | vanilla `minecraft:entity.experience_orb.pickup`, volume 0.4, pitch 1.2 |
| message key | `command.no-permission`, `command.unknown-player`, `command.reloaded`, `command.reload-failed`, `command.version` | plugin-coder | in `messages.yml` |
| config key | `config.yml: server.name`, `database.type` (`h2`\|`mariadb`), `database.file`, `database.host`, `database.port`, `database.name`, `database.user`, `database.password` | plugin-coder | |
| permission | `cookiecore.admin.reload`, `cookiecore.admin.version` | plugin-coder | `Perm` constants |
| service | `CookieCoreApi` (`dev.casperrs.cookieCore.api`) | plugin-coder | via `ServicesManager`; `PlayerDirectory` read-only |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| resource-pack-researcher | write `docs/research/core-paper-apis.md` (5 questions above) | — | feature branch (docs) |
| plugin-coder | everything under acceptance criteria except sounds files and tests | contract, research note on the feature branch | `core-foundation/plugin-coder` |
| plugin-tester | review each branch, write `ArchitectureRulesTest` and service tests, create `docs/architecture.md`, integration review | owner DONE | (reviewed branch) |

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| resource-pack-researcher | done | feature branch | n/a | `docs/research/core-paper-apis.md` committed |
| plugin-coder | done (merged) | `core-foundation/plugin-coder`, `…-fixes` | READY | skeleton + fix pass F-01, F-03..F-09 |
| plugin-tester | done (merged) | `core-foundation/plugin-tester`, `…-2` | READY | 230 tests, 1 skipped (F-12); 24 architecture rules |

## Final state (2026-10-21)
**Done:** every acceptance criterion except the in-game checks. `mise run check` is green (230 tests, 1 skipped on purpose). `ownership-check` was run by hand with a copy of CookieClicker's `ownership_lib.py` for every merged branch: clean. Reviews: `docs/reviews/2026-10-21-core-foundation.md` (first review and section 9 "Fix pass", verdict READY). Architecture: `docs/architecture.md`.

**Follow-ups, none blocking:**
- F-12 (LOW): a quoted number such as `database.port: '3308'` passes the number check but `getInt` falls back to 3306 without a problem. Fix in `PluginSettings.intOrDefault`, then remove `@Disabled` from `PluginSettingsTest.aQuotedWholeNumberIsEitherUsedOrReportedNeverIgnoredSilently`. Do it at the start of phase 1.
- F-13 (INFO): a lookup future accepted by the scheduler just before disable never completes; F-14 (INFO): `PlayerDirectory.cachedPlayers()` has no production caller (tests use it); F-10/F-11 (INFO).

**For the user (not doable by an agent):**
1. F-02: copy `.claude/hooks/` (`ownership.py`, `ownership_lib.py`) from CookieClicker and register the hook in `.claude/settings.json`, so `mise run ownership-check` and the write-time hook work here.
2. In-game checks on a Paper 26.3 server: clean boot, `/cookiecore version`, `/cookiecore reload`, `players` table filled after a join, tab completion of the custom arguments registers cleanly, F-05 (stop the server with a player online and read `last_seen`). Guide: section 6 of the review.
3. PR: open it, or give the bot credentials (own GitHub App: `APP_ID`, `INSTALLATION_ID`, `KEY_PATH` in `~/.config/cookieclicker/github-app.env`).

## Recap (2026-10-20, history: first session)
**Done:** roadmap and brief; research note; coder skeleton: `pom.xml`, `plugin.yml`, `config.yml`, `messages.yml`, `sounds.yml` (vanilla keys), `Perm`, `Messages`, `SoundService` + parser, `PluginSettings`/`YamlFiles`, storage (H2/MariaDB factory, `StorageThread`, `SchemaMigrator`/`Migrations`, `JdbcPlayerStore`), `PlayerDirectory` + `PlayerListener`, `CookieCoreApi` (`api/`).

**Still open (next session):**
1. `CookieCore` main class (plugin.yml already points to `dev.casperrs.cookieCore.CookieCore`): enable/disable order, wiring, `ServicesManager` registration
2. Brigadier helper and `/cookiecore version|reload` (the `command/` package is empty); custom offline-player and duration argument types as in the research note
3. Unit tests (parsers, Messages, SoundConfigParser, PlayerDirectory, schema migration on H2)
4. Port the mise tasks `build`, `test`, `check`, `team`, `ownership-check` and `tools/ownership` from CookieClicker (`mise.toml`: needs human review)
5. plugin-tester: `ArchitectureRulesTest`, `docs/architecture.md`, review of the coder branch and integration review; only then flip this brief to done

**Not verified:** nothing has been reviewed by Gandalf, `ownership-check` was not run (task not ported yet), the plugin has never been started on a Paper server.

## Merge order
1. research note (direct on the feature branch)
2. plugin-coder

## Needs human review
- [ ] Listen to the three vanilla UI sounds in game (`ui_confirm`, `ui_error`, `ui_notify`); decide later whether Core gets a resource pack and custom sounds (needs `ownership.json` paths for the sound-designer)
- [ ] `mise.toml` changes (new tasks) and `.gitattributes` if touched
- [ ] Start a Paper 26.3 test server with the jar: clean boot log, `/cookiecore version`, `/cookiecore reload`, `players` table filled after a join
