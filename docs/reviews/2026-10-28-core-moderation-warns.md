# Review: core-moderation-warns (slice 2 of 4b)

Reviewer: Gandalf (plugin-tester). Branch `core-moderation-warns/plugin-tester` (coder tip `e0d2def`, tester commits `127d9a8`, `2a573b9`, `f4e9f35`). Brief: `docs/briefs/core-moderation-warns.md`.

**VERDICT: READY** (round 2, after the round 1 NOT READY for M-1; see sections 3 and 8).

## 1. Target version
Paper 26.3 (`26.3.build.205-stable`), Java 25, `api-version: '26.3'`, `mise.toml` pins temurin-25. No version mismatch found. No MiniMessage or Adventure 4 API in the diff. `PlayerCommandPreprocessEvent` (whisper block) is not deprecated in 26.3 (no `[removal]` or deprecation warning from it in `mise run check`; the only warning in the build is the existing `FlyTracker.isOnGround()`, tracked in the gamemode review).

## 2. Test results
- `mise run check` at `f4e9f35`: BUILD SUCCESS, 216 tests, 0 failures, 0 errors, 1 skipped (same skip as before this slice).
- Restored the coder's test edits first (`127d9a8`): the split compiles and the existing storage tests run green.
- `JdbcPunishmentStoreTest` (new, 5 tests): the four punishment tests moved out of `JdbcPlayerStoreTest` (round trip, unban per player, corrupt row, mutes separate from bans) plus `historyIsNewestFirstPagesWithLimitAndOffsetAndMixesEveryType` (ids as tie-break, LIMIT/OFFSET paging on H2, bans, mutes, warns and kicks in one list, other players excluded, WARN and KICK stored inactive).
- `MuteListenerTest` (+2): whisper commands (msg, tell, w, whisper, me, `minecraft:` prefix, uppercase) cancelled for a muted player with the message; `/kick` passes; `isWhisper` matches the label only (`/msgfoo`, `/say /msg`, text without slash pass).
- **Not covered (gaps, listed for the coder's follow-up, no test written in this slice):** `ModerationService.warn` order and failure paths (DB first, failed write changes nothing, self refused, `-s` suppresses the broadcast but the target still sees the warning); `PunishmentHistory` status and type mapping (private static, reached only through `show`, needs `Messages`/`SoundService` fakes); `ModerationModule` start-up ordering (LOW-1, the `usable` flag after mutes). The time box for this round did not allow them. These are gaps, not failures; add them in the fix round if the coder wants the warn path covered, or in the follow-up.

## 3. Bugs found

### M-1 (MEDIUM): a failed mute load leaves `/mute` working but the new mute unenforced
- `ModerationModule.loadNow()` (around line 97-103): `loadMutes()` catches its own failure (`loadMutes`, around line 115-124, logs SEVERE and does not call `mutes.markLoaded()`). `usable = true` is then set anyway, so `ModerationService` accepts `/mute` (its `storageUp` is `usable && storageAvailable`).
- `afterMute` puts the new row in `MuteList`, but `MuteListener.onChat` (line 69) and `onCommand` (line 44) return early while `!mutes.loaded()`. So a player muted after a failed mute load keeps chatting and whispering, while the staff see "muted" and the row is in the table. The message to the staff is wrong for the whole session (until restart).
- Why it matters: the ordering fix (LOW-1) only covers the success path. The bans path is fail-closed (a failed load retries on the next login). The mutes path is fail-open for unknown state but must not claim a success it does not enforce.
- Suggested fix (coder, pick one): (a) on a mute-load failure keep `usable` false and rethrow into `loadNow`'s existing retry, so the next login reloads bans and mutes together (the cleanest, matches fail-closed bans); or (b) keep fail-open but refuse `/mute` while `!mutes.loaded()` with the storage-down message. Option (b) contradicts the brief line "While the mutes are not loaded, `/mute` and `/unmute` still work": the brief and the mutes review disagree on this point, so the orchestrator should decide before the fix round.
- Regression test once fixed: a `PunishmentStore` stub whose `loadActiveMutes` throws, then `/mute` must not report success while chat is unblocked (in `ModerationModuleTest` or the fixture).

## 4. Review of the coder diff (`main...HEAD`), by item
- **Storage split (correct):** `JdbcPunishmentStore` takes `JdbcPlayerStore.session()`, so one connection, one IO thread, and `JdbcPlayerStore.close()` closes both (the punishment store has no close of its own; `CookieCore` closes the player store through `directory.shutdown()`). `JdbcSession` is single-threaded by contract and used only on the IO thread (`ModerationService`, `PunishmentHistory` and the module load all submit through `io`). No regression in the player SQL: `JdbcPlayerStoreTest` (13 tests) and `PlayerDirectoryTest` pass unchanged.
- **SQL (MariaDB and H2):** `ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?` is valid on both; `setInt` for limit and offset is correct. `type` is inlined from the enum (`name()`, a constant, no injection). `expires_at` NULL read with `getLong` then `wasNull()` (correct order). `type VARCHAR(16)` takes `WARN` without a migration. The `(target_uuid, active)` index does not cover the `ORDER BY`; per-player rows are few, so this is a PERF-LOW (see 5).
- **Warn (`ModerationService.warn`):** the write runs on IO first (`background`), then the main-thread callback confirms, alerts and logs; `busy` is released in `finish` and on rejection; self is refused before the write; `-s` only changes `alert(...)`; the target gets the warning in `afterWarn` regardless of `silent` (as the brief asks). Reason goes through `reasonOf` (required, same as the other actions).
- **History (`PunishmentHistory`):** read on IO, render on main via `handBack`; page size plus one for the next-page test; `storageUp` is checked before queueing; a failed read says "failed" and does not hang. Status and type mapping: `BAN`/`MUTE` with `expiresAt` give tempban/tempmute; `active` false gives lifted; an expired active row gives expired; `KICK`/`WARN` give one-off. The mapping reads correctly from the code (not yet unit tested, see section 2).
- **Whisper block:** `PlayerCommandPreprocessEvent` at LOWEST, main thread, memory only (one `ConcurrentHashMap` lookup); `isWhisper` lowercases the label, strips `/` and `minecraft:`, and matches exact labels (`/msgfoo` passes). Uses the `loaded()` gate like chat (fail-open by design, brief).
- **LOW-1 (ordering):** `usable = true` now comes after `loadMutes()` on the success path; verified in code. The failure path is M-1.
- **Thread safety:** `MuteList` (volatile `loaded`, concurrent map) is read from the chat thread and written on the main thread (unchanged pattern). `JdbcPunishmentStore` holds no state beyond `session`, `p`, `log`.
- **Architecture rules:** `storage/` still imports no Bukkit or other Core package; `CookieCore.java` uses the fully qualified `JdbcPunishmentStore` (accepted by the orchestrator, 199 of 200 lines); `Perm` lists the two new nodes and `paper-plugin.yml` declares them (`PluginDescriptorTest` passes). No `playSound` or `spawnParticle` outside their owners.
- **Known and accepted (not reported):** tempmute expiry notice (out of scope).

## 5. Performance
- **Chat and whisper hot path:** `MuteListener.onChat` and `onCommand` do one map lookup per message or command and a short `substring`/`toLowerCase` only for commands whose text starts with the first word. Per-event cost is constant, no database, no allocation beyond the label string. PERF-LOW-1 (optional): compare the first word with `regionMatches` to avoid the lowercase copy per command. Not worth a change.
- **`/history`:** one indexed query per call, `LIMIT 9`. Index `(target_uuid, active)` does not cover `ORDER BY created_at DESC, id DESC`, so MariaDB sorts the player's rows. A player has few rows, so cost is small. PERF-LOW-2: a `(target_uuid, created_at)` index in a future migration if a player accumulates thousands of rows.
- **Start-up:** `loadActiveMutes` runs once on the IO thread after the bans; no main-thread blocking.
- No PERF-HIGH or PERF-MEDIUM found.

## 6. Architecture and follow-ups
- **LOW-4 (coder, follow-up, not done here):** make `loadActiveMutes`, `deactivateMutes` and `history` abstract in `PunishmentStore` (the defaults return empty and 0, which would silently hide a missing implementation). This is coder-owned production code. Consequence for the tester: the `ModerationFixture` store and the anonymous `PunishmentStore` stub must implement all three when it changes (tester part, do it with the change).
- **L-1 (coder, LOW):** `history` page overflows: `(page - 1) * PAGE_SIZE` is an `int`, so a page above about 268 million gives a negative offset, the SQL fails, and the sender sees "could not read". Fix: clamp the page or compute the offset as `long` and reject over a cap. Not in the fix round (LOW).
- **L-2 (coder, LOW, follow-up):** the whisper list covers the vanilla labels only; another plugin's whisper alias (e.g. a chat plugin's `/t`) still gets through. Document it in the PR or add the labels to config if the server needs them.
- **L-3 (coder, LOW, follow-up):** `ModerationService.warn` and `PunishmentHistory` have no unit tests yet (section 2).
- Architecture: `docs/architecture.md` updated in the review commit (storage split, `WarnCommand`, `HistoryCommand`, `PunishmentHistory`, the whisper block, history threading).

