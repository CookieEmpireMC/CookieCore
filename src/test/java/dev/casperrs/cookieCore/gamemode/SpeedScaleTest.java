package dev.casperrs.cookieCore.gamemode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpeedScaleTest {

    @Test
    void levelOneIsTheVanillaDefaultAndTenIsExactlyFullSpeed() {
        assertEquals(0.2f, SpeedScale.walk(1));
        assertEquals(0.1f, SpeedScale.fly(1));
        assertEquals(1.0f, SpeedScale.walk(10));
        assertEquals(1.0f, SpeedScale.fly(10));
    }

    @Test
    void levelsInBetweenAreLinearAndIncrease() {
        for (int level = 2; level <= 10; level++) {
            assertTrue(SpeedScale.walk(level) > SpeedScale.walk(level - 1), "walk " + level);
            assertTrue(SpeedScale.fly(level) > SpeedScale.fly(level - 1), "fly " + level);
        }
        assertEquals(0.2 + 0.8 * 4 / 9, SpeedScale.walk(5), 1e-6);
        assertEquals(0.1 + 0.9 * 4 / 9, SpeedScale.fly(5), 1e-6);
    }

    @Test
    void levelsOutsideOneToTenAreClampedAndOnlyOneToTenIsValid() {
        assertEquals(SpeedScale.walk(1), SpeedScale.walk(0));
        assertEquals(SpeedScale.walk(10), SpeedScale.walk(11));
        assertEquals(SpeedScale.fly(1), SpeedScale.fly(-3));
        assertEquals(SpeedScale.fly(10), SpeedScale.fly(99));
        assertFalse(SpeedScale.valid(0));
        assertFalse(SpeedScale.valid(11));
        assertTrue(SpeedScale.valid(1));
        assertTrue(SpeedScale.valid(10));
    }
}
