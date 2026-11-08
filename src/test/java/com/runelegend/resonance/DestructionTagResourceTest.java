package com.runelegend.resonance;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DestructionTagResourceTest {
    @Test
    void productionDestructionTagIsEmptyByDefault() {
        var stream = getClass().getResourceAsStream("/data/runelegend/tags/entity_type/destruction_eligible.json");
        assertNotNull(stream, "The production destruction eligibility tag must exist");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            assertFalse(root.get("replace").getAsBoolean(), "The tag must remain mergeable");
            assertEquals(0, root.getAsJsonArray("values").size(), "The production tag must default to empty");
        } catch (Exception error) {
            fail(error);
        }
    }
}
