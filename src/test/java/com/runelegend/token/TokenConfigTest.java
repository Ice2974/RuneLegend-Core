package com.runelegend.token;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenConfigTest {
    @Test void defaultJsonContainsEveryBuiltInToken() {
        var tokens = TokenConfig.DEFAULT.toJson();
        assertEquals(14, tokens.size());
        assertEquals(2, tokens.getAsJsonObject("runelegend:soul_siphon").size());
        assertEquals(5, tokens.getAsJsonObject("runelegend:echo").size());
        assertEquals(3, tokens.getAsJsonObject("runelegend:immediate_counter").size());
        var venomous = tokens.getAsJsonObject("runelegend:venomous_tip");
        assertEquals(2, venomous.size());
        assertEquals(0.50F, venomous.get("chance").getAsFloat());
        assertEquals(6, venomous.get("duration_seconds").getAsInt());
        assertFalse(venomous.has("level"), "Venomous Tip poison level is no longer configurable");
        var poisonedArrow = tokens.getAsJsonObject("runelegend:poisoned_arrow");
        assertEquals(2, poisonedArrow.size());
        assertEquals(0.50F, poisonedArrow.get("chance").getAsFloat());
        assertEquals(12, poisonedArrow.get("duration_seconds").getAsInt());
        assertFalse(poisonedArrow.has("level"), "Poisoned Arrow poison level is no longer configurable");
        var asura = tokens.getAsJsonObject("runelegend:asura_form");
        assertEquals(6, asura.size());
        assertEquals(3, asura.get("required_kills").getAsInt());
        assertEquals(12, asura.get("duration_seconds").getAsInt());
        assertEquals(4F, asura.get("bonus_damage").getAsFloat());
        assertEquals(2, asura.get("speed_level").getAsInt());
        assertEquals(16F, asura.get("temporary_health").getAsFloat());
        assertEquals(9, asura.get("cooldown_seconds").getAsInt());
        var resonance = tokens.getAsJsonObject("runelegend:resonance_healing");
        assertEquals(4, resonance.size());
        assertEquals(0.5F, resonance.get("health_threshold").getAsFloat());
        assertEquals(5.0, resonance.get("radius").getAsDouble());
        assertEquals(6.0F, resonance.get("heal_amount").getAsFloat());
        assertEquals(10, resonance.get("cooldown_seconds").getAsInt());
        var vernal = tokens.getAsJsonObject("runelegend:vernal_tide");
        assertEquals(3, vernal.size());
        assertEquals(0.5F, vernal.get("chance").getAsFloat());
        assertEquals(5.0, vernal.get("radius").getAsDouble());
        assertEquals(3.0F, vernal.get("damage").getAsFloat());
        var arrogance = tokens.getAsJsonObject("runelegend:arrogance");
        assertEquals(4, arrogance.size());
        assertEquals(0.6F, arrogance.get("health_threshold").getAsFloat());
        assertEquals(1, arrogance.get("kills_per_increase").getAsInt());
        assertEquals(1.0F, arrogance.get("damage_per_increase").getAsFloat());
        assertEquals(5.0F, arrogance.get("max_bonus_damage").getAsFloat());
        var executionEcho = tokens.getAsJsonObject("runelegend:execution_echo");
        assertEquals(4, executionEcho.size());
        assertEquals(1.0F, executionEcho.get("decay_per_interval").getAsFloat());
        assertEquals(1, executionEcho.get("decay_interval_seconds").getAsInt());
        assertEquals(10, executionEcho.get("cooldown_seconds").getAsInt());
        assertEquals(10, executionEcho.get("max_recorded_damage").getAsInt());
        var retribution = tokens.getAsJsonObject("runelegend:retribution");
        assertEquals(2, retribution.size());
        assertEquals(5, retribution.get("duration_seconds").getAsInt());
        assertEquals(10, retribution.get("cooldown_seconds").getAsInt());
        var substitute = tokens.getAsJsonObject("runelegend:substitute");
        assertEquals(3, substitute.size());
        assertEquals(0.5F, substitute.get("health_threshold").getAsFloat());
        assertEquals(20, substitute.get("duration_seconds").getAsInt());
        assertEquals(40, substitute.get("cooldown_seconds").getAsInt());
        var fullBlessing = tokens.getAsJsonObject("runelegend:full_blessing");
        assertEquals(5, fullBlessing.size());
        assertEquals(3, fullBlessing.get("blessings_per_trigger").getAsInt());
        assertEquals(0.5F, fullBlessing.get("health_threshold").getAsFloat());
        assertEquals(4.0F, fullBlessing.get("heal_amount").getAsFloat());
        assertEquals(6, fullBlessing.get("max_stored_blessings").getAsInt());
        assertEquals(20, fullBlessing.get("cooldown_seconds").getAsInt());
        var fountain = tokens.getAsJsonObject("runelegend:fountain_of_life");
        assertEquals(8, fountain.size());
        assertEquals(0.4F, fountain.get("health_threshold").getAsFloat());
        assertEquals(3.0, fountain.get("radius").getAsDouble());
        assertEquals(2, fountain.get("regeneration_level").getAsInt());
        assertEquals(2, fountain.get("resistance_level").getAsInt());
        assertEquals(1, fountain.get("regeneration_linger_seconds").getAsInt());
        assertEquals(5, fountain.get("resistance_linger_seconds").getAsInt());
        assertEquals(10, fountain.get("duration_seconds").getAsInt());
        assertEquals(10, fountain.get("cooldown_seconds").getAsInt());
    }

    @Test void codecRoundTripsEverySetting() {
        var config = new TokenConfig(
                new TokenConfig.SoulSiphonSettings(0.75F, 3.0F),
                new TokenConfig.EchoSettings(0.33F, 6.5, 4.0F, 80, 10),
                new TokenConfig.ImmediateCounterSettings(5.0F, 20, 30),
                new TokenConfig.VenomousTipSettings(0.35F, 8),
                new TokenConfig.PoisonedArrowSettings(0.45F, 10),
                new TokenConfig.AsuraFormSettings(2, 1, 5.5F, 3, 30.5F, 0),
                new TokenConfig.ResonanceHealingSettings(0.4F, 6.5, 4.5F, 11),
                new TokenConfig.VernalTideSettings(0.25F, 4.5, 2.0F),
                new TokenConfig.ArroganceSettings(0.7F, 3, 1.5F, 4.25F),
                new TokenConfig.ExecutionEchoSettings(0.75F, 2, 7, 13),
                new TokenConfig.RetributionSettings(7, 0),
                new TokenConfig.SubstituteSettings(0.4F, 12, 7),
                new TokenConfig.FullBlessingSettings(5, 0.6F, 2.5F, 0, 11),
                new TokenConfig.FountainSettings(0.3F, 6.5, 3, 4, 0, 8, 19, 0));
        assertEquals(config, TokenConfig.fromJson(config.toJson()));
    }

    @Test void fountainUsesDefaultsForAnAbsentOrInvalidSection() {
        var tokens = new JsonObject();
        var defaults = TokenConfig.FountainSettings.DEFAULT;
        assertEquals(defaults, TokenConfig.fromJson(tokens).fountainOfLife());
        tokens.add("runelegend:fountain_of_life", JsonParser.parseString("{\"radius\":7,\"resistance_linger_seconds\":0}"));
        assertEquals(new TokenConfig.FountainSettings(0.4F, 7, 2, 2, 1, 0, 10, 10), TokenConfig.fromJson(tokens).fountainOfLife());
        for (var invalid : new String[]{"\"regeneration_level\":0", "\"resistance_level\":2.5", "\"resistance_level\":257",
                "\"radius\":0", "\"health_threshold\":1.1", "\"duration_seconds\":0",
                "\"regeneration_linger_seconds\":107374183", "\"resistance_linger_seconds\":-1", "\"cooldown_seconds\":-1"}) {
            tokens.add("runelegend:fountain_of_life", JsonParser.parseString("{\"unused\":true," + invalid + "}"));
            assertEquals(defaults, TokenConfig.fromJson(tokens).fountainOfLife(), invalid);
        }
    }

    @Test void fullBlessingUsesDefaultFieldsAndRejectsInvalidSections() {
        var tokens = new JsonObject();
        var defaults = TokenConfig.FullBlessingSettings.DEFAULT;
        assertEquals(defaults, TokenConfig.fromJson(tokens).fullBlessing());

        tokens.add("runelegend:full_blessing", JsonParser.parseString("{\"blessings_per_trigger\":5,\"cooldown_seconds\":0}"));
        assertEquals(new TokenConfig.FullBlessingSettings(5, 0.5F, 4F, 6, 0), TokenConfig.fromJson(tokens).fullBlessing());
        for (var invalid : new String[]{"\"blessings_per_trigger\":0", "\"blessings_per_trigger\":2.5",
                "\"blessings_per_trigger\":1025", "\"health_threshold\":1.01", "\"health_threshold\":-0.1",
                "\"heal_amount\":0", "\"heal_amount\":1e100", "\"max_stored_blessings\":-1", "\"max_stored_blessings\":1.5",
                "\"max_stored_blessings\":1025", "\"cooldown_seconds\":1.5", "\"cooldown_seconds\":107374183"}) {
            tokens.add("runelegend:full_blessing", JsonParser.parseString("{\"unused\":true," + invalid + "}"));
            assertEquals(defaults, TokenConfig.fromJson(tokens).fullBlessing(), invalid);
        }
        tokens.add("runelegend:full_blessing", JsonParser.parseString("{\"blessings_per_trigger\":7,\"heal_amount\":0}"));
        assertEquals(defaults, TokenConfig.fromJson(tokens).fullBlessing(), "One invalid field resets all five fields");
    }

    @Test void substituteUsesDefaultFieldsAndRejectsInvalidSections() {
        var tokens = new JsonObject();
        assertEquals(TokenConfig.SubstituteSettings.DEFAULT, TokenConfig.fromJson(tokens).substitute());
        for (var invalid : new String[]{
                "[]", "{\"health_threshold\":-0.1,\"cooldown_seconds\":7}", "{\"health_threshold\":1.01}",
                "{\"health_threshold\":\"half\"}", "{\"duration_seconds\":0,\"cooldown_seconds\":7}",
                "{\"duration_seconds\":1.5}", "{\"duration_seconds\":107374183}", "{\"duration_seconds\":\"short\"}",
                "{\"cooldown_seconds\":-1}", "{\"cooldown_seconds\":0.5}",
                "{\"cooldown_seconds\":107374183}", "{\"cooldown_seconds\":\"fast\"}"}) {
            tokens.add("runelegend:substitute", JsonParser.parseString(invalid));
            assertEquals(TokenConfig.SubstituteSettings.DEFAULT, TokenConfig.fromJson(tokens).substitute(), invalid);
        }
        tokens.add("runelegend:substitute", JsonParser.parseString("{\"health_threshold\":0.4,\"cooldown_seconds\":0}"));
        assertEquals(new TokenConfig.SubstituteSettings(0.4F, 20, 0), TokenConfig.fromJson(tokens).substitute());
    }

    @Test void missingAndInvalidRetributionSettingsUseDefaults() {
        var tokens = JsonParser.parseString("""
                {"runelegend:soul_siphon":{"chance":0.2}}
                """).getAsJsonObject();
        var defaults = TokenConfig.fromJson(tokens);
        assertEquals(TokenConfig.RetributionSettings.DEFAULT, defaults.retribution());
        for (var invalid : new String[]{"[]", "{\"duration_seconds\":0,\"cooldown_seconds\":1}",
                "{\"duration_seconds\":1.5}", "{\"duration_seconds\":107374183}",
                "{\"cooldown_seconds\":-1,\"duration_seconds\":1}", "{\"cooldown_seconds\":0.5}",
                "{\"cooldown_seconds\":107374183}", "{\"cooldown_seconds\":\"fast\"}"}) {
            tokens.add("runelegend:retribution", JsonParser.parseString(invalid));
            assertEquals(defaults, TokenConfig.fromJson(tokens), invalid);
        }
        tokens.add("runelegend:retribution", JsonParser.parseString("{\"cooldown_seconds\":0}"));
        var settings = TokenConfig.fromJson(tokens).retribution();
        assertEquals(new TokenConfig.RetributionSettings(5, 0), settings);
        assertEquals(100, settings.durationTicks());
        assertEquals(0, settings.cooldownTicks());
    }

    @Test void missingOrInvalidArroganceSettingsPreserveOtherTokens() {
        var tokens = JsonParser.parseString("""
                {"runelegend:soul_siphon":{"chance":0.2}}
                """).getAsJsonObject();
        var oldConfig = TokenConfig.fromJson(tokens);
        assertEquals(TokenConfig.ArroganceSettings.DEFAULT, oldConfig.arrogance());
        for (var invalid : new String[]{
                "\"health_threshold\":1.1", "\"kills_per_increase\":0", "\"kills_per_increase\":2.5",
                "\"kills_per_increase\":2147483648", "\"damage_per_increase\":0",
                "\"damage_per_increase\":\"garbage\"", "\"max_bonus_damage\":-1", "\"max_bonus_damage\":1e50"}) {
            tokens.add("runelegend:arrogance", JsonParser.parseString("{\"health_threshold\":0.8," + invalid + "}"));
            assertEquals(oldConfig, TokenConfig.fromJson(tokens), invalid);
        }
        tokens.add("runelegend:arrogance", JsonParser.parseString("{\"damage_per_increase\":0.5}"));
        assertEquals(new TokenConfig.ArroganceSettings(0.6F, 1, 0.5F, 5.0F), TokenConfig.fromJson(tokens).arrogance());
    }

    @Test void boundedSettingsRejectInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.SoulSiphonSettings(-0.1F, 1));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.EchoSettings(0.5F, 0, 1, 20, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.EchoSettings(0.5F, 5, -1, 20, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.EchoSettings(0.5F, 5, 1, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.EchoSettings(0.5F, 5, 1, 20, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ImmediateCounterSettings(-1, 20, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ImmediateCounterSettings(1, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ImmediateCounterSettings(1, 20, -1));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.VenomousTipSettings(-0.1F, 6));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.VenomousTipSettings(1.1F, 6));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.VenomousTipSettings(Float.NaN, 6));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.VenomousTipSettings(0.5F, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.PoisonedArrowSettings(-0.1F, 12));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.PoisonedArrowSettings(1.1F, 12));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.PoisonedArrowSettings(Float.NaN, 12));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.PoisonedArrowSettings(0.5F, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ResonanceHealingSettings(-0.1F, 5, 6, 15));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ResonanceHealingSettings(1.1F, 5, 6, 15));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ResonanceHealingSettings(Float.NaN, 5, 6, 15));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ResonanceHealingSettings(0.5F, 0, 6, 15));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ResonanceHealingSettings(0.5F, -1, 6, 15));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ResonanceHealingSettings(0.5F, Double.NaN, 6, 15));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ResonanceHealingSettings(0.5F, 5, -1, 15));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ResonanceHealingSettings(0.5F, 5, Float.NaN, 15));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ResonanceHealingSettings(0.5F, 5, 6, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new TokenConfig.ResonanceHealingSettings(0.5F, 5, 6, Integer.MAX_VALUE / 20 + 1));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.VernalTideSettings(-0.1F, 5, 3));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.VernalTideSettings(1.1F, 5, 3));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.VernalTideSettings(Float.NaN, 5, 3));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.VernalTideSettings(0.5F, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.VernalTideSettings(0.5F, -1, 3));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.VernalTideSettings(0.5F, Double.NaN, 3));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.VernalTideSettings(0.5F, 5, -1));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.VernalTideSettings(0.5F, 5, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ExecutionEchoSettings(0, 1, 10, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ExecutionEchoSettings(0.001F, 1, 10, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ExecutionEchoSettings(Float.NaN, 1, 10, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ExecutionEchoSettings(2, 0, 10, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ExecutionEchoSettings(2, 1, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ExecutionEchoSettings(2, 1, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ExecutionEchoSettings(2, 1, 10, 1025));
    }

    @Test void executionEchoDefaultsConvertSecondsToTicks() {
        var settings = TokenConfig.ExecutionEchoSettings.DEFAULT;
        assertEquals(1.0F, settings.decayPerInterval());
        assertEquals(20, settings.decayIntervalTicks());
        assertEquals(200, settings.cooldownTicks());
        assertEquals(10, settings.maxRecordedDamage());
    }

    @Test void invalidExecutionEchoSettingsFallBackWithoutDiscardingOtherTokens() {
        var tokens = TokenConfig.DEFAULT.toJson();
        tokens.add("runelegend:soul_siphon", JsonParser.parseString("{\"chance\":1}"));
        for (String invalid : new String[]{"\"decay_per_interval\":0", "\"decay_per_interval\":0.001",
                "\"decay_per_interval\":2000", "\"decay_interval_seconds\":0", "\"decay_interval_seconds\":1.5",
                "\"cooldown_seconds\":-1", "\"cooldown_seconds\":" + (Integer.MAX_VALUE / 20 + 1),
                "\"max_recorded_damage\":0", "\"max_recorded_damage\":1025", "\"max_recorded_damage\":1.5"}) {
            tokens.add("runelegend:execution_echo", JsonParser.parseString("{" + invalid + "}"));
            var parsed = TokenConfig.fromJson(tokens);
            assertEquals(TokenConfig.ExecutionEchoSettings.DEFAULT, parsed.executionEcho(), invalid);
            assertEquals(1F, parsed.soulSiphon().chance());
        }
    }

    @Test void executionEchoMissingFieldsUseTheirOwnDefaults() {
        var tokens = JsonParser.parseString("{\"runelegend:execution_echo\":{\"max_recorded_damage\":12}}")
                .getAsJsonObject();
        assertEquals(new TokenConfig.ExecutionEchoSettings(1.0F, 1, 10, 12), TokenConfig.fromJson(tokens).executionEcho());
    }

    @Test void poisonedArrowConvertsSeconds() {
        assertEquals(240, new TokenConfig.PoisonedArrowSettings(0.5F, 12).durationTicks());
        assertEquals(20, new TokenConfig.PoisonedArrowSettings(0.5F, 1).durationTicks());
    }

    @Test void venomousTipConvertsSeconds() {
        assertEquals(120, new TokenConfig.VenomousTipSettings(0.5F, 6).durationTicks());
        assertEquals(20, new TokenConfig.VenomousTipSettings(0.5F, 1).durationTicks());
    }

    @Test void venomousTipIgnoresRemovedLevelSetting() {
        var tokens = TokenConfig.DEFAULT.toJson();
        tokens.add("runelegend:venomous_tip",
                JsonParser.parseString("{\"chance\":0.4,\"duration_seconds\":8,\"level\":3}"));
        assertEquals(new TokenConfig.VenomousTipSettings(0.4F, 8), TokenConfig.fromJson(tokens).venomousTip());
    }

    @Test void poisonedArrowIgnoresRemovedLevelSetting() {
        var tokens = TokenConfig.DEFAULT.toJson();
        tokens.add("runelegend:poisoned_arrow",
                JsonParser.parseString("{\"chance\":0.4,\"duration_seconds\":8,\"level\":3}"));
        assertEquals(new TokenConfig.PoisonedArrowSettings(0.4F, 8), TokenConfig.fromJson(tokens).poisonedArrow());
    }

    @Test void emptyTokensUseDefaultsAndInvalidTokenUsesOnlyThatDefault() {
        assertEquals(TokenConfig.DEFAULT, TokenConfig.fromJson(new JsonObject()));

        var tokens = TokenConfig.DEFAULT.toJson();
        tokens.add("runelegend:soul_siphon", JsonParser.parseString("{\"chance\":2}"));
        var parsed = TokenConfig.fromJson(tokens);
        assertEquals(TokenConfig.SoulSiphonSettings.DEFAULT, parsed.soulSiphon());
        assertEquals(TokenConfig.EchoSettings.DEFAULT, parsed.echo());
    }

    @Test void knownTokenMayOmitFields() {
        var tokens = JsonParser.parseString("{\"runelegend:soul_siphon\":{\"chance\":1}}").getAsJsonObject();
        var parsed = TokenConfig.fromJson(tokens);
        assertEquals(new TokenConfig.SoulSiphonSettings(1, 2), parsed.soulSiphon());
        assertEquals(TokenConfig.AsuraFormSettings.DEFAULT, parsed.asuraForm());
    }

    @Test void invalidAsuraSettingsFallBackWithoutDiscardingOtherTokens() {
        var tokens = TokenConfig.DEFAULT.toJson();
        tokens.add("runelegend:soul_siphon", JsonParser.parseString("{\"chance\":1}"));
        for (String invalid : new String[]{"\"required_kills\":1.5", "\"required_kills\":0",
                "\"duration_seconds\":107374183", "\"duration_seconds\":0", "\"bonus_damage\":1e300",
                "\"bonus_damage\":-1", "\"speed_level\":0", "\"temporary_health\":0.5",
                "\"temporary_health\":1025", "\"cooldown_seconds\":-1"}) {
            tokens.add("runelegend:asura_form", JsonParser.parseString("{" + invalid + "}"));
            var parsed = TokenConfig.fromJson(tokens);
            assertEquals(TokenConfig.AsuraFormSettings.DEFAULT, parsed.asuraForm(), invalid);
            assertEquals(1F, parsed.soulSiphon().chance());
        }
    }

    @Test void invalidResonanceSettingsFallBackWithoutDiscardingOtherTokens() {
        var tokens = TokenConfig.DEFAULT.toJson();
        tokens.add("runelegend:soul_siphon", JsonParser.parseString("{\"chance\":1}"));
        for (String invalid : new String[]{"\"health_threshold\":1.5",
                "\"radius\":0", "\"radius\":-1", "\"heal_amount\":-1",
                "\"cooldown_seconds\":-1", "\"cooldown_seconds\":1.5",
                "\"cooldown_seconds\":" + (Integer.MAX_VALUE / 20 + 1)}) {
            tokens.add("runelegend:resonance_healing", JsonParser.parseString("{" + invalid + "}"));
            var parsed = TokenConfig.fromJson(tokens);
            assertEquals(TokenConfig.ResonanceHealingSettings.DEFAULT, parsed.resonanceHealing(), invalid);
            assertEquals(1F, parsed.soulSiphon().chance());
        }
    }

    @Test void invalidVernalTideSettingsFallBackWithoutDiscardingOtherTokens() {
        var tokens = TokenConfig.DEFAULT.toJson();
        tokens.add("runelegend:soul_siphon", JsonParser.parseString("{\"chance\":1}"));
        for (String invalid : new String[]{"\"chance\":1.5", "\"chance\":-0.1",
                "\"radius\":0", "\"radius\":-1", "\"damage\":-1"}) {
            tokens.add("runelegend:vernal_tide", JsonParser.parseString("{" + invalid + "}"));
            var parsed = TokenConfig.fromJson(tokens);
            assertEquals(TokenConfig.VernalTideSettings.DEFAULT, parsed.vernalTide(), invalid);
            assertEquals(1F, parsed.soulSiphon().chance());
        }
    }
}
