package com.runelegend;

import com.runelegend.combat.GuardConfig;
import com.runelegend.combat.StaminaManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StaminaManagerTest {
    private final StaminaManager stamina = new StaminaManager(GuardConfig.DEFAULT);

    @Test void exactStartupThreshold() {
        assertEquals(2000, stamina.config().minToStart());
        assertFalse(stamina.canStart(1999));
        assertTrue(stamina.canStart(2000));
    }

    @Test void exactDrainAndRecoveryBoundaries() {
        int units = stamina.max();
        for (int i = 0; i < 159; i++) units = stamina.drain(units);
        assertEquals(125, units);
        assertEquals(0, stamina.drain(units));
        assertEquals(0, stamina.drain(0));

        assertEquals(0, stamina.recover(0, 10, 0));
        units = 0;
        for (int t = 1; t <= 29; t++) units = stamina.recover(units, 160 + t, 160);
        assertEquals(1900, units);
        assertEquals(stamina.config().minToStart(), stamina.recover(units, 190, 160));
        assertEquals(stamina.max(), stamina.recover(stamina.max() - 1, 191, 160));
    }

    @Test void defaultConfigIsExposedAndClampedAtMax() {
        assertSame(GuardConfig.DEFAULT, stamina.config());
        assertEquals(20000, stamina.max());
        assertEquals(stamina.max(), stamina.recover(stamina.max(), 100, 0));
    }

    @Test void customConfigDrivesThresholdDrainAndRecovery() {
        var config = new GuardConfig(1000, 100, 10, 5, 3);
        var custom = new StaminaManager(config);
        assertEquals(config, custom.config());
        assertEquals(1000, custom.max());
        assertFalse(custom.canStart(99));
        assertTrue(custom.canStart(100));

        int units = custom.max();
        for (int i = 0; i < 99; i++) units = custom.drain(units);
        assertEquals(10, units);
        assertEquals(0, custom.drain(units));

        assertEquals(0, custom.recover(0, 3, 0));
        assertEquals(5, custom.recover(0, 4, 0));
        assertEquals(1000, custom.recover(999, 4, 0));
    }

    @Test void zeroThresholdAllowsEmptyStamina() {
        var custom = new StaminaManager(new GuardConfig(1, 0, 0, 1, 0));
        assertTrue(custom.canStart(0));
        assertEquals(1, custom.drain(1));
        assertEquals(1, custom.recover(0, 1, 0));
    }

    @Test void missingConfigIsRejected() {
        assertThrows(NullPointerException.class, () -> new StaminaManager(null));
    }
}
