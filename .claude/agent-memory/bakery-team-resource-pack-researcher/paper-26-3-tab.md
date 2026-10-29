---
name: paper-26-3-tab
description: Verified tab list facts for Paper (main branch, checked 2026-10-29): list order direction, no dedupe, ping/TPS, LuckPerms primary group weight
metadata:
  type: reference
---

Full detail: `docs/research/core-tab-apis.md` in CookieCore.
- `Player#setPlayerListOrder(int)`: HIGHER = listed first (wiki protocol "Update List Priority"), default 0, `>= 0` required (CraftPlayer Preconditions), broadcast to every viewer who canSee; ties: spectator after non-spectator, team name, then name.
- `playerListName(Component)` and `sendPlayerListHeaderAndFooter` have no dedupe; both set in PlayerJoinEvent go in the first info packet (event fires before the info packets in PlayerList.placeNewPlayer).
- `getPing()` = connection.latency(), EWMA (3*old+new)/4, keep-alive every ~15 s, throws without connection. `getTPS()` = double[3] (1m/5m/15m); `getTickTimes()` clones array in nanos; `getAverageTickTime()` ms 5 s avg.
- LuckPerms: `User#getPrimaryGroup()` / `CachedMetaData#getPrimaryGroup()`, `GroupManager#getGroup` nullable, `Group#getWeight()` OptionalInt (missing = 0); config `primary-group-calculation` ships as parents-by-weight.
- Not found: first Paper release of setPlayerListOrder; packet size limits for list names; unknown-entry update behaviour on the client.
