package com.runelegend.token;

import com.google.gson.JsonParser;
import com.runelegend.token.effect.EchoEffect;
import com.runelegend.token.effect.ImmediateCounterEffect;
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

    @Test void fractionalAndWholeSecondsAreTrimmed() {
        assertEquals("1.5", TokenConfig.seconds(30));
        assertEquals("0.05", TokenConfig.seconds(1));
        assertEquals("50", TokenConfig.seconds(1000));
        assertEquals("0", TokenConfig.seconds(0));
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
