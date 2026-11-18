package com.runelegend.token;

import com.google.gson.JsonParser;
import com.runelegend.token.effect.AsuraFormEffect;
import com.runelegend.token.effect.EchoEffect;
import com.runelegend.token.effect.ExecutionEchoEffect;
import com.runelegend.token.effect.ImmediateCounterEffect;
import com.runelegend.token.effect.PoisonedArrowEffect;
import com.runelegend.token.effect.ResonanceHealingEffect;
import com.runelegend.token.effect.RetributionEffect;
import com.runelegend.token.effect.SubstituteEffect;
import com.runelegend.token.effect.VenomousTipEffect;
import com.runelegend.token.effect.ArroganceEffect;
import com.runelegend.token.effect.VernalTideEffect;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Player-visible token descriptions report durations in seconds, never raw server ticks. */
class TokenDescriptionTest {
    @Test void fountainDescriptionsUseEveryConfiguredValue() throws IOException {
        var settings = new TokenConfig.FountainSettings(0.35F, 4.5, 3, 6, 7, 8, 12, 21);
        var args = new FountainOfLifeEffect(settings).descriptionArguments();
        for (var language : new String[]{"zh_cn", "en_us"}) {
            try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                    "/assets/runelegend/lang/" + language + ".json"), StandardCharsets.UTF_8)) {
                var format = JsonParser.parseReader(reader).getAsJsonObject()
                        .get("token.runelegend.fountain_of_life.description").getAsString();
                var description = String.format(java.util.Locale.ROOT, format, args);
                for (var value : new String[]{"35%", "4.5", " 3", " 6", " 7", " 8", " 12", " 21"}) {
                    assertTrue(description.contains(value), description);
                }
            }
        }
    }

    @Test void fullBlessingDescriptionsUseEveryConfiguredValue() throws IOException {
        var settings = new TokenConfig.FullBlessingSettings(5, 0.65F, 3.25F, 8, 17);
        var args = new FullBlessingEffect(settings).descriptionArguments();
        for (var language : new String[]{"zh_cn", "en_us"}) {
            try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                    "/assets/runelegend/lang/" + language + ".json"), StandardCharsets.UTF_8)) {
                var format = JsonParser.parseReader(reader).getAsJsonObject()
                        .get("token.runelegend.full_blessing.description").getAsString();
                var description = String.format(java.util.Locale.ROOT, format, args);
                for (var value : new String[]{"65% ", " 5 ", " 3.25 ", " 8 ", " 17 "}) {
                    assertTrue(description.contains(value), description);
                }
            }
        }
    }

    @Test void substituteDescriptionsUseConfiguredThresholdDurationAndCooldown() throws IOException {
        var args = new SubstituteEffect(new TokenConfig.SubstituteSettings(0.35F, 14, 27)).descriptionArguments();
        for (var language : new String[]{"zh_cn", "en_us"}) {
            try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                    "/assets/runelegend/lang/" + language + ".json"), StandardCharsets.UTF_8)) {
                var format = JsonParser.parseReader(reader).getAsJsonObject()
                        .get("token.runelegend.substitute.description").getAsString();
                var description = String.format(java.util.Locale.ROOT, format, args);
                assertTrue(description.contains("35%") && description.contains("14") && description.contains("27"), description);
            }
        }
    }

    @Test void echoDescriptionArgumentsAreSeconds() {
        var arguments = new EchoEffect(TokenConfig.EchoSettings.DEFAULT).descriptionArguments();
        assertEquals("8", arguments[3], "Default 160-tick duration is 8 seconds");
        assertEquals("1", arguments[4], "Default 20-tick interval is 1 second");
    }

    @Test void immediateCounterDescriptionArgumentsAreSeconds() {
        var arguments = new ImmediateCounterEffect(TokenConfig.ImmediateCounterSettings.DEFAULT).descriptionArguments();
        assertEquals("8", arguments[1], "Default 160-tick duration is 8 seconds");
        assertEquals("12", arguments[2], "Default 240-tick cooldown is 12 seconds");
    }

    @Test void poisonedArrowDescriptionUsesConfiguredChanceDurationAndFixedLevel() throws IOException {
        var args = new PoisonedArrowEffect(new TokenConfig.PoisonedArrowSettings(0.45F, 13))
                .descriptionArguments();
        for (var language : new String[]{"zh_cn", "en_us"}) {
            try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                    "/assets/runelegend/lang/" + language + ".json"), StandardCharsets.UTF_8)) {
                var format = JsonParser.parseReader(reader).getAsJsonObject()
                        .get("token.runelegend.poisoned_arrow.description").getAsString();
                var description = String.format(java.util.Locale.ROOT, format, args);
                for (String value : new String[]{"45%", "13", "Ⅱ"}) {
                    assertTrue(description.contains(value), description);
                }
            }
        }
    }

    @Test void venomousTipDescriptionUsesConfiguredChanceDurationAndFixedLevel() throws IOException {
        var args = new VenomousTipEffect(new TokenConfig.VenomousTipSettings(0.45F, 13))
                .descriptionArguments();
        for (var language : new String[]{"zh_cn", "en_us"}) {
            try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                    "/assets/runelegend/lang/" + language + ".json"), StandardCharsets.UTF_8)) {
                var format = JsonParser.parseReader(reader).getAsJsonObject()
                        .get("token.runelegend.venomous_tip.description").getAsString();
                var description = String.format(java.util.Locale.ROOT, format, args);
                for (String value : new String[]{"45%", "13", "Ⅱ"}) {
                    assertTrue(description.contains(value), description);
                }
            }
        }
    }

    @Test void fractionalAndWholeSecondsAreTrimmed() {
        assertEquals("1.5", TokenConfig.seconds(30));
        assertEquals("0.05", TokenConfig.seconds(1));
        assertEquals("50", TokenConfig.seconds(1000));
        assertEquals("0", TokenConfig.seconds(0));
    }

    @Test void asuraDescriptionsUseEveryConfiguredValue() throws IOException {
        var args = new AsuraFormEffect(new TokenConfig.AsuraFormSettings(7, 13, 4.25F, 3, 18.5F, 11))
                .descriptionArguments();
        for (var language : new String[]{"zh_cn", "en_us"}) {
            try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                    "/assets/runelegend/lang/" + language + ".json"), StandardCharsets.UTF_8)) {
                var format = JsonParser.parseReader(reader).getAsJsonObject()
                        .get("token.runelegend.asura_form.description").getAsString();
                var description = String.format(java.util.Locale.ROOT, format, args);
                for (String value : new String[]{"7", "13", "4.25", "3", "18.5", "11"}) {
                    assertTrue(description.contains(" " + value), description);
                }
            }
        }
    }

    @Test void resonanceHealingDescriptionUsesEveryConfiguredValue() throws IOException {
        var args = new ResonanceHealingEffect(new TokenConfig.ResonanceHealingSettings(0.4F, 6.5, 4.5F, 11))
                .descriptionArguments();
        for (var language : new String[]{"zh_cn", "en_us"}) {
            try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                    "/assets/runelegend/lang/" + language + ".json"), StandardCharsets.UTF_8)) {
                var format = JsonParser.parseReader(reader).getAsJsonObject()
                        .get("token.runelegend.resonance_healing.description").getAsString();
                var description = String.format(java.util.Locale.ROOT, format, args);
                for (String value : new String[]{"40%", "6.5", "4.5", "11"}) {
                    assertTrue(description.contains(value), description);
                }
            }
        }
    }

    @Test void vernalTideDescriptionUsesEveryConfiguredValue() throws IOException {
        var args = new VernalTideEffect(new TokenConfig.VernalTideSettings(0.45F, 6.5, 3.5F))
                .descriptionArguments();
        for (var language : new String[]{"zh_cn", "en_us"}) {
            try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                    "/assets/runelegend/lang/" + language + ".json"), StandardCharsets.UTF_8)) {
                var format = JsonParser.parseReader(reader).getAsJsonObject()
                        .get("token.runelegend.vernal_tide.description").getAsString();
                var description = String.format(java.util.Locale.ROOT, format, args);
                for (String value : new String[]{"45%", "6.5", "3.5"}) {
                    assertTrue(description.contains(value), description);
                }
            }
        }
    }

    @Test void arroganceDescriptionUsesEveryConfiguredValue() throws IOException {
        var settings = new TokenConfig.ArroganceSettings(0.73F, 8, 1.25F, 6.5F);
        var args = new ArroganceEffect(settings).descriptionArguments();
        for (var language : new String[]{"zh_cn", "en_us"}) {
            try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                    "/assets/runelegend/lang/" + language + ".json"), StandardCharsets.UTF_8)) {
                var format = JsonParser.parseReader(reader).getAsJsonObject()
                        .get("token.runelegend.arrogance.description").getAsString();
                var description = String.format(java.util.Locale.ROOT, format, args);
                for (String value : new String[]{"73%", "8", "1.25", "6.5"}) {
                    assertTrue(description.contains(value), description);
                }
            }
        }
    }

    @Test void executionEchoDescriptionUsesEveryConfiguredValue() throws IOException {
        var args = new ExecutionEchoEffect(new TokenConfig.ExecutionEchoSettings(0.75F, 3, 7, 13))
                .descriptionArguments();
        for (var language : new String[]{"zh_cn", "en_us"}) {
            try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                    "/assets/runelegend/lang/" + language + ".json"), StandardCharsets.UTF_8)) {
                var format = JsonParser.parseReader(reader).getAsJsonObject()
                        .get("token.runelegend.execution_echo.description").getAsString();
                var description = String.format(java.util.Locale.ROOT, format, args);
                for (String value : new String[]{"0.75", "3", "7", "13"}) {
                    assertTrue(description.contains(value), description);
                }
            }
        }
    }

    @Test void retributionDescriptionUsesConfiguredDurationAndCooldown() throws IOException {
        var args = new RetributionEffect(new TokenConfig.RetributionSettings(7, 13)).descriptionArguments();
        for (var language : new String[]{"zh_cn", "en_us"}) {
            try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                    "/assets/runelegend/lang/" + language + ".json"), StandardCharsets.UTF_8)) {
                var format = JsonParser.parseReader(reader).getAsJsonObject()
                        .get("token.runelegend.retribution.description").getAsString();
                var description = String.format(java.util.Locale.ROOT, format, args);
                assertTrue(description.contains("7 ") && description.contains("13 "), description);
            }
        }
    }

    @Test void noDescriptionTextExposesRawTicks() {
        for (String language : new String[]{"zh_cn", "en_us"}) {
            var stream = getClass().getResourceAsStream("/assets/runelegend/lang/" + language + ".json");
            assertNotNull(stream, language + " lang resource is on the classpath");
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                for (var entry : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                    if (!entry.getKey().endsWith(".description")) continue;
                    assertFalse(entry.getValue().getAsString().contains("tick"),
                            entry.getKey() + " must not expose raw ticks: " + entry.getValue());
                }
            } catch (IOException error) {
                fail("Could not read " + language + " lang resource", error);
            }
        }
    }
}
