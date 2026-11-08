package com.runelegend.resonance;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResonanceSavedDataTest {
    @Test
    void codecRoundTripsLayersAndSelection() {
        var layerHolder = UUID.randomUUID();
        var player = UUID.randomUUID();
        var selection = Identifier.parse("runelegend:protection");
        var data = new ResonanceSavedData(Map.of(layerHolder, new ResonanceLayers(1, 2, 3)),
                Map.of(player, selection), true);

        var encoded = (CompoundTag) ResonanceSavedData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        var decoded = ResonanceSavedData.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();

        var json = ResonanceSavedData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().getAsJsonObject();
        assertEquals(1, json.get("schemaVersion").getAsInt(), "Schema version is always persisted");

        assertEquals(new ResonanceLayers(1, 2, 3), decoded.layers(layerHolder));
        assertEquals(Optional.of(selection), decoded.guardSelection(player));
    }

    @Test
    void unknownSelectionIdentifiersArePreserved() {
        var player = UUID.randomUUID();
        var selection = Identifier.parse("removed:resonance");
        var data = new ResonanceSavedData(Map.of(), Map.of(player, selection), true);

        var encoded = ResonanceSavedData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        var decoded = ResonanceSavedData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(Optional.of(selection), decoded.guardSelection(player));
    }

    @Test
    void schemaVersionAndNegativeLayersAreRejected() {
        assertTrue(ResonanceSavedData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"schemaVersion\":2}")).isError());

        var uuid = UUID.randomUUID();
        var invalid = JsonParser.parseString("{\"schemaVersion\":1,\"layers\":{\""
                + uuid + "\":{\"protection\":-1}}}");
        var result = ResonanceSavedData.CODEC.parse(JsonOps.INSTANCE, invalid);
        assertTrue(result.isError());
        assertTrue(result.resultOrPartial(ignored -> {}).isEmpty(),
                "A partial result must not be accepted as a valid save");
    }

    @Test
    void writesRemoveEmptyRecordsAndTrackDirtiness() {
        var uuid = UUID.randomUUID();
        var data = new ResonanceSavedData(Map.of(), Map.of(), true);
        assertFalse(data.isDirty());

        assertTrue(data.writeLayers(uuid, new ResonanceLayers(1, 2, 3)));
        assertTrue(data.isDirty());
        assertFalse(data.writeLayers(uuid, new ResonanceLayers(1, 2, 3)));
        assertTrue(data.eraseLayers(uuid));
        assertEquals(ResonanceLayers.NONE, data.layers(uuid));
    }

    @Test
    void readOnlyDataRejectsEveryWrite() {
        var uuid = UUID.randomUUID();
        var data = new ResonanceSavedData(Map.of(), Map.of(), false);
        assertThrows(IllegalStateException.class, () -> data.writeLayers(uuid, new ResonanceLayers(1, 0, 0)));
        assertThrows(IllegalStateException.class, () -> data.eraseLayers(uuid));
        assertThrows(IllegalStateException.class, () -> data.writeGuardSelection(uuid,
                Optional.of(Identifier.parse("runelegend:protection"))));
    }
}
