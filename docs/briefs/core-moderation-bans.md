# core-moderation-bans

**Status:** in progress  <!-- planned | in progress | review | done -->
**Created:** 2026-10-27 · **Depends on:** `core-foundation`, `core-tidy`, `core-scoreboard`, `core-gamemode` (all merged into `main`). Phase 4a of `docs/briefs/ROADMAP.md`; phase 4b (`core-moderation-mutes`) builds on the table and helpers introduced here.

## Goal
Staff can remove a player (`/kick`) or keep them out (`/ban`, `/tempban`, `/unban`) quickly and safely, with a clear reason, a readable ban screen, an alert for the rest of the staff, and a complete audit trail in the database. Offline players can be banned and unbanned by name.

## Player story
As staff I type `/ban Steve griefing the spawn` or `/tempban Steve 7d12h -s spam` and get an immediate confirmation with a soft sound. Steve is removed with a clear screen (who, why, for how long, until when) and cannot rejoin until the ban ends or someone runs `/unban Steve`. Other players see a short announcement, unless I used `-s` (silent): then only staff with the notify permission see it. Every action is recorded, so nobody has to remember who did what.
As a banned player I see exactly why I cannot join and when it ends, not "Connection lost".

## Acceptance criteria
- [ ] **Commands** (Brigadier, plain literals so Paper replaces the vanilla unqualified `/kick`, `/ban`, `/pardon`; the `minecraft:` ones stay vanilla and are not gated by our nodes):
  - `/kick <online player> [-s] [reason...]`
  - `/ban <known player> [-s] [reason...]` (permanent)
  - `/tempban <known player> <duration> [-s] [reason...]` (duration like `7d12h`, the existing `DurationParser`/`kit.duration`; max 100 years)
  - `/unban <known player> [-s] [reason...]` (also `/pardon` as alias); completion for `/unban` lists **currently banned** names only
  - `-s` means silent. Reason is optional; the default text comes from `messages.yml`; reason is limited to 200 characters (clear error above that).
- [ ] **Offline names:** a *known* player (seen by Core at least once, online or not, via `kit.knownPlayer` / `PlayerDirectory`) can be banned and unbanned while offline. A name Core has never seen is rejected with a clear message (no phantom UUIDs). `/kick` needs an online player.
- [ ] **Enforcement:** a banned player is denied at login (`AsyncPlayerPreLoginEvent`) with the ban screen. If the target is online when banned, they are removed at once with the same screen. An expired tempban lets the player in again with no timer and no cleanup task (the login check compares `expires_at` with the clock). The check reads memory only (no database call per login) from an in-memory index of the **active** bans, filled from the database at start and kept in sync on every change.
- [ ] **Not ready yet:** until the active bans are loaded at start-up, logins are denied with a short "server is starting" screen (`moderation.starting`) instead of letting a banned player slip in.
- [ ] **Ban screen** (kick screen too): built only in `text/` from `messages.yml`; shows who banned (staff name or "Console"), the reason, and for a tempban the remaining time and the end date; the ban id for support. Placeholders are inserted unparsed.
- [ ] **Order of effects:** the database write succeeds first; only then the target is removed, the sender confirmed and the alert sent. If the write fails, nobody is kicked and the sender gets an error (`ui_error`). A ban needs the database: with storage in memory-only mode `/ban`, `/tempban` and `/unban` refuse with a clear message; `/kick` still works (logged to the console only, once).
- [ ] **Alerts:** a non-silent action is announced to everyone online (`moderation.broadcast.*`). A silent action (`-s`) goes only to online holders of `cookiecore.staff.notify`. Staff alerts play `ui_notify`. Every action also writes one INFO line to the server log (actor, target, type, reason, duration).
- [ ] **Safety:** you cannot kick or ban yourself; banning someone who is already banned (active and not expired) is refused and says until when; unbanning someone who is not banned says so. Console can run every command (actor "Console").
- [ ] **Audit log:** every kick, ban, tempban and unban is a row in the new table (see Contract): actor uuid and name, target uuid and name, reason, created time, expiry, silent flag. An unban marks the ban inactive and records who removed it, when and why; rows are never deleted. Names are stored next to the uuids so the log stays readable after a rename.
- [ ] **Permissions** (plain Bukkit nodes, default op): `cookiecore.staff.kick`, `cookiecore.staff.ban` (covers `/ban` and `/tempban`), `cookiecore.staff.unban`, `cookiecore.staff.notify`, plus the wildcard `cookiecore.staff.*`. Declared in `paper-plugin.yml` and `Perm`; `PluginDescriptorTest` keeps them in sync. Commands are hidden and denied for senders without the node.
- [ ] **Messages and sounds:** all player-facing text under `moderation.*` in `messages.yml`; sounds only through `SoundService` (`ui_confirm` on success, `ui_error` on errors, `ui_notify` for alerts). No new sound events; no new `config.yml` keys.
- [ ] **Storage:** migration v3 adds exactly one table, with the configured prefix, working on H2 and MariaDB; a v2 database upgrades in place and keeps its data (tested). All JDBC on the IO thread, nothing in `storage/` imports Bukkit.
- [ ] **Performance:** no repeating task, no database call on the main thread, no database call per login after start-up; the login check is a hash lookup. `/unban` completion reads the in-memory index.
- [ ] Lean tests for the important parts (command trees and permission gating; ban index: active, expired, unban; login decision: banned, tempban left and expired, not loaded yet; ban-screen text incl. unparsed placeholders; migration v2 to v3 and the store round trip on H2; silent vs public alert routing; architecture rules still pass). No test per class.
- [ ] `mise run check` green; `ownership-check` clean for every merged branch.