## 7. In-game guide (for the PR)
Run as an op with the staff nodes (`cookiecore.staff.warn`, `cookiecore.staff.history`) and a second account as the target.
1. `/warn <target> spam`: the sender gets "warned", the target (online) gets the warning and a sound, the staff with `cookiecore.staff.notify` see the alert.
2. `/warn <target> -s spam`: the target still gets the warning, no public or staff alert line.
3. `/warn <yourself> spam`: refused with the self message.
4. `/warn <target>` with no reason: usage message, nothing written.
5. `/history <target>`: newest first, one line per row with status words: "warning" as one-off, mute and ban active, lifted after `/unmute` or `/unban`, expired for a tempmute past its end, tempban/tempmute with its length.
6. Make 9+ rows and `/history <target> 2`: the second page shows the rest; a "next page" hint appears on page 1 only when there is more.
7. `/mute <target> 5m spam`, then as the target: `/msg <someone> hi`, `/tell`, `/w`, `/me waves`, and `/minecraft:msg` are refused with "You are muted, so you cannot use that command."; plain chat shows the usual mute message. After `/unmute` the commands work again.
8. Restart with a mute active: the mute still applies (start-up load). With the database down: `/history` and `/warn` say storage is down.
- **Good:** the warn and history commands answer in one or two ticks; no lag while the database answers (it runs on the IO thread); chat and whisper checks cost nothing measurable.
- **spark:** `/spark profiler start --timeout 60` while running `/history` in a loop; the plugin's share of the tick should be negligible (no `CookieCore-Storage` on the main thread).

