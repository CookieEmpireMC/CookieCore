# core-moderation-mutes

**Status:** in progress (slice 1 of phase 4b, timeboxed to 30 minutes by the user on 2026-10-28)  <!-- planned | in progress | review | done -->
**Created:** 2026-10-28 · **Depends on:** `core-moderation-bans` (merged, PR #9).

## Goal
Staff can mute a player so their chat is blocked, for a set time or for good, and lift it again. The mute survives a restart and is in the same audit log as bans. This is the first slice of roadmap phase 4b; the rest follows in later slices.

## Player story
Staff types `/mute Steve spamming`. Steve tries to chat and gets a clear "you are muted" line (with the reason and the time left); nobody sees his message. `/tempmute Steve 2h spam` does the same for two hours. `/unmute Steve` lifts it. After a server restart the mute is still there.

## Acceptance criteria
- [ ] `/mute <player> [-s] [reason]`, `/tempmute <player> <duration> [-s] [reason]`, `/unmute <player> [-s] [reason]`. Same argument style, `-s` flag, duration parser (`7d12h`), offline-name resolution and staff alert as the bans commands; reuse `SilentReasonTail`, `Actor`, `PlayerRef` resolution and the alert code, do not copy them.
- [ ] Permissions `cookiecore.staff.mute` and `cookiecore.staff.unmute`, default op, added to `Perm`, `paper-plugin.yml` and under the `cookiecore.staff.*` parent. `/unmute` completes only currently muted names.
- [ ] A muted player's chat is cancelled (Paper `AsyncChatEvent`, the muted player's message is not shown to anyone) and they get the `moderation.muted_chat` message with reason and remaining time (permanent: "permanently"). Check is one in-memory lookup, no database call and no thread hop on the chat path.
- [ ] Mutes are rows in `<prefix>punishments` with `type = MUTE`, active on insert; expiry is `expires_at`, an expired tempmute is simply ignored (no cleanup task), like tempbans. `/unmute` deactivates the rows like `/unban`.
- [ ] Order of effects like 4a: DB write on the IO thread first, then the in-memory `MuteList` update, then confirmation and staff alert. A failed write changes nothing and tells the sender.
- [ ] Active mutes are loaded at start-up by the same module load that loads bans. While the mutes are not loaded, `/mute` and `/unmute` still work; chat of a player whose mute state is unknown is **not** blocked, because a chat mute is not a security boundary; log it once.
- [ ] Muting an already muted player is rejected with a clear message (use `/unmute` first); `/unmute` of a non-muted player is rejected. Never-seen names cannot be muted (same rule as bans).
- [ ] All player text in `messages.yml` under `moderation.*`; sounds only through `SoundService` (`ui_confirm`/`ui_error` as in 4a). No new config keys.
- [ ] `JdbcPlayerStore` must not keep growing: put the mute SQL in a new class (for example `JdbcPunishmentStore`) if that is cleaner than extending the 307-line class; if you move the existing punishment methods, behaviour and tests stay identical.
- [ ] Lean tests (service order/failure, MuteList expiry, chat listener cancel/not cancel, store round trip on H2); `mise run check` green; architecture rules pass.

## Out of scope (later slices of 4b)
`/warn`, `/history <player>`, the expiry notice when a tempmute ends, blocking vanilla `/msg` `/tell` `/w` (phase 5 brings our own `/msg`; note the gap in the PR), IP mutes, a unified `/punish` view.

## Assets needed
none (vanilla sounds, D7).

## Balance impact
none

## Research needed
none: `AsyncChatEvent` cancel is standard Paper API; the rest reuses the 4a research in `docs/research/core-moderation-bans-apis.md`.

## Open questions
None. Defaults decided by the orchestrator: chat only (no commands) is blocked; fail-open for chat when mute state is unknown.

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| command | `/mute`, `/tempmute`, `/unmute` | plugin-coder | Brigadier, in the `moderation` package |
| permission | `cookiecore.staff.mute`, `cookiecore.staff.unmute` | plugin-coder | default op, `Perm` constants and `paper-plugin.yml` |
| storage | `PunishmentType.MUTE` | plugin-coder | no new migration needed (the `type` column is text); say so if that is wrong |
| class | `moderation.MuteList`, `moderation.MuteCommand`, `TempmuteCommand`, `UnmuteCommand`, `MuteListener` | plugin-coder | |
| message keys | `moderation.muted_chat`, `moderation.mute_*`, `moderation.unmute_*`, `moderation.already_muted`, `moderation.not_muted` | plugin-coder | `messages.yml` |
| sound event | `ui_confirm`, `ui_error` | plugin-coder | existing |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| plugin-coder | everything above except tests | brief | `core-moderation-mutes/plugin-coder` |
| plugin-tester | tests, branch review, `docs/architecture.md` | coder DONE | `core-moderation-mutes/plugin-tester` |

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| plugin-coder | launched 2026-10-28 08:35 | `core-moderation-mutes/plugin-coder` | | |
| plugin-tester | waits for coder | `core-moderation-mutes/plugin-tester` | | |

## Needs human review
- [ ] In-game: `/mute`, `/tempmute 1m`, chat while muted, `/unmute`, restart with an active mute, offline player.
- [ ] Brief was drafted and built in one go on the user's instruction ("bouw een deel van 4b, 30 minuten"), without the usual separate approval step.