## Out of scope
- Mutes, warnings and `/history` (phase 4b, which reuses the table and the alert/ban-screen helpers).
- IP bans, appeals, web panel (roadmap D6); bans that propagate live between servers sharing one database (a ban made on another server shows after this server restarts or re-reads; revisit in hardening).
- Immune ranks (a "cannot be banned" permission), ban reasons from a preset list, ban expiry notices, public API for other plugins.
- Paper's own ban list (`banned-players.json`); Core uses its own table (roadmap D3), so `/minecraft:ban` and the vanilla list are untouched.

## Assets needed
| Kind | What | Owner |
|---|---|---|
| model / texture / glyph | none | model-designer |
| sound | none: existing `ui_confirm`, `ui_error`, `ui_notify` (vanilla keys, roadmap D7) | plugin-coder |
| effect | none | vfx-designer |

## Balance impact
none

## Research needed
For Citation Cindy, in `docs/research/core-moderation-bans-apis.md` (Paper 26.3.build.205, verified against the API jar, not from memory):
1. `AsyncPlayerPreLoginEvent`: how to deny with a `Component` (result, `disallow`), what runs on which thread, whether blocking is allowed and what a plain map read costs. Is there a newer Paper-only login event (for example a configuration-phase or `PlayerConnection` API) that is better, and is `PlayerLoginEvent` (forbidden by our rules) the only alternative?
2. `Player#kick(Component)` and its cause; any event to listen for or avoid (`PlayerKickEvent`); that quit cleanup in `PlayerListener` still runs on a kick.
3. Replacing the vanilla unqualified `/kick`, `/ban`, `/pardon` with plain Brigadier literals (same technique as `/gamemode`: result of `Commands.register`, labels taken, aliases); whether `/pardon` and `/unban` can share a tree.
4. A literal flag `-s` between the player argument and the greedy reason in Brigadier (suggestion and parse order; is a reason that itself starts with `-s` ambiguous).
5. Table DDL that works unchanged on H2 (default mode) and MariaDB: auto-increment `BIGINT` primary key, `BOOLEAN`, `VARCHAR` limits, an index on `(target_uuid, active)`, `INSERT` returning the generated id with plain JDBC (`RETURN_GENERATED_KEYS`) on both drivers.
6. Date formatting for the ban screen (`java.time`, server zone or UTC, locale) and a human-readable "remaining" text from a `Duration` without a library.

