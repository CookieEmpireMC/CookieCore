---
name: paper-26-3-moderation-facts
description: Verified Paper 26.3 / Brigadier / H2 / MariaDB facts for kick, ban, login deny, -s flag, DDL (full note docs/research/core-moderation-bans-apis.md)
metadata:
  type: reference
---

Verified 2026-10-27.

- `AsyncPlayerPreLoginEvent` runs on a virtual thread per login ("User Authenticator #n"); `disallow(Result, Component)`; blocking legal but Core's JDBC store is single-connection: use an in-memory ban index. `PlayerConnectionValidateLoginEvent` thread still undocumented.
- `Player#kick(Component, PlayerKickEvent.Cause)`: AsyncCatcher = main thread only; fires cancellable `PlayerKickEvent` (cancel = not kicked); `PlayerQuitEvent` follows next tick (reason KICKED). Causes `BANNED`, `KICKED`; `KICK_COMMAND` deprecated since 26.2.
- `Commands.register`: main label always overrides; alias overrides only vanilla (no apiCommandMeta, not BukkitCommandNode) and skips other plugins' commands. `pardon` as alias of `unban` replaces vanilla.
- Brigadier `getRelevantNodes`: a literal child that equals the next token wins exclusively, so `literal("-s")` + sibling greedy reason works; reason starting with bare `-s` is always the flag. Don't reuse one built node under two parents.
- DDL on H2 2.5 REGULAR mode and MariaDB: `id BIGINT AUTO_INCREMENT NOT NULL, ..., PRIMARY KEY (id)`; `CREATE INDEX IF NOT EXISTS name ON t (a,b)` as a separate statement; `type/active/silent/reason` not reserved on either; read id via `prepareStatement(sql, RETURN_GENERATED_KEYS)` + `getGeneratedKeys().next()` + `getLong(1)`. MariaDB side not run locally.
- CookieCore code facts (2026-10-27): `Messages` is documented main-thread-only (HashSet `warned`, non-volatile `yaml`); times in `players` are epoch ms; `KnownPlayerArgument` suggestions come from `PlayerDirectory.namesStartingWith`.
