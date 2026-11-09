package com.runelegend.combat;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuardConfigTest {
    @Test void defaultJsonContainsEverySetting() {
        var root = GuardConfig.DEFAULT.toJson();
        assertEquals(6, root.size());
        assertEquals(1, root.get("schemaVersion").getAsInt());
        assertEquals(20000, root.get("max").getAsInt());
        assertEquals(2000, root.get("min_to_start").getAsInt());
        assertEquals(125, root.get("drain_per_tick").getAsInt());
        assertEquals(100, root.get("regen_per_tick").getAsInt());
        assertEquals(10, root.get("regen_delay_ticks").getAsInt());
    }

    @Test void jsonRoundTripsEverySetting() {
        var config = new GuardConfig(12000, 1500, 75, 60, 5);
        assertEquals(config, GuardConfig.fromJson(config.toJson()));
        assertEquals(GuardConfig.DEFAULT, GuardConfig.fromJson(GuardConfig.DEFAULT.toJson()));
    }

    @Test void boundedSettingsRejectInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new GuardConfig(0, 0, 125, 100, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new GuardConfig(GuardConfig.WIRE_MAX + 1, 0, 125, 100, 10));
        assertThrows(IllegalArgumentException.class, () -> new GuardConfig(20000, -1, 125, 100, 10));
        assertThrows(IllegalArgumentException.class, () -> new GuardConfig(2000, 2001, 125, 100, 10));
        assertThrows(IllegalArgumentException.class, () -> new GuardConfig(20000, 2000, -1, 100, 10));
        assertThrows(IllegalArgumentException.class, () -> new GuardConfig(20000, 2000, 125, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> new GuardConfig(20000, 2000, 125, 100, -1));
        assertEquals(GuardConfig.WIRE_MAX, new GuardConfig(GuardConfig.WIRE_MAX,
                GuardConfig.WIRE_MAX, 0, 0, 0).max());
    }

    @Test void missingFieldsUseDefaults() {
        var root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        assertEquals(GuardConfig.DEFAULT, GuardConfig.fromJson(root));

        root.addProperty("max", 30000);
        assertEquals(new GuardConfig(30000, 2000, 125, 100, 10), GuardConfig.fromJson(root));
    }

    @Test void invalidRootUsesAllDefaults() {
        assertEquals(GuardConfig.DEFAULT, GuardConfig.fromJson(new JsonObject()));

        var wrongSchema = GuardConfig.DEFAULT.toJson();
        wrongSchema.addProperty("schemaVersion", 2);
        assertEquals(GuardConfig.DEFAULT, GuardConfig.fromJson(wrongSchema));

        var badValue = GuardConfig.DEFAULT.toJson();
        badValue.addProperty("max", 0);
        assertEquals(GuardConfig.DEFAULT, GuardConfig.fromJson(badValue));

        var oversized = GuardConfig.DEFAULT.toJson();
        oversized.addProperty("max", GuardConfig.WIRE_MAX + 1);
        assertEquals(GuardConfig.DEFAULT, GuardConfig.fromJson(oversized));

        var crossed = GuardConfig.DEFAULT.toJson();
        crossed.addProperty("min_to_start", 30000);
        assertEquals(GuardConfig.DEFAULT, GuardConfig.fromJson(crossed));

        var fallbackCrossesMax = new JsonObject();
        fallbackCrossesMax.addProperty("schemaVersion", 1);
        fallbackCrossesMax.addProperty("max", 1000);
        assertEquals(GuardConfig.DEFAULT, GuardConfig.fromJson(fallbackCrossesMax));

        var badType = GuardConfig.DEFAULT.toJson();
        badType.addProperty("drain_per_tick", "fast");
        assertEquals(GuardConfig.DEFAULT, GuardConfig.fromJson(badType));
    }
}
