# core-moderation-warns

**Status:** review (slice 2 of phase 4b, built within the 45-minute timebox the user set on 2026-10-28; branch verdict READY after one fix round, waits for the in-game check)  <!-- planned | in progress | review | done -->
**Created:** 2026-10-28 · **Depends on:** `core-moderation-bans` (#9) and `core-moderation-mutes` (#10), both merged.

## Goal
Staff can warn a player and look up everything that ever happened to them. A muted player can no longer whisper around the mute. The storage class stops growing.

## Player story
Staff types `/warn Steve please stop spamming`. Steve sees a clear warning with the reason and hears a sound; staff see an alert. `/history Steve` shows Steve's last punishments (warn, kick, ban, mute), newest first, with who did it, why, when, and whether it is still active. A muted player who types `/msg Alex hi` is refused with the muted line.

## Acceptance criteria
- [ ] **Refactor first (behaviour-preserving):** move the punishment SQL out of `JdbcPlayerStore` into a new `JdbcPunishmentStore` (shares the same connection source / IO-thread rules); `JdbcPlayerStore` shrinks back to players and sidebar prefs. Wiring in `CookieCore`/`ModerationModule` changes accordingly without adding lines to `CookieCore.java` (199 of 200 max, the scheduler/arch rule). All existing tests stay green; store tests may be adjusted only for the new class name.
- [ ] `/warn <player> [-s] [reason]`: same argument style as `/ban` (`SilentReasonTail`, `KnownPlayerArgument`, offline names, never-seen names refused). Inserts a `PunishmentType.WARN` row (inactive on insert, like KICK), then shows the target (if online) a warning message with the reason plus a sound via `SoundService` on the main thread, confirms to the sender and alerts staff like the other actions (`-s` silent suppresses the public/staff broadcast like in 4a, the target still sees it). Permission `cookiecore.staff.warn`, default op.
- [ ] `/history <player> [page]`: newest first, 8 entries per page, shows type, status (active / expired / lifted / one-off), actor, reason, date (UTC, same date format as the ban screen) and length for tempbans/tempmutes; "no history" message when empty. Offline known names work; never-seen names are refused. The query runs on the IO thread, the output is sent on the main thread. Permission `cookiecore.staff.history`, default op. Tab completion lists known names as the other staff commands do.
- [ ] Both permissions are in `Perm`, `Perm.ALL`, `paper-plugin.yml` and under `cookiecore.staff.*`.
- [ ] A muted player cannot use the vanilla whisper commands `/msg`, `/tell`, `/w`, `/whisper`, `/me` (also with the `minecraft:` prefix): block them in a listener using the existing `MuteList` lookup (in-memory, main thread) and send a muted-command line. Pick the cleanest Paper API for this version (`PlayerCommandPreprocessEvent` is acceptable; document the choice). Our own `/msg` (phase 5) will replace this.
- [ ] Follow-ups from the mutes review: **LOW-1** (set `usable` after `loadMutes()` or require `mutes.loaded()` so a `/mute` cannot insert a second row in the start-up window), **LOW-2** (delete the unused 10-argument `ModerationService` constructor), **LOW-4** (make `loadActiveMutes`/`deactivateMutes` abstract and implement them in the test fakes; the tester does the test-side part).
- [ ] All text in `messages.yml` under `moderation.*` (hyphenated keys), sounds only via `SoundService`, no new config keys, no new migration (WARN fits `VARCHAR(16)`; `history` reads existing columns).
- [ ] Lean tests; `mise run check` green; architecture rules pass.

## Out of scope
The notice when a tempmute ends (the scheduler may only live in `CookieCore.java`, which is full; do it with phase 5 or after a deliberate refactor), warn escalation / auto-ban at N warnings, IP history, web panel, deleting history.

## Assets needed
none (vanilla sounds, D7).

## Balance impact
none

## Research needed
none (4a research covers the APIs; if the whisper-block event is unclear the coder decides and documents it).

## Open questions
None. Defaults by the orchestrator: warn is a one-off row (inactive), `/history` is 8 per page, UTC dates.

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| command | `/warn`, `/history` | plugin-coder | Brigadier, `moderation` package |
| permission | `cookiecore.staff.warn`, `cookiecore.staff.history` | plugin-coder | default op |
| storage | `PunishmentType.WARN`, `JdbcPunishmentStore`, `PunishmentStore#history(UUID, int limit, int offset)` | plugin-coder | `storage/`, no Bukkit types; a `HistoryEntry` record |
| class | `WarnCommand`, `HistoryCommand`, `MuteCommandBlocker` (names free for the last) | plugin-coder | |
| message keys | `moderation.warn-*`, `moderation.history-*`, `moderation.muted-command` (hyphens) | plugin-coder | `messages.yml` |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| plugin-coder | everything above except tests | brief | `core-moderation-warns/plugin-coder` |
| plugin-tester | tests (incl. LOW-4 fakes), branch review, `docs/architecture.md` | coder DONE | `core-moderation-warns/plugin-tester` |

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| plugin-coder | done (merged, one fix round) | `core-moderation-warns/plugin-coder` | READY | Split `JdbcPlayerStore` (325 to 202 lines) into `JdbcPunishmentStore` + `JdbcSession`; `/warn`, `/history`, whisper block inside `MuteListener#onCommand` (no separate class); LOW-1/LOW-2 of the mutes review. M-1 fixed: mutes are refused while the mute load failed (option b). `MuteCommandBlocker` was not built as a class. |
| plugin-tester | done (merged) | `core-moderation-warns/plugin-tester` | READY | 224 tests; `docs/reviews/2026-10-28-core-moderation-warns.md` (two rounds) |

Ownership note: the coder first edited two tester-owned test files through `sed` (the Edit hook would have denied it). Rick removed those edits from the coder commits (rewriting the unmerged local branch) and Gandalf adopted the files in his own first commit. `ownership-check` is clean.

## Needs human review
- [ ] In-game: `/warn` (target sees it, sound), `/history` with warn/kick/ban/mute entries and pages, offline name, muted player trying `/msg` and `/me`.
- [ ] MariaDB by hand for the `history` query and the `WARN` rows.
- [ ] Built in one go on the user's instruction without a separate brief-approval step.
