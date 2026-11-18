package com.runelegend.player;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerStateConfigTest {
    @Test void defaultsAndFractionalTicks() {
        assertEquals(PlayerStateConfig.DEFAULT, PlayerStateConfig.fromJson(PlayerStateConfig.DEFAULT.toJson()));
        assertEquals(40, PlayerStateConfig.DEFAULT.reviveTicks());
        assertEquals(300, PlayerStateConfig.DEFAULT.downedTicks());
        assertEquals(4, PlayerStateConfig.DEFAULT.distanceSquared());
        assertEquals(2, new PlayerStateConfig(2, 0.051, 1, 25).reviveTicks());
        var missing = new JsonObject();
        missing.addProperty("revive_distance", 4);
        assertEquals(new PlayerStateConfig(4, 2, 15, 50), PlayerStateConfig.fromJson(missing));
    }

    @Test void rejectsInvalidAndNonNumericValuesWithoutTruncation() {
        for (double value : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new PlayerStateConfig(value, 3, 15, 50));
            assertThrows(IllegalArgumentException.class, () -> new PlayerStateConfig(2, value, 15, 50));
        }
        assertThrows(IllegalArgumentException.class, () -> new PlayerStateConfig(2, 3, 15, 101));
        for (String field : new String[]{"revive_distance", "revive_duration_seconds", "downed_duration_seconds", "revive_health_percent"}) {
            var json = PlayerStateConfig.DEFAULT.toJson();
            json.addProperty("revive_distance", 9);
            json.addProperty(field, "1");
            assertEquals(PlayerStateConfig.DEFAULT, PlayerStateConfig.fromJson(json));
        }
    }
}
