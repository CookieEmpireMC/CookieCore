# CookieCore roadmap

Written by Rickroll Rick, 2026-10-20, from the user's direction: *"Tab, Scoreboard, Gamemode commands, Staff commands (/kick, /ban, /mute, /unmute). Even though it is simple, it must work as tight as possible and still feel impressive."*

CookieCore is the server's **base layer**: the things every player and staff member touches in the first ten seconds, before any gameplay. Gameplay stays in [CookieClicker](https://github.com/CookieEmpireMC/CookieClicker). Core never depends on CookieClicker; CookieClicker may use Core's small public API (soft dependency).

## Principles (what "tight" means here)
1. **Nothing blocks the main thread.** Database and file work on one IO thread, results back on main (same rule as CookieClicker).
2. **Nothing flickers, nothing spams.** Tab and scoreboard update by diff, at most once per second, only when the text changed.
3. **Every message is text in `messages.yml`** (MiniMessage, placeholders unparsed), every feedback sound an event in `sounds.yml`. No player text or sound key in Java.
4. **Permissions are plain Bukkit nodes** (`cookiecore.<area>.<action>`), so LuckPerms works without a hard dependency.
5. **Staff actions are auditable.** Every kick/ban/mute lands in the database with actor, target, reason, time and duration.
6. **Small surface, high polish.** Fewer commands, each with tab completion, clear errors, a confirmation sound and a readable message.

## Decisions made by the orchestrator (the user can overrule any of these)
| # | Decision | Why |
|---|---|---|
| D1 | Paper 26.3, Java 25, package `dev.casperrs.cookieCore`, same layout and rules as CookieClicker | one toolchain, one architecture style |
| D2 | LuckPerms is a **soft** dependency (ordering, prefix); without it Core falls back to permission-based ranks from `config.yml` | server still boots and tests without it |
| D3 | Bans, mutes and history live in **Core's own tables** (H2 default, MariaDB optional), not in Paper's ban list | one audit trail, offline-name support, tempbans, mute |
| D4 | English default text, all editable in `messages.yml` | one file to translate later |
| D5 | Brigadier commands (Paper lifecycle API), no `plugin.yml` command entries | native completion and validation |
| D6 | IP bans, appeals, web panel: **out of scope** for now | keeps phases small |
| D7 | Feedback sounds are **vanilla** for now. The sound-designer has no pack folder or `sounds.yml` path in Core's `ownership.json`, and Core has no resource pack. Wherever a phase below lists the sound-designer, it applies only after the user adds those paths | respects the human-controlled ownership map |

## Phases (one feature = one branch = one PR; the user merges each into `main`)

Each phase starts only when the previous one is merged (the workflow gate "Depends on"). Names below are the `/bakery-team:feature` names.

| Phase | Feature | What the player / staff gets | Agents involved |
|---|---|---|---|
| **0** | `core-foundation` | Nothing visible yet except a clean boot log, `/cookiecore reload` and `/cookiecore version`. Underneath: build, config, messages, sound service, permission helper, Brigadier command helper, storage on an IO thread, player name cache, small public API, architecture tests, mise tasks. | researcher, plugin-coder, plugin-tester |
| **1** | `core-tab` | Tab list with header and footer (server name, online count, ping/TPS line), players sorted by rank with prefix, no flicker, no packet spam. | researcher, plugin-coder, plugin-tester |
| **2** | `core-scoreboard` | Sidebar per player without flicker or red score numbers, lines from config, **placeholder registry** in the API so CookieClicker can later show cookies/CPS, `/scoreboard toggle`. | researcher, plugin-coder, plugin-tester |
| **3** | `core-gamemode` | `/gamemode` (`/gm`), `/gmc /gms /gma /gmsp`, optional target, completion, confirmation sound; plus `/fly` and `/speed` as small staff comfort. | plugin-coder, plugin-tester, sound-designer |
| **4a** | `core-moderation-bans` | `/kick`, `/ban`, `/tempban`, `/unban` with duration parser (`7d12h`), reason, silent flag, ban screen, staff alert, audit log, offline-name resolution. | researcher, plugin-coder, plugin-tester, sound-designer |
| **4b** | `core-moderation-mutes` | `/mute`, `/tempmute`, `/unmute`, `/warn`, `/history <player>`; mute blocks chat and private messages, survives restart, expiry notice. | plugin-coder, plugin-tester, sound-designer |
| **5** | `core-chat-join` | Chat format with rank prefix, join/quit messages, welcome title, MOTD, `/msg` `/r`, staff chat. | plugin-coder, plugin-tester, sound-designer |
| **6** | `core-hub-essentials` | `/spawn` `/setspawn`, vanish, maintenance mode, `/seen`. Only what the hub proves it needs. | plugin-coder, plugin-tester |
| **7** | `core-hardening` | Performance review with many (fake) players, config validation messages, docs for staff, final architecture notes. | plugin-tester, plugin-coder |

### Who is not used, and why
- **balance-designer:** Core has no economy. Cookie/CPS numbers shown on the scoreboard come from CookieClicker through the placeholder API.
- **vfx-designer:** the `Effects` engine lives in CookieClicker. Revisit if Core needs particles (for example a ban or join effect); until then no effects in Core.
- **model-designer:** needs a resource pack. Phase 1 may later want a logo glyph in the tab header; that is a follow-up once it is decided how the pack loader and Core cooperate (see open questions).

## Open questions (none block phase 0; defaults apply)
1. Server name and tab/scoreboard title text (default `Cookie Empire`; the GDD still has the name open).
2. Tab header logo glyph from the resource pack, yes or no (default no, plain text until the pack question is settled).
3. Ranks and prefixes: take them from LuckPerms meta (default) or define in `config.yml`.
4. Which staff ranks exist and who may use `/ban` vs `/kick` only (default: `cookiecore.staff.*` split per command).