## 8. Round 2 (fix commit `28ba96c`, tests `88f0190`)
Scope: the diff of `28ba96c` only (`src/main`: `ModerationModule.loadMutes`, `MuteList`, `ModerationService.mute`/`unmute`, `PunishmentHistory.read`, `messages.yml`).
- **M-1 fixed, option (b) (decided by the orchestrator):** `MuteList.markLoadFailed()` is set in the `loadMutes` catch; `mute`, `tempmute` (through `service.mute`) and `unmute` refuse with `storage-down` while `loadFailed()`. The flag is separate from `loaded()` and is set only on the failure, so the start-up window (not yet loaded, no failure) still allows `/mute` as the brief says. `usable` and bans are unchanged. Verified in code. Chat and whispers stay open after a failed load (fail-open, as the brief says). Message text now says "bans and mutes cannot be saved".
- **Regression tests:** `ModerationServiceTest.muteAndUnmuteRefuseWhileTheMuteLoadFailed` (mute and unmute write nothing, the sender is told; without the guard this would insert a row, so it bites); `MuteListTest.aFailedLoadIsRememberedAndTheListIsNotLoaded`; `ModerationModuleTest.aFailedMuteLoadKeepsTheBansWorking` (a store whose mute read throws: a banned player is still refused, a new one allowed). Limit: the module keeps its `ModerationService` private, so the mute refusal through the module itself is covered by the service test and by reading the `loadMutes` wiring, not by a module-level mute command test.
- **LOW-1 (history offset) fixed:** `(int) Math.min((page - 1L) * PAGE_SIZE, Integer.MAX_VALUE)`, correct for every `int` page. `PunishmentHistoryTest.aHugePageNumberShowsTheEmptyHistoryInsteadOfAnError` (page `Integer.MAX_VALUE` shows the empty-history message, not the failed message).
- **LOW-3 tests written:** `ModerationServiceTest`: warn write before the target hears (queued write, nothing told until it lands), failed write (sender told, target hears nothing), self refused with no write, silent warn reaches the target and not a bystander.
- **Still open (LOW-4, coder):** `loadActiveMutes`, `deactivateMutes` and `history` are still default methods in `PunishmentStore`. Not in this round.
- **LOW-2 (whisper aliases of other plugins)** and **PERF-LOW (history index)** stay as follow-ups.
- No new findings in the round-2 diff. No PERF-HIGH or PERF-MEDIUM.

`mise run check` at `88f0190`: BUILD SUCCESS, 224 tests, 0 failures, 0 errors, 1 skipped (the same skip as before).

## Verdict
**Round 1: NOT READY (M-1). Round 2: READY** for `core-moderation-warns` (tests `88f0190` on `core-moderation-warns/plugin-tester`, product code `28ba96c` on the coder branch). Follow-ups for the PR: LOW-4 (coder: abstract the three PunishmentStore defaults; the test fakes then implement them), LOW-2 (other plugins' whisper aliases), PERF-LOW (history index), the in-game check list in section 7.
