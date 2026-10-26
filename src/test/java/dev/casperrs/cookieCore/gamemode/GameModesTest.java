package dev.casperrs.cookieCore.gamemode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.Perm;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class GameModesTest {

    @Test
    void everyTokenMapsToItsModeInAnyCase() {
        Map<String, GameMode> tokens = Map.ofEntries(
                Map.entry("survival", GameMode.SURVIVAL), Map.entry("s", GameMode.SURVIVAL), Map.entry("0", GameMode.SURVIVAL),
                Map.entry("creative", GameMode.CREATIVE), Map.entry("c", GameMode.CREATIVE), Map.entry("1", GameMode.CREATIVE),
                Map.entry("adventure", GameMode.ADVENTURE), Map.entry("a", GameMode.ADVENTURE), Map.entry("2", GameMode.ADVENTURE),
                Map.entry("spectator", GameMode.SPECTATOR), Map.entry("sp", GameMode.SPECTATOR), Map.entry("3", GameMode.SPECTATOR));
        tokens.forEach((token, mode) -> assertEquals(Optional.of(mode), GameModes.parse(token), token));
        assertEquals(Optional.of(GameMode.SPECTATOR), GameModes.parse("SP"));
        assertEquals(Optional.of(GameMode.CREATIVE), GameModes.parse("Creative"));
    }

    @Test
    void junkIsRejected() {
        for (String junk : List.of("", " c", "c ", "4", "-1", "creativ", "gm", "1.0")) {
            assertTrue(GameModes.parse(junk).isEmpty(), "'" + junk + "' must not be a game mode");
        }
    }

    @Test
    void eachModeNeedsItsOwnPermissionNode() {
        assertEquals(Perm.GAMEMODE_SURVIVAL, GameModes.permission(GameMode.SURVIVAL));
        assertEquals(Perm.GAMEMODE_CREATIVE, GameModes.permission(GameMode.CREATIVE));
        assertEquals(Perm.GAMEMODE_ADVENTURE, GameModes.permission(GameMode.ADVENTURE));
        assertEquals(Perm.GAMEMODE_SPECTATOR, GameModes.permission(GameMode.SPECTATOR));
    }
}
