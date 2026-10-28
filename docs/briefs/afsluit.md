# Afsluit: stand van zaken (bijgewerkt 2026-10-28, na de merge van PR #10; 4b plak 2 staat open als PR)

Overdracht voor de volgende sessie. Lees eerst dit bestand, daarna `docs/briefs/ROADMAP.md`.

## Waar staan we
`main` bevat alles tot en met fase 4b plak 1. Alle PR's zijn door Luuk gemerged. Daarbovenop staat plak 2 van 4b (`/warn`, `/history`, geblokkeerde `/msg`-commando's voor gemute spelers, opsplitsing van `JdbcPlayerStore`) als open PR op `feature/core-moderation-warns`.

| Stap | PR | Inhoud |
|---|---|---|
| Fase 0 `core-foundation` | #4 | basis, opslag, `/cookiecore`, API |
| Stap A `core-tidy` | #5 | opgelijnd met de CookieMemory-regels, `paper-plugin.yml`, schonere tests |
| Fase 2 `core-scoreboard` | #7 | sidebar per speler, placeholder-API, `/scoreboard toggle`, opslag v2 |
| Fase 3 `core-gamemode` | #6 | `/gamemode` `/gm` `/gms` `/gmc` `/gma` `/gmsp`, `/fly`, `/speed` |
| Fase 4a `core-moderation-bans` | #9 | `/kick` `/ban` `/tempban` `/unban`, banscherm, staff-melding, auditlog (`punishments`, opslag v3) |
| Fase 4b plak 1 `core-moderation-mutes` | #10 | `/mute` `/tempmute` `/unmute`, chat geblokkeerd, overleeft herstart |
| Fase 4b plak 2 `core-moderation-warns` | open PR | `/warn`, `/history <speler> [pagina]`, `/msg` `/tell` `/w` `/whisper` `/me` geblokkeerd voor gemute spelers, `JdbcPunishmentStore` los van `JdbcPlayerStore` |

- `mise run check`: 213 tests op `main`, 224 op de warns-branch; 0 mislukt, 1 bewust overgeslagen (scoreboard L-4).
- Fase 1 (tab) is door Luuk overgeslagen.
- **Nog te doen in 4b (dan is het af):** alleen de melding als een tempmute afloopt. Die vraagt een timer, en de scheduler mag alleen in `CookieCore.java` (199 van max 200 regels). Doe dat bewust, bijvoorbeeld met fase 5, of laat de melding komen bij de eerstvolgende chat- of join-actie. Daarna fase 5. Start pas na Luuks "go"; hij draait `/bakery-team:feature` zelf.
- Follow-ups uit de reviews staan in de PR's #10 en de warns-PR (o.a. `PunishmentStore`-methodes abstract maken, `activeNames` bij tab-completie, history-index).

## Voor Luuk (niet door een agent te doen)
- **In-game test op een Paper 26.3-server** (nog niets is door een echte server gehaald):
  - schone start (paper-plugin.yml, loader, drivers), `/cookiecore version|reload`;
  - **scoreboard:** toont `Score#customName` de tekst, zonder rode cijfers en zonder `l00`? Zo niet: Teams-fallback (alleen `BoardSink` wisselt, zie `docs/architecture.md`). Ook: geen flikkering, `/sb toggle` overleeft relog en herstart;
  - **bestaande v1-database** start zonder fouten en houdt zijn spelers (kopieer eerst `plugins/CookieCore`);
  - **gamemode:** de 22 stappen in `docs/reviews/2026-10-27-core-gamemode.md`, vooral `/fly` na `/gm a`, kick van een vliegende speler, `minecraft:gamemode` werkt nog.
- `.claude/hooks/` uit CookieClicker kopiëren en registreren in `.claude/settings.json`.
- Geluiden: pas eigen geluiden als er paden voor de sound-designer in `ownership.json` komen; tot dan vanilla (D7).

## Beslissingen die Luuk heeft goedgekeurd (door te mergen)
- Anderen van modus veranderen vraagt de modus-permissie én `cookiecore.gamemode.others`.
- `/gamemode` neemt één speler (`@a` werkt niet meer); `/minecraft:gamemode` wordt niet door onze nodes beperkt.
- `api/` mag precies `Player`, `Plugin` en `Component` tonen (voor het placeholder-contract).

