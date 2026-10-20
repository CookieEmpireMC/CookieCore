---
name: paper-26-3-core-apis
description: Verified Paper 26.3 API facts for commands, login, chat, tab/scoreboard, LuckPerms (full note in docs/research/core-paper-apis.md)
metadata:
  type: reference
---

Verified 2026-10-20 against paper-api 26.3.build.207-stable. Full detail: `docs/research/core-paper-apis.md` in CookieCore.

- Command registration: `getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, e -> e.registrar().register(node, "desc"))`; custom args via `CustomArgumentType.Converted<T,N>`.
- `ArgumentTypes.time()` = int ticks, `d` = in-game day (24000): unusable for ban durations. `playerProfiles()` calls Mojang for unknown names.
- Login deny: `AsyncPlayerPreLoginEvent#disallow(Result, Component)` (async). `PlayerConnectionValidateLoginEvent` thread undocumented; `PlayerLoginEvent` deprecated since 1.21.6.
- Chat: `AsyncChatEvent` async unless plugin-forced; `ChatRenderer.viewerUnaware(...)`.
- Header/footer is `Audience#sendPlayerListHeaderAndFooter`, not a Player method. String-based Player/Team/Objective methods are deprecated. `NumberFormat.blank()` hides sidebar numbers.
- UNVERIFIED still: `setPlayerListOrder` direction, thread rules of scoreboard/tab calls, thread of ValidateLogin.
- LuckPerms: `net.luckperms:api:5.5` (Maven Central), `LuckPermsProvider.get()`, `Group#getWeight()` OptionalInt.
- CookieClicker command style: `final class XCommand { LiteralCommandNode<CommandSourceStack> node() }`, `.requires(src -> src.getSender().hasPermission(..))`.
