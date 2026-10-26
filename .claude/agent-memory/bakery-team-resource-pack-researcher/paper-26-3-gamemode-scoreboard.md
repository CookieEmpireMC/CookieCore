---
name: paper-26-3-gamemode-scoreboard
description: Verified Paper 26.3 facts for gamemode/fly/speed and sidebar scoreboard (full notes in docs/research/core-gamemode-apis.md and core-scoreboard-apis.md)
metadata:
  type: reference
---

Verified 2026-10-26 (Javadoc 26.3 + Paper `main` source on raw.githubusercontent.com).

- `ArgumentTypes.gameMode()` exists (vanilla names only). `Commands.register(node, desc, aliases)` overrides vanilla main labels; aliases do not override other plugins' commands.
- PaperCommands source: `.../paper-server/src/main/java/io/papermc/paper/command/brigadier/PaperCommands.java`; CraftPlayer/CraftScore/CraftScoreboardManager in `.../org/bukkit/craftbukkit/{entity,scoreboard}/` (CraftPlayer is 144k chars: fetch with offset 50000 for fly/speed).
- Fly/walk speed API range -1..1, stored /2; `setFlying(true)` throws if not `getAllowFlight()`.
- `PlayerGameModeChangeEvent`: fires before change, same-mode = no event, cancel = silent for plugin calls.
- `Score#customName(Component)` exists (no teams needed for sidebar lines); `getNewScoreboard()` has AsyncCatcher; `setScoreboard` on a logged-out player throws ISE; quit clears the override itself.
- Paper patches are readable at `paper-server/patches/sources/net/minecraft/.../X.java.patch` (ServerPlayer, ServerPlayerGameMode).
- UNVERIFIED: `minecraft:gamemode` after override, `GameType.updatePlayerAbilities` body, ability persistence on relog, sidebar rendering of customName, packet counts.
