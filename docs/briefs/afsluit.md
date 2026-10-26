# Afsluit: stand van zaken (2026-10-26)

Overdracht voor de volgende sessie. Lees eerst dit bestand, daarna `docs/briefs/core-tidy.md` en `docs/briefs/ROADMAP.md`.

## Waar staan we
- **Fase 0 `core-foundation`: gemerged in `main`** (PR #4, door Luuk goedgekeurd). Plugin is nog nooit gestart op een Paper-server.
- **Stap A `core-tidy`: bezig, niet af, niet gepusht.** Branch `feature/core-tidy` (lokaal, HEAD `d21a33a`). Doel: fase 0 op de nieuwe CookieMemory-regels brengen en F-12 fixen. Brief: `docs/briefs/core-tidy.md`.
- Daarna het plan: **B = fase 2 scoreboard** en **C = fase 3 gamemode**, parallel (fase 1 tab is door Luuk niet aangewezen; vraag of tab overgeslagen blijft). Nog niet gestart.

## core-tidy: wat is af
Coder (Works4Me Wesly), branch `core-tidy/plugin-coder`, **gemerged** in `feature/core-tidy` (ownership-check schoon):
- `plugin.yml` wordt `paper-plugin.yml` + `CookieCoreLoader` (PluginLoader + MavenLibraryResolver) + `cookiecore-libraries.properties`; LuckPerms onder `dependencies.server`.
- `@param`/`@return` weg, `config.yml` ingekort (`shutdown-timeout-seconds` is nu constante `CookieCore.SHUTDOWN_TIMEOUT_SECONDS = 10`), F-12 gefixt in `PluginSettings.intOrDefault`.
- `src/main` compileert (direct met Maven, zie "Tooling"); **tests zijn niet gedraaid**.

## core-tidy: wat nog moet (in volgorde)
Tester (Gandalf), branch `core-tidy/plugin-tester`, worktree `.claude/worktrees/core-tidy-tester`, commit `1799a52` (alleen een deelnotitie in `docs/reviews/2026-10-26-core-tidy.md`). **Ongecommitte wijziging** in die worktree: `src/test/.../config/PluginSettingsTest.java` (herschreven, niet geverifieerd, dus bewust niet gecommit). Verdict nu: **NOT READY** (testsuite compileert niet).
1. `PluginDescriptorTest` aanpassen: leest nog `/plugin.yml`. Moet `paper-plugin.yml` lezen; pom `h2.version`/`mariadb.version` koppelen aan `cookiecore-libraries.properties` via `CookieCoreLoader.libraries()` (beide coördinaten exact asserten); `loader:` = `dev.casperrs.cookieCore.CookieCoreLoader`; `dependencies.server.LuckPerms` = load BEFORE, required false, join-classpath true; `Perm` tegen permissions.
2. `PluginSettingsTest` afmaken (timeout-tests weg, F-12-test aan, `'3308'`, `' 3308 '`, `'70000'`, `3308.5`), dan compileren en draaien.
3. Suite inkorten (voor: 230 totaal, 1 overgeslagen; na: nog onbekend), regressietests voor echte bugs (F-xx) houden.
4. `ArchitectureRulesTest` bekijken (regel "config importeert geen feature-pakket" niet rood maken, zie follow-up).
5. `docs/architecture.md`: Standards-sectie + verouderde regels (`plugin.yml`, `libraries:`, "LuckPerms is a soft dependency", ca. regels 5, 12, 41, 50).
6. Review afmaken met VERDICT; daarna `ownership-check`, merge `--no-ff` als Rickroll Rick, `check`, PR openen.

Aanbeveling: Gandalf-run **hervatten** (zelfde agent, context nog warm) met `SendMessage` of opnieuw starten met dit lijstje; de WIP in de worktree behouden.

## Follow-ups (LOW, geen fix-ronde)
- `PluginSettings.intOrDefault`, tak voor `Number`: `3308.5` wordt stil naar `3308` afgekapt, te grote `Long` wrapt. Fix: `Integer` direct accepteren, rest uit de string parsen en problemen melden. Beslis: LOW-follow-up (advies tester) of fix-ronde.
- `api/CookieCoreApi.java:9` Javadoc noemt nog `softdepend: [CookieCore]`.
- Lege `cookiecore-libraries.properties` geeft stil nul libraries; fout komt pas later als "JDBC driver not found".
- Pakket `config` importeert `sound`, `storage` (`DatabaseSettings`) en `text`; geen cyclus, maar de gedeelde regel zegt: `config` importeert geen feature-pakket.
- Niet geverifieerd: de sleutels `load`/`required`/`join-classpath` in `paper-plugin.yml` worden door de server geparsed, niet door de API-jar; eerste start op Paper 26.3 moet dat bevestigen.
- Uit fase 0: F-13, F-14, F-10/F-11 (INFO).

## Tooling en omgeving
- **`mise.exe` wordt geblokkeerd door Windows Application Control** (PowerShell: "An Application Control policy has blocked this file"; Bash: "Permission denied"). Daardoor is `mise run check` deze sessie niet gedraaid. Workaround: Maven direct, JDK `%LOCALAPPDATA%\mise\installs\java\temurin-25.0.4+101.0.LTS`, Maven `%LOCALAPPDATA%\mise\installs\maven\3.9.16\apache-maven-3.9.16\bin`. Een echte `mise run check` moet nog één keer draaien waar het wel kan (of Luuk voegt een uitzondering toe).
- `ownership-check`: `.claude/hooks/ownership_lib.py` ontbreekt in deze repo. Draai `python tools\ownership\check.py <branch>` met `CLAUDE_PROJECT_DIR` naar de kopie in de scratchpad (`...\scratchpad\own`, bevat `hooks\ownership_lib.py` en een kopie van `ownership.json`; kopieer de actuele `ownership.json` er eerst naartoe). Scratchpad is per sessie: kopieer anders `ownership_lib.py` uit `C:\dev\Cookie-Clicker\.claude\hooks\`.
- Casper heeft in `main` een regel voor de repo-watcher (Gossip Gus) aan `ownership.json` toegevoegd (PR #3).

## PR's openen als bot
- Eigen GitHub App `cookie-bakery-luuk` (App ID 5476628, Installation ID 175376554). Config: `~/.config/cookieclicker/github-app.env` en `github-app.pem` (alleen Luuk leest). Details staan in het geheugen `github-app-pr-setup.md`.
- `mise run pr-open` werkt op Windows niet (key-check eist mode 600, Windows meldt 666). Ik draai `open_pr.py` direct met `app.check_key_file` overschreven; de aanroep staat in de sessiegeschiedenis van 2026-10-26. Let op: `tools/github_app/token.py` verdekt de stdlib-module `token`, zet die map dus **niet** op `sys.path`; laad `open_pr.py` met `importlib.util.spec_from_file_location` en zet `C:\Program Files\Git\usr\bin` vooraan op `PATH` (openssl).
- Luuk wil alleen controleren en de PR-URL krijgen. `main` eist 1 goedkeuring; PR's van zijn eigen account kan hij niet zelf goedkeuren, dus altijd via de bot. Sluiten van een PR vereist toestemming van Luuk (de permissiecontrole weigerde het zelf doen).
- **Windows-fix voor `pr-open`** moet nog gebouwd worden in een sessie in `C:\dev\Cookie-Clicker`: `/bakery-team:feature github-app-windows-key "pr-open: skip the unix mode-600 check on Windows (os.name == 'nt'); key is protected by its ACL. Add a test."`

## Voor Luuk (niet door een agent te doen)
- In-game test van fase 0 én na `core-tidy` op een Paper 26.3-server: schone start, drivers laden, `/cookiecore version`, `/cookiecore reload`, tabel `players` gevuld na een join, `last_seen` na stoppen met speler online.
- `.claude/hooks/` (`ownership.py`, `ownership_lib.py`) uit CookieClicker kopiëren en registreren in `.claude/settings.json`.
- Windows Application Control: `mise.exe` toestaan.
- Geluiden: pas als er paden voor de sound-designer in `ownership.json` komen, zijn er eigen geluiden mogelijk; tot dan vanilla (D7).

## Eerste stappen volgende sessie
1. `git status`; controleer dat `feature/core-tidy` op `d21a33a` staat en de worktrees `core-tidy-tester` (WIP!) en `agent-a8af949a58fcee3ec` (coder, mag na afronding weg) bestaan.
2. Gandalf hervatten of herstarten met de lijst "wat nog moet".
3. Na READY: ownership-check, merge, test, daarna `git push -u origin feature/core-tidy` (alleen `feature/*`, nooit force) en PR als bot openen. Cleanup: `git worktree remove` en `git branch -d core-tidy/*`.
4. Pas daarna B en C starten (parallel), eerst gedeelde bedrading (`Perm`, `messages.yml`, `sounds.yml`, `paper-plugin.yml`, `CookieCore`) in één run zetten.
5. Tijdsschatting uit de planning: B 45 tot 60 min, C 25 tot 35 min, review en PR per fase 10 tot 15 min.