## Follow-ups (LOW, geen fix-ronde)
- **Scoreboard:** L-4 (`scoreboard.yml` Online-regel laat `<gray>` open; eenregelige fix staat in de PR #7, daarna `@Disabled` weghalen in `ScoreboardLayoutTest`), L-2 (toggle in het laadvenster kan de keuze omdraaien), L-3 (placeholder met de naam van een MiniMessage-tag), L-5 (typefouten in `scoreboard.yml` niet gemeld), L-6, L-7, PERF-LOW-1 (alle regels opnieuw renderen bij één wijziging).
- **Gamemode:** `FlyTracker` gebruikt het verouderde `Player#isOnGround()`; een foute modus of `/speed`-getal is een parse-fout zonder `ui_error`; registratie van `/fly` en `/speed` wordt niet gecontroleerd; commando-omschrijvingen zijn Engelse literals; `/fly`-intrekking wordt pas bij de volgende modus-wissel gezien; bij uitzetten in de lucht volgt valschade.
- **Uit core-tidy:** `PluginSettings.intOrDefault` (`3308.5` wordt stil `3308`), Javadoc in `CookieCoreApi` noemt nog `softdepend`, lege `cookiecore-libraries.properties` geeft stil nul libraries, null-scope bij `Dependency` (onbevestigd tot de eerste Paper-start).
- **Moderatie (4a):** zie `docs/reviews/2026-10-27-core-moderation-bans.md` sectie 6 (in-game, ook een v2-database en een MariaDB-controle met de hand). **Mutes:** `docs/reviews/2026-10-28-core-moderation-mutes.md`.
- Idee: één kleine "polish"-run (coder + tester) voor bovenstaande LOW's.

## Tooling en omgeving
- `mise` werkt (pad `%LOCALAPPDATA%\Microsoft\WinGet\Packages\jdx.mise_Microsoft.Winget.Source_8wekyb3d8bbwe\mise\bin\mise.exe` als het niet op PATH staat). Eerder blokkeerde Windows Application Control `mise.exe` tijdelijk; zo ja, Maven direct draaien (JDK/Maven onder `%LOCALAPPDATA%\mise\installs`).
- `mise run check` wordt door de GateGuard-hook als destructief gezien: eerst de feiten noemen (wijzigt alleen `target/`), dan opnieuw. Voor de eerste Bash/PowerShell-opdracht, de eerste Write/Edit per bestand en `git branch -D` geldt hetzelfde.
- `ownership-check`: `.claude/hooks/ownership_lib.py` ontbreekt in deze repo. Draai `python tools\ownership\check.py <branch>` met `CLAUDE_PROJECT_DIR` naar een kopie met `hooks\ownership_lib.py` (uit `C:\dev\Cookie-Clicker\.claude\hooks\`) en de actuele `ownership.json`.
- Agents: `SendMessage` naar een eerdere agent-id hervat hem met zijn context. Gandalf draait op haiku; de integratiereview mag `model: "sonnet"`. Een coder die vastloopt (stream watchdog) kan met `SendMessage` hervat worden.
- Parallelle features: de tweede worktree krijgt een eigen branch (`git worktree add`), en agents krijgen expliciet de basis-branch. De gedeelde bestanden (`Perm`, `paper-plugin.yml`, `messages.yml`, `CookieCore`, `CookieCoreApi`) alleen toevoegend wijzigen; de tweede PR vraagt na de eerste merge toch een conflictoplossing. De ownership-hook weigert Rick `Edit` op testbestanden; een mergeconflict daarin los je op in de merge zelf (of laat Gandalf het doen).
- gh is niet geïnstalleerd in de shell; CI-status ophalen kan niet met `gh`.

## PR's openen als bot
- Eigen GitHub App `cookie-bakery-luuk` (App ID 5476628, Installation ID 175376554), van de organisatie CookieEmpireMC. Config: `~/.config/cookieclicker/github-app.env` en `github-app.pem` (alleen Luuk leest; nooit lezen of tonen). Zie geheugen `github-app-pr-setup.md`.
- `mise run pr-open` werkt op Windows niet (key-check eist mode 600, Windows meldt 666). Aanroep: `PATH` met `C:\Program Files\Git\usr\bin` vooraan (openssl), dan Python die `C:\dev\Cookie-Clicker\tools` op `sys.path` zet, `app.check_key_file = lambda p: p` zet en `open_pr.py` laadt met `importlib.util.spec_from_file_location`. Zet `tools\github_app` **niet** op `sys.path` (`token.py` verdekt de stdlib). Argumenten: `--head --base main --repo CookieEmpireMC/CookieCore --title --body-file`.
- Luuk wil alleen controleren en de PR-URL krijgen; PR's van zijn eigen account kan hij niet zelf goedkeuren (main eist 1 review). Een PR sluiten vraagt Luuks expliciete toestemming. Alleen `feature/*` pushen, nooit force, nooit `gh pr create` met Luuks account.
- **Windows-fix voor `pr-open`** moet nog gebouwd worden in een sessie in `C:\dev\Cookie-Clicker`: `/bakery-team:feature github-app-windows-key "pr-open: skip the unix mode-600 check on Windows (os.name == 'nt'); key is protected by its ACL. Add a test."`

## Eerste stappen volgende sessie
1. `git status`, `git fetch`; staat de docs-PR (`feature/docs-afsluit`) al in `main`? Zo ja: `git switch main && git pull`, lokale `feature/docs-afsluit` verwijderen.
2. Vraag Luuk naar de uitkomst van de in-game test (vooral het scoreboard); bij een rendering-probleem eerst de Teams-fallback.
3. Bij Luuks "go": de volgende fase is fase 5 `core-chat-join` (chatformaat, join/quit, `/msg` `/r`, staff chat); de verloopmelding van tempmutes past daar goed bij. Reviews: `docs/reviews/2026-10-28-core-moderation-warns.md`.
