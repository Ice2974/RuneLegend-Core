package com.runelegend.resonance;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResonanceLayersTest {
    @Test
    void codecRoundTripsAllCounters() {
        var expected = new ResonanceLayers(1, 2, 3);
        var encoded = ResonanceLayers.CODEC.encodeStart(JsonOps.INSTANCE, expected).getOrThrow();
        assertEquals(expected, ResonanceLayers.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void missingFieldsDefaultToZero() {
        var decoded = ResonanceLayers.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
        assertEquals(ResonanceLayers.NONE, decoded);
    }

    @Test
    void negativeCounterIsRejectedByConstructorAndCodec() {
        assertThrows(IllegalArgumentException.class, () -> new ResonanceLayers(-1, 0, 0));
        assertTrue(ResonanceLayers.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"protection\":-1}")).isError());
    }

    @Test
    void getAndWithAddressASingleAttribute() {
        var layers = new ResonanceLayers(1, 2, 3);
        assertEquals(1, layers.get(ResonanceAttribute.PROTECTION));
        assertEquals(2, layers.get(ResonanceAttribute.LOST));
        assertEquals(3, layers.get(ResonanceAttribute.DESTRUCTION));
        assertEquals(new ResonanceLayers(5, 2, 3), layers.with(ResonanceAttribute.PROTECTION, 5));
        assertEquals(layers, new ResonanceLayers(1, 2, 3));
    }
}
