# Verified lookups (URL | what it confirmed | date)
- `C:\Users\Luuk\.m2\repository\io\papermc\paper\paper-api\26.3.build.205-stable\paper-api-26.3.build.205-stable.jar` + `mise exec -- javap -cp <jar> -c -p <class>` | `CustomArgumentType.Converted#parse` = `convert(getNativeType().parse(reader))`; `Commands.literal/argument` are plain Brigadier; `MessageComponentSerializer.message()` = ServiceLoader `findFirst().orElseThrow()` | 2026-10-21 (stable for 26.3.build.205)
- Cookie-Clicker `src/test/.../ArchitectureRulesTest.java` | style reference (source scan, `hits(needle, allowedPathPart)`); note its `"/sound/"` path test breaks on Windows separators, mine normalises | 2026-10-21
Dead ends: none yet.
