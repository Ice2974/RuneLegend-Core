package com.runelegend.token;

import com.google.gson.JsonParser;
import com.runelegend.token.effect.AsuraFormEffect;
import com.runelegend.token.effect.EchoEffect;
import com.runelegend.token.effect.ImmediateCounterEffect;
import com.runelegend.token.effect.PoisonedArrowEffect;
import com.runelegend.token.effect.VenomousTipEffect;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Player-visible token descriptions report durations in seconds, never raw server ticks. */
class TokenDescriptionTest {
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
