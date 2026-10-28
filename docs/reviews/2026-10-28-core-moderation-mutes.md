# Review: core-moderation-mutes (phase 4b, slice 1)

**Reviewer:** Gandalf (plugin-tester) · **Date:** 2026-10-28
**Branch reviewed:** `core-moderation-mutes/plugin-coder` at `468182e` (DONE, coder stopped)
**Tester branch:** `core-moderation-mutes/plugin-tester` (tests, this review, architecture note)
**Target:** Paper 26.3 (`paper-api 26.3.build.205-stable`), Java 25.

**VERDICT: READY.** No HIGH or MEDIUM findings. Two LOW follow-ups for the PR list, no fix round.

## 1. Target version
Paper 26.3.build.205-stable. `AsyncChatEvent` 7-argument constructor checked with `javap` against the API jar (used in the test, no server needed). `PlayerKickEvent` and the ban paths are unchanged. No new API beyond what the ban slice already uses. Build: `mise run test` BUILD SUCCESS on the tester branch.

## 2. Test results
`mise run test`: 213 run, 0 failures, 1 skipped (the existing skip in `ScoreboardLayoutTest`). Before this round: 205. New: 8.
- `MuteListTest` (2): a tempmute ends at its expiry and `remove` clears it; at load, the longer lasting of two rows wins, permanent included.
- `MuteListenerTest` (3): a muted player's chat is cancelled and shows the reason, others pass; chat is not blocked before the load; an expired tempmute passes.
- `ModerationServiceTest` (+2): a failed mute write changes nothing (mute list, chat) and tells the sender; a mute/unmute round trip updates the list and the table, and self, already muted, not muted are refused without a change.
- `JdbcPlayerStoreTest` (+1): mutes load (expired rows excluded), undo deactivates the mutes of one player and leaves the ban and other players alone.
Ban regressions: `BanListTest`, `ModerationServiceTest` (existing 6), `ModerationCommandTest`, `ModerationListenerTest`, `ModerationModuleTest` and `JdbcPlayerStoreTest` (existing 16) all pass unchanged. The `JdbcPlayerStore` refactor to `loadActive(type)` / `deactivate(type)` keeps the SQL the same for `BAN` (`type` comes from the enum name, so no injection).

## 3. Findings

**Thread safety (passed):** `MuteList` is a `ConcurrentHashMap` with a volatile `loaded` flag; `MuteRecord` is an immutable record. The chat listener only reads (one lookup and one clock read per chat message) and sends a message on the chat thread, which Paper allows. Writes come from the main thread (commands) and the IO thread (start-up load, which runs before any command can reach the list because `storageUp` needs the load to have set `usable`).

**LOW-1 (`ModerationModule.java:~93-100`, follow-up):** `usable = true` is set before `loadMutes()` runs. A `/mute` in that short window passes the `storageUp` check and the main-thread "already muted" check sees an empty `MuteList`, so the row is inserted twice (the insert runs after the load on the same storage thread). The restart load merges the two rows (longer wins), so nothing is lost for good. Fix: have the mute commands' storage check also require `mutes.loaded()`, or set `usable` after `loadMutes()`. Not blocking: a window of milliseconds at start-up, no security effect.

**LOW-2 (`ModerationService.java:67-72`, follow-up):** the 10-argument constructor ("without mute support") is no longer used by any code or test (`ModerationModule` and `ModerationFixture` use the 11-argument one). Remove it.

**LOW-3 (`MuteList.java`, follow-up):** an expired tempmute stays in the map until the player is muted or unmuted again (`active` ignores it). Bounded by the players muted during one run, so no leak in practice. `activeNames` scans the whole map per tab completion (PERF-LOW: only for `/unmute` and bounded by the mutes in the map).

**LOW-4 (`storage/MuteRecord.java`, `JdbcPlayerStore.java`, follow-up):** `MuteRecord.of(BanRecord)` makes the storage mute row depend on the ban record type. A shared row record would be cleaner. Also `PunishmentStore.loadActiveMutes` and `deactivateMutes` are `default` methods returning empty/0; once all fakes implement them, make them abstract so a new store cannot silently skip mutes.

Checked and passed: failed write leaves everything unchanged (`finish` does not run the `onSuccess` path); `busy` covers mutes too; chat listener at `LOWEST` so other plugins do not see a cancelled message's formatting; permanent and tempmute messages use `Placeholder.unparsed` for the reason (player input never parsed); the mute permission nodes match `Perm` and `paper-plugin.yml` (`cookiecore.staff.mute`, `cookiecore.staff.unmute`, `*` wildcard updated); `MuteListener` is registered in `CookieCore.java:101`.

## 4. Performance
- Chat (per message, per chat thread): one `ConcurrentHashMap.get`, one `System.currentTimeMillis`, nothing allocated for an unmuted player. Estimated well under 1 µs per message.
- Start-up load: one query for the active mutes on the IO thread, same shape as the ban query (indexed by `target_uuid, active`). No per-tick or per-second work.
- No PERF-HIGH or PERF-MEDIUM.

## 5. Architecture
- `moderation -> {…, sound, storage, text, log, api, Perm}` still holds; `MuteListener` uses `log.WarnOnce` and `text.Messages`, both already allowed.
- The mute path keeps the ban rule: database write first, then the in-memory list, then confirm and alert.
- `docs/architecture.md` updated (moderation row: `MuteList`, `MuteListener`, the load order and the write rule).

## 6. In-game checks for the PR
1. Start the server with the database enabled. Log shows "Loaded N active ban(s)" then "Loaded N active mute(s)".
2. `/mute <you-alt> spam` from a staff account on a second account: the target gets the "muted" chat refusal with the reason on every line; other players see the public mute alert; the staff hears the notify sound.
3. The muted account types in chat: the message is not shown to anyone and the refusal shows the reason. Permanent mutes say so; tempmutes show the time left.
4. `/tempmute <alt> 1m spam`, wait for the end: the next chat goes through without a restart.
5. `/unmute <alt>`: chat works at once; the alert and the unmute sound are as for bans.
6. Restart the server: a permanent mute is still in force; an expired tempmute is not.
7. `/mute -s <alt>`: only staff with `cookiecore.staff.notify` see the alert.
8. Storage off (wrong database password): `/mute` says the storage is down; chat is not blocked (known, accepted).
9. Spark (`/spark profiler start --timeout 60` while chatting with several players): `MuteListener.onChat` should be a negligible share of the chat thread.

## 7. Verdict
READY. LOW-1 to LOW-4 go to the PR follow-up list.
