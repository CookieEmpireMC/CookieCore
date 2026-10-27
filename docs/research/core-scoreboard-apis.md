# Paper 26.3 sidebar: limits, flicker-free updates, lifecycle (phase 2, `core-scoreboard`)

Citation Cindy, 2026-10-26. Target: Paper `26.3.build.205-stable`. Gaps ONLY on top of section 4 of `core-paper-apis.md` (objective/team/NumberFormat basics are there). Tags: [JD] 26.3 Javadoc, [SRC] Paper `main` source on GitHub (may be newer than build 205), [WIKI] minecraft.wiki Scoreboard, [DOC] Adventure docs. **UNVERIFIED** = not confirmed there; test in game.

## Summary
1. **Plan change (recommended):** `Score#customName(@Nullable Component)` exists in 26.3 [JD]. A line is one entry with a fixed unique name (`l00`...) plus a custom display Component; changing a line = one `customName(...)` call, no Teams needed. Teams (prefix/suffix) stay the fallback. The brief's "Lines are Teams" can be replaced by this, if an in-game check shows the sidebar renders the custom name (vanilla feature `display` since 1.20.3, [WIKI] `/scoreboard players display name`; rendering of the Paper call itself **UNVERIFIED**, 5-minute test).
2. Max 15 lines; give every line a unique score (`n - index`) so order never depends on tie-breaks.
3. Everything on the main thread; `getNewScoreboard()` even throws off-thread.

## 1. Limits
| Item | Value |
|---|---|
| visible sidebar lines | 15 highest scores [WIKI] ("up to 15 entities with the highest score") |
| tie order | by score, then alphabetical on the entry name [WIKI, 14w29a: "secondarily sorted by alphabetical order"; wording implicit]. Avoid ties |
| entries starting with `#` | never shown in the sidebar [WIKI]: do not start entry names with `#` |
| entry string length | `Objective#getScore(String)` throws `IllegalArgumentException` above 32767 chars [JD]; the old 16-char limits were removed in 1.18 (21w37a) [WIKI] |
| Team prefix/suffix, `customName` Component | no limit in [JD] (all methods only throw `IllegalStateException` once unregistered) |
| objective title | `Objective#displayName(Component)`: Javadoc says `IllegalArgumentException` for null or over 128 characters despite `@Nullable` [JD]; keep title short, never null |
| entry uniqueness | a `Score` is "one entry on one objective" [JD]: the same entry string twice is the same line. Use distinct names (`l00`..`l14`) |
| blank line | a unique entry with `customName(Component.empty())` (render **UNVERIFIED**; fallback `Component.text(" ")`) and its own score; never reuse an entry for two blanks |
| long text | no wrapping; the client widens the sidebar to the widest line (**UNVERIFIED**, vanilla behaviour). Practical cap about 30-40 visible characters; clip in the config validation, not at render time |

## 2. No-flicker update recipe
Setup once per player (main thread):
```java
Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();           // AsyncCatcher: main thread only [SRC]
Objective o = sb.registerNewObjective("sidebar", Criteria.DUMMY, title);
o.setDisplaySlot(DisplaySlot.SIDEBAR);
o.numberFormat(NumberFormat.blank());
for (int i = 0; i < n; i++) o.getScore("l" + (i < 10 ? "0" : "") + i).setScore(n - i);  // fixed order, never changed again
player.setScoreboard(sb);                                                  // after the entries exist: one full sync
```
Per tick, per line, only when the rendered Component differs (`Component.equals`) from the last one sent:
```java
o.getScore(entry).customName(rendered);      // Score#customName(@Nullable Component); creates the score at 0 if missing
```
- `Score` methods [JD]: `customName()`, `customName(@Nullable Component)`, `getEntry()`, `getObjective()`, `getScore()`, `setScore(int)`, `isScoreSet()`, `resetScore()`, `numberFormat()/numberFormat(@Nullable NumberFormat)`, `getScoreboard()`. All throw `IllegalStateException` after the objective is unregistered. `customName` does `getOrCreatePlayerScore(...).display(...)` [SRC]: no remove/add.
- Cost: one changed line = one score update to the viewer (client packet `ClientboundSetScorePacket`, **UNVERIFIED** count). Unchanged tick = nothing sent, as long as YOU skip equal values (whether Paper dedupes equal writes is **UNVERIFIED**).
- Team fallback: one `Team` per line, entry `l00`.. added once with `team.addEntry(entry)`, then only `team.prefix(Component)` / `suffix(Component)` (one team-update packet per changed line). Same diff rule.
- What flickers: `Scoreboard#resetScores(entry)` or `Score#resetScore()` followed by a re-add (remove + add on the client), `Objective#unregister()` + re-register, `setDisplaySlot(null)` then back, switching to another `Scoreboard` every second, entries whose NAME carries the changing text, and rewriting `displayName` every second (compare first; it re-sends the objective, no flicker but pointless traffic).
- Ordering: only `setScore(int)`. If the line count changes on reload (rare): `resetScore()` the dropped entries and `setScore` the rest once; that one-time change may flash, acceptable.
- Score numbers: `o.numberFormat(NumberFormat.blank())` once; no per-score override needed.

