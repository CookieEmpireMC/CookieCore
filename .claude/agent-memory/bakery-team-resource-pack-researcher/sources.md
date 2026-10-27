# Sources checked (URL | what it confirmed | date | stable/volatile)

- https://jd.papermc.io/paper/26.3/... (Javadoc, build 207-stable) | Commands.register overloads, ArgumentTypes.player/players/playerProfiles/time(int)/uuid, CommandSourceStack methods, ArgumentResolver#resolve(CommandSourceStack) throws CommandSyntaxException | 2026-10-20 | stable per version
- .../org/bukkit/event/player/AsyncPlayerPreLoginEvent.html | async; disallow(Result, Component); Result = ALLOWED, KICK_FULL, KICK_BANNED, KICK_WHITELIST, KICK_OTHER | 2026-10-20 | stable
- .../io/papermc/paper/event/connection/PlayerConnectionValidateLoginEvent.html | allow(), kickMessage(Component), isAllowed(); thread NOT documented | 2026-10-20 | stable
- .../io/papermc/paper/event/player/AbstractChatEvent.html, ChatRenderer.html | chat event/renderer signatures | 2026-10-20 | stable
- .../org/bukkit/scoreboard/{Scoreboard,Objective,Team}.html, io/papermc/paper/scoreboard/numbers/NumberFormat.html | Component-based API; NumberFormat.blank()/fixed/styled/noStyle | 2026-10-20 | stable
- https://docs.papermc.io/paper/dev/command-api/basics/{registration,executors,requirements,argument-suggestions,custom-arguments}/ + arguments/{minecraft,entity-player,paper}/ | command API how-to (use trailing slash; /command-executors and /command-requirements 404) | 2026-10-20 | stable
- https://docs.papermc.io/paper/dev/chat-events/ | AsyncChatEvent async, renderer, viewerUnaware | 2026-10-20 | stable
- https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml | newest 26.3 stable build = 207 (205, 206 also) | 2026-10-20 | volatile
- https://repo1.maven.org/maven2/net/luckperms/api/maven-metadata.xml | net.luckperms:api latest 5.5 | 2026-10-20 | volatile
- https://raw.githubusercontent.com/LuckPerms/LuckPerms/master/api/src/main/java/net/luckperms/api/... | LuckPermsProvider.get() throws IllegalStateException; UserManager getUser/loadUser; CachedMetaData getPrefix nullable; Group#getWeight OptionalInt | 2026-10-20 | stable
- https://jd.advntr.dev/api/latest/net/kyori/adventure/audience/Audience.html | sendPlayerListHeaderAndFooter(Component, Component) (shows 4.26.1 docs) | 2026-10-20 | stable

- https://raw.githubusercontent.com/PaperMC/Paper/main/{paper-api/src/main/java/org/bukkit/{entity/Player,entity/HumanEntity,GameMode,Bukkit}.java, paper-server/src/main/java/io/papermc/paper/command/brigadier/{PaperCommands,argument/VanillaArgumentProviderImpl}.java, paper-server/src/main/java/org/bukkit/craftbukkit/{entity/CraftPlayer,entity/CraftHumanEntity,scoreboard/CraftScoreboardManager,scoreboard/CraftScore}.java} | override semantics, fly/speed impl, scoreboard manager | 2026-10-26 | stable-ish (main branch)
- .../paper-server/patches/sources/net/minecraft/server/level/{ServerPlayer,ServerPlayerGameMode}.java.patch | gamemode change event flow | 2026-10-26 | stable-ish
- https://jd.papermc.io/paper/26.3/org/bukkit/scoreboard/{Score,Objective,Scoreboard,Team,ScoreboardManager}.html, .../event/player/PlayerGameModeChangeEvent(.Cause).html, .../scheduler/BukkitScheduler.html, .../argument/ArgumentTypes.html | signatures used in core-gamemode/scoreboard notes | 2026-10-26 | stable
- https://minecraft.wiki/w/Scoreboard (sidebar 15 lines, # hidden), /w/Player.dat_format (abilities) | 2026-10-26 | stable
- https://jd.advntr.dev/api/latest/net/kyori/adventure/text/TextReplacementConfig.Builder.html, .../text-minimessage/latest/.../tag/resolver/Placeholder.html | 4.26.1 signatures | 2026-10-26 | stable
- https://docs.papermc.io/paper/dev/command-api/arguments/{enums,entity-player,minecraft}/ | gameMode arg, player resolve (no-match not documented) | 2026-10-26 | stable

