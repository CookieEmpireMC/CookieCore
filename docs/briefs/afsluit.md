# Afsluit: stand van zaken (bijgewerkt 2026-10-27)

Overdracht voor de volgende sessie. Lees eerst dit bestand, daarna `docs/briefs/ROADMAP.md`.

## Waar staan we
- **Fase 0 `core-foundation`:** gemerged in `main` (PR #4).
- **Stap A `core-tidy`:** af, verdict **READY**, `mise run check` groen (137 tests, was 230). Branch `feature/core-tidy`, gepusht, PR als bot geopend (URL: zie laatste bericht van Rick of GitHub). Wacht op Luuks goedkeuring en merge. Brief: `docs/briefs/core-tidy.md`, review: `docs/reviews/2026-10-26-core-tidy.md`.
- **B = fase 2 scoreboard** en **C = fase 3 gamemode:** nog niet gestart. Starten pas als `core-tidy` in `main` staat (gate "Depends on"). Fase 1 tab: Luuk heeft die niet aangewezen; vraag of tab overgeslagen blijft.

## Follow-ups (LOW, geen fix-ronde)
- L-1: `PluginSettings.intOrDefault`, tak voor `Number`: `3308.5` wordt stil `3308`, te grote `Long` wrapt.
- L-2: `api/CookieCoreApi.java:9` Javadoc noemt nog `softdepend: [CookieCore]`.
- L-3: lege `cookiecore-libraries.properties` geeft stil nul libraries (gooi in `libraries()`).
- L-4 (onbevestigd): `Dependency(new DefaultArtifact(...), null)`, null-scope; eerste Paper-start moet dat laten zien.
- Pakket `config` importeert `sound`, `storage`, `text` (geen cyclus; gedeelde regel zegt: geen feature-pakketten).
- Uit fase 0: F-13, F-14, F-10/F-11 (INFO).
Idee: één kleine "polish"-run (coder + tester) voor L-1 tot L-3 samen met het begin van fase 2.

## Tooling en omgeving
- `mise` werkt weer (2026.10.12; pad `%LOCALAPPDATA%\Microsoft\WinGet\Packages\jdx.mise_Microsoft.Winget.Source_8wekyb3d8bbwe\mise\bin\mise.exe` als het niet op PATH staat). Eerder was `mise.exe` tijdelijk geblokkeerd door Windows Application Control; zo ja, Maven direct draaien (JDK/Maven onder `%LOCALAPPDATA%\mise\installs`).
- `mise run check` wordt door de GateGuard-hook als destructief gezien: eerst de feiten noemen (wijzigt alleen `target/`), dan opnieuw.
- `ownership-check`: `.claude/hooks/ownership_lib.py` ontbreekt in deze repo. Draai `python tools\ownership\check.py <branch>` met `CLAUDE_PROJECT_DIR` naar een kopie met `hooks\ownership_lib.py` (uit `C:\dev\Cookie-Clicker\.claude\hooks\`) en de actuele `ownership.json`.
- Agents: `SendMessage` naar een eerdere agent-id hervat hem met zijn context (werkte over sessies heen); Gandalf draait nu op haiku, de integratiereview mag `model: "sonnet"`.

## PR's openen als bot
- Eigen GitHub App `cookie-bakery-luuk` (App ID 5476628, Installation ID 175376554). Config: `~/.config/cookieclicker/github-app.env` en `github-app.pem` (alleen Luuk leest). Zie geheugen `github-app-pr-setup.md`.
- `mise run pr-open` werkt op Windows niet (key-check eist mode 600, Windows meldt 666). Aanroep: `PATH` met `C:\Program Files\Git\usr\bin` vooraan (openssl), dan Python die `C:\dev\Cookie-Clicker\tools` op `sys.path` zet, `app.check_key_file = lambda p: p` zet en `open_pr.py` laadt met `importlib.util.spec_from_file_location`. Zet `tools\github_app` **niet** op `sys.path` (`token.py` verdekt de stdlib). Argumenten: `--head --base main --repo CookieEmpireMC/CookieCore --title --body-file`.
- Luuk wil alleen controleren en de PR-URL krijgen; PR's van zijn eigen account kan hij niet zelf goedkeuren (main eist 1 review). Een PR sluiten vraagt Luuks expliciete toestemming (de permissiecontrole weigerde het anders).
- **Windows-fix voor `pr-open`** moet nog gebouwd worden in een sessie in `C:\dev\Cookie-Clicker`: `/bakery-team:feature github-app-windows-key "pr-open: skip the unix mode-600 check on Windows (os.name == 'nt'); key is protected by its ACL. Add a test."`

## Voor Luuk (niet door een agent te doen)
- In-game test op een Paper 26.3-server (schone start, drivers laden, `/cookiecore version|reload`, `players` gevuld na een join, `last_seen` na stop met speler online). Sinds `core-tidy` is dit extra belangrijk (paper-plugin.yml en loader zijn nog niet door een echte server gehaald).
- `.claude/hooks/` uit CookieClicker kopiëren en registreren in `.claude/settings.json`.
- Geluiden: pas als er paden voor de sound-designer in `ownership.json` komen, zijn eigen geluiden mogelijk; tot dan vanilla (D7).

## Eerste stappen volgende sessie
1. `git status`, `git fetch`; staat PR van `core-tidy` al in `main`? Zo ja: `git switch main && git pull`, oude `feature/core-tidy` en `feature/core-foundation` lokaal verwijderen.
2. Alleen als `core-tidy` gemerged is: B en C starten, parallel. Eerst gedeelde bedrading (`Perm`, `messages.yml`, `sounds.yml`, `paper-plugin.yml`, `CookieCore`) in één coder-run, merge, dan de twee fases elk met eigen coder en tester.
3. Tijdsschatting: B 45 tot 60 min, C 25 tot 35 min, review en PR per fase 10 tot 15 min.