## 3. `Player#setScoreboard(Scoreboard)` [JD + SRC `CraftScoreboardManager`]
- Throws `IllegalArgumentException` for null or a board not made by the manager, `IllegalStateException` if the player "is not logged yet or has logged out". Call it from `PlayerJoinEvent` (not from async pre-login), never from a quit handler.
- Effects: the server removes every objective display slot and every team of the OLD board from the viewer by packets, then sends the whole NEW board. Everything the main board showed that player (vanilla `/team` colours, nametag teams from other plugins, `below_name`/`list` objectives, collision rules) disappears for that viewer. Core must therefore be the only plugin that owns per-player boards on this server, or document it. The old board object stays intact for others.
- Passing `getMainScoreboard()` clears the override (`setScoreboardOverride(null)`); `Player#getScoreboard()` returns the override or the main board.
- Detecting a replacement: no Paper event found (absence **UNVERIFIED**). In the 1 s tick compare `player.getScoreboard() != ownBoard` (same instance is returned [SRC]). Brief rule "overwrite at most once per join": keep a `boolean reasserted` per player; first mismatch re-apply `setScoreboard(ownBoard)`, later mismatches: drop the player from the sidebar set and log once.
- Quit: the server clears the override itself (`removePlayer` -> `setScoreboardOverride(null)`). Do NOT call `setScoreboard` on quit (`IllegalStateException` once logged out); just remove the map entry (the board is only weakly tracked, `getNewScoreboard()` is strongly held only by your reference). Toggle off while online: `player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard())`, then drop the reference. Do not `unregister()` on quit.
- `Bukkit.getScoreboardManager()` is nullable per Javadoc before the first world loads [SRC `Bukkit`]; irrelevant at join time.
- Paper tracks plugin boards globally only if its config `trackPluginScoreboards` is on [SRC]; `Criteria.DUMMY` does not need it.

## 4. Threading and the single task
- Main thread only. `getNewScoreboard()` calls `AsyncCatcher.catchOp("scoreboard creation")` and throws off-thread [SRC]; `CraftScoreboard`/`CraftScore` methods have no thread check [SRC] but the NMS scoreboard is not thread-safe and sends packets. Compute values on main (they are cheap) and never touch boards from the IO thread; toggle persistence is loaded async, applied on main.
- Scheduler [JD `BukkitScheduler`]: `BukkitTask runTaskTimer(Plugin, Runnable, long delay, long period)`; `void runTaskTimer(Plugin, Consumer<? super BukkitTask>, long, long)` (void, task can cancel itself). `period` in ticks (20 = 1 s).
```java
// SidebarService, main thread only
private final Map<UUID, Sidebar> viewers = new HashMap<>();
private @Nullable BukkitTask task;
void show(Player p) { viewers.put(p.getUniqueId(), Sidebar.create(p)); if (task == null) task = scheduler.runTaskTimer(plugin, this::tick, 20L, 20L); }
void hide(UUID id) { viewers.remove(id); if (viewers.isEmpty() && task != null) { task.cancel(); task = null; } }
private void tick() { if (viewers.isEmpty()) { stop(); return; } viewers.values().forEach(Sidebar::refresh); }
```
`PlayerQuitEvent` -> `hide(id)`; `onDisable` -> `stop()`. `refresh` for an offline player must not throw: skip when `!player.isOnline()`.