- https://jd.papermc.io/paper/26.3/org/bukkit/event/player/{AsyncPlayerPreLoginEvent,AsyncPlayerPreLoginEvent.Result,PlayerKickEvent,PlayerKickEvent.Cause}.html, .../io/papermc/paper/event/connection/PlayerConnectionValidateLoginEvent.html | pre-login async + methods; kick Cause list (KICK_COMMAND deprecated 26.2); validate-login thread undocumented | 2026-10-27 | stable
- https://raw.githubusercontent.com/PaperMC/Paper/main/paper-server/patches/sources/net/minecraft/server/{network/ServerLoginPacketListenerImpl,network/ServerCommonPacketListenerImpl,players/PlayerList}.java.patch | pre-login on virtual "User Authenticator" threads; PlayerKickEvent flow (cancel = no kick); quit event after kick | 2026-10-27 | stable-ish
- https://raw.githubusercontent.com/mojang/brigadier/master/src/main/java/com/mojang/brigadier/tree/{CommandNode,LiteralCommandNode}.java | literal child wins in getRelevantNodes; literal needs space/end after it | 2026-10-27 | stable
- https://raw.githubusercontent.com/h2database/h2database/master/h2/src/main/org/h2/{engine/Mode,jdbc/JdbcConnection}.java | REGULAR mode autoIncrementClause=true; RETURN_GENERATED_KEYS flag | 2026-10-27 | stable
- https://h2database.com/html/advanced.html (keywords list), https://mariadb.com/kb/en/reserved-words/, .../create-index/ | type/active/silent/reason unreserved on both; CREATE INDEX IF NOT EXISTS both | 2026-10-27 | stable
- LuckPerms common/src/main/resources/me/lucko/luckperms/schema/{h2,mariadb}.sql | AUTO_INCREMENT + CREATE INDEX shape that runs on both | 2026-10-27 | stable

## Dead ends / tips
- mariadb-connector-j raw Statement.java / ClientPreparedStatement.java paths 404 (any branch); only Jira tickets (CONJ-953/957) found for getGeneratedKeys. h2database.com/html/grammar.html lacks AUTO_INCREMENT (use Mode.java).
- Paper *.java.patch files are 170k chars; the WebFetch summarizer reads only the first 100k per window, but targeted prompts on ServerCommonPacketListenerImpl/PlayerList/ServerLoginPacketListenerImpl patches worked.
- Adventure MiniMessage Javadoc says nothing on thread safety.
- docs.advntr.dev/minimessage/api.html redirects to docs.papermc.io/adventure/minimessage/api/. docs.papermc.io/paper/dev/commands and .../misc/comparison-bukkit-brigadier say nothing on overriding vanilla.
- raw .../PlayerSelectorArgumentResolverImpl.java is 404.
- javadoc.io pages for LuckPerms render only navigation: use raw.githubusercontent.com sources instead.
- api.luckperms.net does not resolve; luckperms.net/wiki pages are mostly empty for the fetcher.
- jd.papermc.io Player.html is 505k chars; setPlayerListOrder detail text not reachable via offsets 100000-500000 (direction higher/lower first still unknown).
- docs.papermc.io/paper/dev/scoreboard-api/ and /api/scoreboard/ are 404.
- WebFetch summarizer sometimes invents "general knowledge" code when the page is empty: cross-check against raw sources.
