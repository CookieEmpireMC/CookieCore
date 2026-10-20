# Project guide for Claude: CookieCore

**CookieCore** is the Cookie Empire core plugin for Paper (GDD §9.1: "core plugin"). It is the server's **base layer**: tab list, scoreboard, gamemode commands and staff moderation (kick, ban, mute), kept small, tight and polished. Phases and decisions are in `docs/briefs/ROADMAP.md`; one feature per phase, the user merges each PR. Gameplay lives in [CookieClicker](https://github.com/CookieEmpireMC/CookieClicker); Core never depends on it.

## Shared team and knowledge
The team comes from the **`bakery-team`** plugin ([CookieEmpireMC/CookieMemory](https://github.com/CookieEmpireMC/CookieMemory)), enabled in `.claude/settings.json`:
- agents `bakery-team:<agent>` (coder, tester, model-, sound-, vfx- and balance-designer, researcher)
- `/bakery-team:feature <name> <description>` runs a feature
- every agent preloads the `bakery-context` skill (team, git identities, ownership model, workflow, ground rules) and the `game-design` skill (the full game design document)

Shared knowledge changes only through a PR on CookieMemory. Everything specific to this repo stays here.

## This repo's own memory
| Path | What |
|---|---|
| `CLAUDE.md` | this file: what CookieCore is, its commands and docs |
| `.claude/ownership.json` | who may edit what **here** (human-controlled) |
| `.claude/agent-memory/bakery-team-<agent>/` | each agent's memory for this repo |
| `docs/briefs/` | feature briefs (from `TEMPLATE.md`) |
| `docs/reviews/`, `docs/architecture.md` | tester's reviews and architecture notes (once there is code) |

## Commands
- `mise run check`: full check (passes on an empty project until there is a `pom.xml`). Never run plain `mvn`; use `mise exec -- mvn …`.
- Pins: Java 25, Maven 3.9, Python 3.12 (`mise.toml`), same as CookieClicker (Paper 26.x).
- PRs are opened as `cookie-bakery[bot]` with CookieClicker's `mise run pr-open --repo CookieEmpireMC/CookieCore …`; only the user merges into `main`.
