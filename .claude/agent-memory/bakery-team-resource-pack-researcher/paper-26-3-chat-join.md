---
name: paper-26-3-chat-join
description: Verified Paper 26.3 / LuckPerms facts for chat renderer, join/quit, MOTD, msg override (full note docs/research/core-chat-join-apis.md)
metadata:
  type: reference
---

Verified 2026-10-28. Full detail: `docs/research/core-chat-join-apis.md`.

- Chat: set only `ChatRenderer.viewerUnaware(...)`, never `event.message(..)`; Paper keeps the original signature, custom renderer = chat type `paper:raw`. Sync `ChatEvent` deprecated.
- LuckPerms: `net.luckperms:api:5.5` latest (2025-05-25); `UserManager#getUser(UUID)` null if not loaded (PlayerAdapter#getUser throws ISE); prefix is legacy `&` string, nullable.
- PermissibleBase.permissions is a plain HashMap, hasPermission unsynchronized.
- msg: vanilla aliases are tell, w only; plugin alias registration replaces vanilla nodes; `minecraft:<name>` copies stay vanilla. MuteListener label set lacks pm/r/reply.
- PaperServerListPingEvent is async (`super(true)`); `motd(Component)`; getNumPlayers -1 if hidden.
- `hasPlayedBefore()` exact in PlayerJoinEvent (CraftPlayer.readExtraData sets it).
- Title: Title.title(c,c,int,int,int) since 4.24.0; Adventure 4.26.1.
- UNVERIFIED: chat report screen on rendered msg, minecraft:msg in game, title in PlayerJoinEvent, LegacyComponentSerializer.Builder.hexColors.
