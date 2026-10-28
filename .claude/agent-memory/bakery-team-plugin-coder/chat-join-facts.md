---
name: chat-join-facts
description: Verified facts from building core-chat-join (chat renderer, LuckPerms isolation, /msg override, Template use, arch-test limits), stable for Paper 26.3.205, 2026-10-28
metadata:
  type: reference
---

Verified 2026-10-28 by compiling against paper-api 26.3.build.205-stable (stable until the version changes):
- `Title.title(Component, Component, int, int, int)` and `LegacyComponentSerializer.builder().character('&').hexColors().build()` both compile; `&#ff8800` becomes `<#FF8800>` (smoke run).
- `net.luckperms:api:5.5` resolves from Maven Central with the existing papermc-repo (no new `<repository>`); `mvn -o` fails on the first resolve, run once online.
- `Template.compile(src, keys)` + `render(Map<String,String>, Map<String,Component>)` is the cheap way to render chat.yml lines: `<gray><message></gray>` keeps the player's text literal (`<red>hi` stays text) and inherits the gray. Key order = replacement order, so put `message` last.
- ArchitectureRulesTest forces: `showTitle(`, `sendMessage(`, `LegacyComponentSerializer`, `MiniMessage`, `.deserialize(` only in `text/` (so `Messages.title/deliver` and `ChatText` exist); `net.luckperms` on any line is flagged until the tester changes that rule; `PluginDescriptorTest` demands default `op` for every Perm except scoreboard.toggle, so `silentjoin` (false) and `chat.msg` (true) need a test change.
- Coder cannot edit src/test: those two tests stay red until the tester updates them; report under REQUESTS.
- Smoke-run recipe without a test file: `mise exec -- mvn -B -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt`, then javac/java a scratch class from the scratchpad with `cp.txt;target/classes` (PowerShell, `mise exec -- javac`).
- The Bash tool refuses `S=...; mise ... $S` (variable in a mise command); use literal paths or PowerShell.
