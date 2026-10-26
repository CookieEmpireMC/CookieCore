# core-tidy

**Status:** review (integration verdict READY; waits for the first Paper start)  <!-- planned | in progress | review | done -->
**Created:** 2026-10-26 · **Depends on:** `core-foundation` (merged into `main` as PR #4)

## Goal
Bring the phase 0 code in line with the shared CookieMemory rules of 2026-10-23 (code standards, minimal config, `paper-plugin.yml` with a `PluginLoader`, lean tests) and fix the one open bug (F-12), before phases 2 and 3 build on it. No new behaviour.

## Player story
Nothing changes for players or staff. The next developer (or agent) finds the same layout and rules as CookieClicker, a smaller and faster test suite, and a descriptor that follows the Paper 26.x recommendation.

## Acceptance criteria
- [ ] `src/main/resources/paper-plugin.yml` replaces `plugin.yml` (same name, `api-version: '26.3'`, `load: POSTWORLD`, description). LuckPerms moves from `softdepend` to `dependencies.server.LuckPerms` with `load: BEFORE`, `required: false`, `join-classpath: true`. Permissions stay declared in the descriptor, with a test that the strings match `Perm`.
- [ ] The JDBC drivers load through a `PluginLoader` (`CookieCoreLoader`) with `MavenLibraryResolver` on `MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR`; versions come from a pom-filtered `cookiecore-libraries.properties`, and a test ties them to the pom (`h2.version`, `mariadb.version`). Pattern: CookieClicker `origin/main`: `src/main/java/dev/casperrs/cookieClicker/CookieClickerLoader.java`, `src/main/resources/cookieclicker-libraries.properties`, `docs/research/paper-plugin.md` (read with `git -C C:\dev\Cookie-Clicker show origin/main:<path>`). The jar stays free of Paper/Adventure/driver classes (provided scope).
- [ ] No `@param` / `@return` tags in `src/main`: units, ranges and special values go in one doc line or in the parameter name (7 places today: `PlayerDirectory`, `SoundDef`, `DatabaseSettings`).
- [ ] `config.yml` holds only what an admin sets: `server.name`, `database.type|file|host|port|name|user|password|table-prefix`. `database.shutdown-timeout-seconds` becomes a constant with the same value (10). Every kept key has a built-in default, so an old or partial config loads cleanly; the shipped `config.yml` and `PluginSettings` agree.
- [ ] F-12 fixed in `PluginSettings.intOrDefault`: a quoted whole number such as `port: '3308'` is either used or reported as a problem, never ignored silently; `@Disabled` removed from `PluginSettingsTest.aQuotedWholeNumberIsEitherUsedOrReportedNeverIgnoredSilently`.
- [ ] Quick standards scan of `src/main`: no wildcard imports, no package cycles, no class over ~500 lines, no comments that narrate history or brief names ("since", "was", "phase 0", "F-xx"), `@Nullable`/`Optional` instead of silent `null`. Fix only what the scan finds, no refactors for their own sake.
- [ ] Test suite trimmed to the important parts (storage and migrations, config loading and validation, parsers with edge cases, `PlayerDirectory` behaviour, command arguments, descriptor/pom ties, `ArchitectureRulesTest`). No coverage target and no one-test-per-class. Record the count before and after in the review.
- [ ] `docs/architecture.md` gets a **Standards** section (the short rules from the `bakery-context` skill: comments, structure, types, config, performance, descriptor) and the changed enable path (loader, `paper-plugin.yml`).
- [ ] `mise run check` is green; `ownership-check` is clean for every merged branch.

## Out of scope
- Any feature behaviour (tab, scoreboard, gamemode, moderation).
- The in-game start on a Paper server (Luuk's to-do; Paper plugins are still experimental, so the first start after this change must be re-tested).
- F-13, F-14, F-10/F-11 (INFO) unless the standards scan touches them anyway.

## Assets needed
| Kind | What | Owner |
|---|---|---|
| model / texture / glyph | none | model-designer |
| sound | none | sound-designer |
| effect | none | vfx-designer |

## Balance impact
none

## Research needed
none: the pattern exists in CookieClicker (see the acceptance criteria).

## Open questions
None.

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| class | `dev.casperrs.cookieCore.CookieCoreLoader` | plugin-coder | `PluginLoader`, referenced by `loader:` in `paper-plugin.yml` |
| resource | `cookiecore-libraries.properties` | plugin-coder | keys `h2`, `mariadb`, pom-filtered |
| descriptor | `paper-plugin.yml` | plugin-coder | replaces `plugin.yml` |
| config key | unchanged except `database.shutdown-timeout-seconds` (removed, now a constant) | plugin-coder | |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| plugin-coder | descriptor, loader, `@param` tags, config trim, F-12, standards scan in `src/main`. Reports tests it knows are red (it may not write `src/test/**`) | contract | `core-tidy/plugin-coder` |
| plugin-tester | after the coder is DONE: fix red tests, enable the F-12 test, trim the suite, add descriptor/pom tests, write the Standards section, review the coder branch | coder DONE | `core-tidy/plugin-tester` |

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| plugin-coder | done (merged) | `core-tidy/plugin-coder` | n/a | ownership-check clean; src/main compiles, tests not run (mise blocked) |
| plugin-tester | done (merged) | `core-tidy/plugin-tester` | READY | 137 tests (was 230), 0 skipped; `docs/reviews/2026-10-26-core-tidy.md`; LOW follow-ups L-1 to L-5 |

## Merge order
1. plugin-coder
2. plugin-tester

## Needs human review
- [ ] `mise.toml` / `.gitattributes` only if touched (none expected).
- [ ] First start on a Paper 26.3 server after this change: clean boot, drivers load, `/cookiecore version`.