## Open questions
Defaults are decided by the orchestrator and used if the user does not object:
1. **Public announcement by default?** Non-silent kicks/bans are announced to all players; `-s` limits it to staff. (Alternative: staff-only unless `-p`.)
2. **Never-seen names cannot be banned.** The player must have joined once so Core knows the uuid (no Mojang lookup). (Alternative: add a Mojang name lookup, which needs the network and a cache.)
3. **No database, no bans.** With storage in memory-only mode `/ban`, `/tempban`, `/unban` refuse; `/kick` still works. (Alternative: ban in memory only, lost on restart.)

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| command | `/kick`, `/ban`, `/tempban`, `/unban` (alias `/pardon`) | plugin-coder | Brigadier, package `moderation/`, one `node()` per command |
| permission | `cookiecore.staff.kick`, `cookiecore.staff.ban`, `cookiecore.staff.unban`, `cookiecore.staff.notify`, `cookiecore.staff.*` | plugin-coder | `Perm.STAFF_KICK`, `STAFF_BAN`, `STAFF_UNBAN`, `STAFF_NOTIFY`; default op; in `paper-plugin.yml` and `Perm.ALL` |
| schema | migration v3: `<prefix>punishments` | plugin-coder | `id BIGINT` auto-increment PK, `type VARCHAR(16)` (`KICK`, `BAN`; phase 4b adds `MUTE`, `WARN`), `target_uuid`, `target_name`, `actor_uuid` (NULL = console), `actor_name`, `reason`, `created_at` and `expires_at` (NULL = permanent / not applicable) in the same epoch representation as `players.first_seen`, `silent BOOLEAN`, `active BOOLEAN`, `removed_at`, `removed_by_uuid`, `removed_by_name`, `remove_reason` (all NULL while active); index on `(target_uuid, active)`. Append-only migration, `IF NOT EXISTS` |
| storage | `PunishmentStore` (+ JDBC implementation) in `storage/` | plugin-coder | insert returning the id, load active bans, deactivate by target; no Bukkit |
| classes | `moderation/BanList` (in-memory index of active bans, thread-safe reads), `ModerationService`, `ModerationListener` (login check), `KickCommand`, `BanCommand`, `TempbanCommand`, `UnbanCommand`, `BanScreen` text inputs | plugin-coder | final names may be adjusted by the coder inside the package; reported in its DONE |
| text | ban/kick screen built in `text/Messages` (new method), kick executed there | plugin-coder | the architecture rule keeps `kick(...)` out of other packages |
| message keys | `moderation.default-reason`, `moderation.starting`, `moderation.screen.kick`, `moderation.screen.ban`, `moderation.screen.tempban`, `moderation.kicked`, `moderation.banned`, `moderation.tempbanned`, `moderation.unbanned`, `moderation.broadcast.kick`, `.ban`, `.tempban`, `.unban`, `moderation.alert.silent`, errors (`self`, `already-banned`, `not-banned`, `unknown-player`, `reason-too-long`, `storage-down`, `failed`, `no-permission`) | plugin-coder | in `messages.yml`; extra keys under `moderation.*` allowed |
| sound events | `ui_confirm`, `ui_error`, `ui_notify` | plugin-coder | already in `sounds.yml` |
| config key | none | | no new keys |

Shared files (`Perm`, `paper-plugin.yml`, `messages.yml`, `CookieCore`, `Migrations`) are touched by this feature only; nothing runs in parallel.

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| resource-pack-researcher | `docs/research/core-moderation-bans-apis.md` (the six questions above) | brief approved | feature branch (docs) |
| plugin-coder | everything under acceptance criteria except tests | contract, research note on the feature branch | `core-moderation-bans/plugin-coder` |
| plugin-tester | tests, review of the coder branch, `docs/architecture.md` update (storage v3, `moderation` package), integration review | coder DONE | `core-moderation-bans/plugin-tester` |

One coder run: about 14 to 18 files, one shared table and one login listener, so splitting would cost more in coordination than it saves. No designers (roadmap D7: sounds stay vanilla; no assets, no balance).

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| resource-pack-researcher | planned | feature branch | n/a | |
| plugin-coder | planned | `core-moderation-bans/plugin-coder` | | |
| plugin-tester | planned | `core-moderation-bans/plugin-tester` | | |

## Merge order
1. research note (direct on the feature branch)
2. plugin-coder
3. plugin-tester

## Needs human review
- [ ] Check in game on Paper 26.3: `/ban` of an online and of an offline player (screen text, no rejoin), `/tempban Steve 1m` then rejoin after a minute, `/unban`, `/kick`, silent vs public announcement, the ban survives a server restart, a `v2` database upgrades without losing players, `/minecraft:ban` still works, the vanilla `/ban` is replaced.
- [ ] Look at the ban screen text and the announcement lines in `messages.yml`; tone and wording are yours to change.
- [ ] Listen to the vanilla sounds (`ui_confirm`, `ui_error`, `ui_notify`).
- [ ] Decide the three open questions above (defaults apply if you do not object).