## 5. Cheap Components per second
- MiniMessage has no compile step; parse once at load. `Placeholder.parsed/unparsed(@TagPattern String key, String value)`, `Placeholder.component(String key, ComponentLike)` exist (adventure-text-minimessage 4.26.1 docs; Paper 26.3 ships Adventure 5, same names expected, compile to confirm) [JD advntr]. `MiniMessage#deserialize(String, TagResolver)` is the per-call form; [DOC] says to create the `MiniMessage` instance once.
- Biggest saving: skip rendering when the VALUES did not change. Keep per player a small `Object[]`/record of resolved values (`player`, `ping`, `online`, registered placeholder results as their Components); if equal to last tick, do nothing. Only then build a Component.
- Template recipe (parse once, substitute values): at load, `mm.deserialize(lineSource, Placeholder.component(key, Component.text(MARK + key + MARK)))` for every known key, with `MARK = "\u0001"` (cannot occur in admin YAML). Per render: for each key present: `tpl = tpl.replaceText(TextReplacementConfig.builder().matchLiteral(MARK + key + MARK).replacement(valueComponent).build())`. Builder methods verified [JD advntr]: `matchLiteral(String)`, `replacement(ComponentLike)`, `replacement(String)`, `once()`. Text values become `Component.text(v)` (never parsed = "unparsed" semantics); registry placeholders return a Component and go in as-is. Pre-build the `TextReplacementConfig` per (line,key) only if the value is constant; otherwise one small builder per changed value is fine.
- Test (brief): `PlainTextComponentSerializer` output and `mm.serialize(...)` of the substituted template equal those of a real `mm.deserialize(source, resolvers)` for sample values; do not rely on `Component.equals` between the two (tree shape may differ). Keep a cache of the last sent Component per line for the `equals` diff against ITSELF.
- Keys unknown at parse time (typo `<foo>`) stay as literal tags or throw in a strict parser: validate at load and report in the reload problems list.

## Pitfalls
- Entries starting with `#` never render. Duplicate entry strings merge lines. More than 15 lines silently drop the lowest scores.
- `registerNewObjective` with an existing name throws `IllegalArgumentException`: one objective per board, created once.
- Calling `customName` on a score of an unregistered objective throws `IllegalStateException`: drop the `Sidebar` when the player toggles off.
- Another plugin's `setScoreboard` on the same player wipes Core's board visibility without any event.
- `Objective#displayName(Component)` 128-char Javadoc limit may be stale; keep titles short.

## Sources
- https://jd.papermc.io/paper/26.3/org/bukkit/scoreboard/{Score,Objective,Scoreboard,Team,ScoreboardManager}.html , .../org/bukkit/scheduler/BukkitScheduler.html
- https://raw.githubusercontent.com/PaperMC/Paper/main/paper-api/src/main/java/org/bukkit/entity/Player.java , .../Bukkit.java ; paper-server `org/bukkit/craftbukkit/scoreboard/{CraftScoreboardManager,CraftScoreboard,CraftScore}.java`
- https://minecraft.wiki/w/Scoreboard
- https://jd.advntr.dev/api/latest/net/kyori/adventure/text/TextReplacementConfig.Builder.html , https://jd.advntr.dev/text-minimessage/latest/net/kyori/adventure/text/minimessage/tag/resolver/Placeholder.html , https://docs.papermc.io/adventure/minimessage/api/ , https://docs.papermc.io/adventure/minimessage/dynamic-replacements/

## Hand-off
- **plugin-coder:** implement `Sidebar` (per player: board, objective, last-sent `Component[]`, value snapshot, `reasserted` flag) and one `SidebarService` task as in section 4. Do 5 minutes of in-game test first: `customName` on `l00` shows in the sidebar with blank numbers; if not, switch to the Team prefix/suffix fallback (same diff code, different setter) and tell the orchestrator. Entries `l00`..`l14`, scores `n - i`, `NumberFormat.blank()`, load templates once with the marker recipe, diff by values then by `Component.equals`. Setup on `PlayerJoinEvent` (after the async toggle lookup returns to main), teardown on quit by map removal only.
- **Skip:** `resetScores` per tick, re-registering objectives, per-player tasks, async scoreboard calls, anything on the main scoreboard, `setScoreboard` on quit.
- **plugin-tester:** diff logic test with a fake `LineSink` (change/no-change/line-removed/toggle sends the expected number of writes); template-vs-real-parse test via plain text + `mm.serialize`; `ArchitectureRulesTest`: scoreboard calls only in the `scoreboard` package.
- **Contract change proposed:** brief says "Lines are Teams with unique invisible entries": change to "Lines are fixed entries with `Score#customName`; Teams only as a documented fallback" (no new IDs).
- **Designers:** nothing.
