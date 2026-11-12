package com.runelegend.token;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenConfigTest {
    @Test void defaultJsonContainsEveryBuiltInToken() {
        var root = TokenConfig.DEFAULT.toJson();
        var tokens = root.getAsJsonObject("tokens");
        assertEquals(7, tokens.size());
        assertEquals(2, tokens.getAsJsonObject("runelegend:sharpened_tip").size());
        assertEquals(2, tokens.getAsJsonObject("runelegend:soul_siphon").size());
        assertEquals(5, tokens.getAsJsonObject("runelegend:echo").size());
        assertEquals(3, tokens.getAsJsonObject("runelegend:immediate_counter").size());
        var venomous = tokens.getAsJsonObject("runelegend:venomous_tip");
        assertEquals(3, venomous.size());
        assertEquals(0.50F, venomous.get("chance").getAsFloat());
        assertEquals(6, venomous.get("duration_seconds").getAsInt());
        assertEquals(2, venomous.get("level").getAsInt());
        var poisonedArrow = tokens.getAsJsonObject("runelegend:poisoned_arrow");
        assertEquals(3, poisonedArrow.size());
        assertEquals(0.50F, poisonedArrow.get("chance").getAsFloat());
        assertEquals(12, poisonedArrow.get("duration_seconds").getAsInt());
        assertEquals(2, poisonedArrow.get("level").getAsInt());
        var asura = tokens.getAsJsonObject("runelegend:asura_form");
        assertEquals(6, asura.size());
        assertEquals(3, asura.get("required_kills").getAsInt());
        assertEquals(12, asura.get("duration_seconds").getAsInt());
        assertEquals(4F, asura.get("bonus_damage").getAsFloat());
        assertEquals(2, asura.get("speed_level").getAsInt());
        assertEquals(16F, asura.get("temporary_health").getAsFloat());
        assertEquals(9, asura.get("cooldown_seconds").getAsInt());
    }

    @Test void codecRoundTripsEverySetting() {
        var config = new TokenConfig(
                new TokenConfig.SharpenedTipSettings(0.25F, 4.0F),
                new TokenConfig.SoulSiphonSettings(0.75F, 3.0F),
                new TokenConfig.EchoSettings(0.33F, 6.5, 4.0F, 80, 10),
                new TokenConfig.ImmediateCounterSettings(5.0F, 20, 30),
                new TokenConfig.VenomousTipSettings(0.35F, 8, 3),
                new TokenConfig.PoisonedArrowSettings(0.45F, 10, 4),
                new TokenConfig.AsuraFormSettings(2, 1, 5.5F, 3, 30.5F, 0));
        var encoded = TokenConfig.DEFAULT.toJson();
        // Encode each section through its typed codec to prove all fields survive a round trip.
        var tokens = encoded.getAsJsonObject("tokens");
        tokens.add("runelegend:sharpened_tip", encode(TokenConfig.SharpenedTipSettings.CODEC, config.sharpenedTip()));
        tokens.add("runelegend:soul_siphon", encode(TokenConfig.SoulSiphonSettings.CODEC, config.soulSiphon()));
        tokens.add("runelegend:echo", encode(TokenConfig.EchoSettings.CODEC, config.echo()));
        tokens.add("runelegend:immediate_counter", encode(TokenConfig.ImmediateCounterSettings.CODEC, config.immediateCounter()));
        tokens.add("runelegend:venomous_tip", encode(TokenConfig.VenomousTipSettings.CODEC, config.venomousTip()));
        tokens.add("runelegend:poisoned_arrow", encode(TokenConfig.PoisonedArrowSettings.CODEC, config.poisonedArrow()));
        tokens.add("runelegend:asura_form", config.toJson().getAsJsonObject("tokens").get("runelegend:asura_form"));
        assertEquals(config, TokenConfig.fromJson(encoded));
    }

    @Test void boundedSettingsRejectInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.SharpenedTipSettings(1.01F, 1));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.SharpenedTipSettings(0.5F, -1));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.SoulSiphonSettings(-0.1F, 1));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.EchoSettings(0.5F, 0, 1, 20, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.EchoSettings(0.5F, 5, -1, 20, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.EchoSettings(0.5F, 5, 1, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.EchoSettings(0.5F, 5, 1, 20, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ImmediateCounterSettings(-1, 20, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ImmediateCounterSettings(1, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ImmediateCounterSettings(1, 20, -1));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.VenomousTipSettings(-0.1F, 6, 2));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.VenomousTipSettings(1.1F, 6, 2));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.VenomousTipSettings(0.5F, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.VenomousTipSettings(0.5F, 6, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.VenomousTipSettings(0.5F, 6, 257));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.PoisonedArrowSettings(-0.1F, 12, 2));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.PoisonedArrowSettings(1.1F, 12, 2));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.PoisonedArrowSettings(Float.NaN, 12, 2));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.PoisonedArrowSettings(0.5F, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.PoisonedArrowSettings(0.5F, 12, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.PoisonedArrowSettings(0.5F, 12, 257));
    }

    @Test void poisonedArrowConvertsSecondsAndOneBasedLevel() {
        var settings = new TokenConfig.PoisonedArrowSettings(0.5F, 12, 2);
        assertEquals(240, settings.durationTicks());
        assertEquals(1, settings.amplifier());
        assertEquals(20, new TokenConfig.PoisonedArrowSettings(0.5F, 1, 1).durationTicks());
        assertEquals(0, new TokenConfig.PoisonedArrowSettings(0.5F, 1, 1).amplifier());
    }

    @Test void venomousTipConvertsSecondsAndOneBasedLevel() {
        var settings = new TokenConfig.VenomousTipSettings(0.5F, 6, 2);
        assertEquals(120, settings.durationTicks());
        assertEquals(1, settings.amplifier());
        assertEquals(20, new TokenConfig.VenomousTipSettings(0.5F, 1, 1).durationTicks());
        assertEquals(0, new TokenConfig.VenomousTipSettings(0.5F, 1, 1).amplifier());
    }

    @Test void invalidRootUsesAllDefaultsAndInvalidTokenUsesOnlyThatDefault() {
        assertEquals(TokenConfig.DEFAULT, TokenConfig.fromJson(new JsonObject()));

        var root = TokenConfig.DEFAULT.toJson();
        root.addProperty("schemaVersion", 99);
        assertEquals(TokenConfig.DEFAULT, TokenConfig.fromJson(root));

        root.addProperty("schemaVersion", 1);
        root.getAsJsonObject("tokens").add("runelegend:sharpened_tip", JsonParser.parseString("{\"chance\":2}"));
        var parsed = TokenConfig.fromJson(root);
        assertEquals(TokenConfig.SharpenedTipSettings.DEFAULT, parsed.sharpenedTip());
        assertEquals(TokenConfig.EchoSettings.DEFAULT, parsed.echo());
    }

    @Test void knownTokenMayOmitFields() {
        var root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.add("tokens", JsonParser.parseString("{\"runelegend:sharpened_tip\":{\"chance\":1}}").getAsJsonObject());
        var parsed = TokenConfig.fromJson(root);
        assertEquals(new TokenConfig.SharpenedTipSettings(1, 2), parsed.sharpenedTip());
        assertEquals(TokenConfig.AsuraFormSettings.DEFAULT, parsed.asuraForm());
    }

    @Test void invalidAsuraSettingsFallBackWithoutDiscardingOtherTokens() {
        var root = TokenConfig.DEFAULT.toJson();
        var tokens = root.getAsJsonObject("tokens");
        tokens.add("runelegend:sharpened_tip", JsonParser.parseString("{\"chance\":1}"));
        for (String invalid : new String[]{"\"required_kills\":1.5", "\"required_kills\":0",
                "\"duration_seconds\":107374183", "\"duration_seconds\":0", "\"bonus_damage\":1e300",
                "\"bonus_damage\":-1", "\"speed_level\":0", "\"temporary_health\":0.5",
                "\"temporary_health\":1025", "\"cooldown_seconds\":-1"}) {
            tokens.add("runelegend:asura_form", JsonParser.parseString("{" + invalid + "}"));
            var parsed = TokenConfig.fromJson(root);
            assertEquals(TokenConfig.AsuraFormSettings.DEFAULT, parsed.asuraForm(), invalid);
            assertEquals(1F, parsed.sharpenedTip().chance());
        }
    }

    private static <T> JsonElement encode(com.mojang.serialization.Codec<T> codec, T value) {
        return codec.encodeStart(JsonOps.INSTANCE, value).result().orElseThrow();
    }
}
