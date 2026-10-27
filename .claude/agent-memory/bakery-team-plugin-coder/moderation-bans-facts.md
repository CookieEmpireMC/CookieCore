---
name: moderation-bans-facts
description: Verified facts from building core-moderation-bans (2026-10-27): ArchitectureRulesTest traps, JDBC/H2 behaviour, how to smoke-test without writing tests
metadata:
  type: reference
---

Verified 2026-10-27 on Paper 26.3.build.205-stable, CookieCore commit 16db8ed + moderation branch:
- `ArchitectureRulesTest` bans the substring `.kick(` anywhere outside `text/`, also for OUR OWN methods: a service method named `kick` fails the build (named it `kickOut`; `Messages.disconnect(Player, Component, Cause)` is the wrapper). Same for `.disallow(` and `.kickMessage(`: login deny lives in `Messages.denyBanned/denyStarting`. Avoid local variables named `result|done|f|future` with `.get(`.
- `Messages` may contain exactly one `Placeholder.parsed(` (the prefix, test pins it); new message code must use `Placeholder.unparsed/component`.
- text/ may import storage (storage is a leaf), so ban-screen methods take `storage.BanRecord`; moderation importing text and storage is fine; text must not import moderation (cycle).
- `MessagesTest` strict MiniMessage parse does NOT fail on unknown placeholder tags (`<reason>`, `<until>` ... passed with the fixed placeholder set), so new placeholders need no test change. Do not use `<x>`-style angle brackets for usage text; use `(player) [-s]`.
- JDBC: `rs.wasNull()` refers to the LAST column read, so read the nullable column into a local and call `wasNull()` immediately. `RETURN_GENERATED_KEYS` + `getLong(1)` works on H2 2.x (ids start at 1). Index name carries the table prefix (schema-wide in H2).
- `Executor` can be satisfied by `this::runOnMainThread` (void, Runnable) — same as `ScoreboardModule`.
- Smoke test without writing tests (coder may not write src/test): take `surefire.test.class.path` from `target/surefire-reports/TEST-*.xml` (value of that property), put it in a file, `javac -cp <it> -d <out> Scratch.java` and run `java -cp out;<it>` from the scratchpad; it can use `TestSupport` (test-classes). `mvn dependency:build-classpath` is NOT available offline.
- Brigadier: literal `-s` as sibling of the greedy argument wins; `-sx` and `-S` fall through to the reason (confirmed with a real `CommandDispatcher`).
- CookieCore.java had 191 of 200 lines before moderation; wiring cost 7 lines (module field + construct + load + listener + register + doc).
