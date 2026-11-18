package com.runelegend.client.hud;

import com.runelegend.client.ClientConfig;
import com.runelegend.client.ClientConfig.StaminaHud;
import com.runelegend.client.ClientConfig.TokenHud;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import java.io.IOException;
import java.nio.file.Files;

public final class HudLayoutChecks {
    private HudLayoutChecks() {}

    public static ClientConfig checkConfigAndLayout() {
        var custom = checkConfig();
        var defaults = ClientConfig.DEFAULT;
        expect(new HudLayout(130, 105, 22), HudLayout.place(320, 200, 3, defaults.tokenHud()));
        expect(new HudLayout(160, 75, 34), HudLayout.place(320, 200, 3, custom.tokenHud()));
        expect(new HudLayout(151, 125, 16), HudLayout.place(320, 200, 3, new TokenHud(15, -20, 0)));
        expect(new HudLayout(1, 75, 20), HudLayout.place(57, 200, 3, custom.tokenHud()));
        expect(new HudLayout(0, 0, 16), HudLayout.place(30, 10, 3, custom.tokenHud()));
        expect(new HudLayout(304, 184, 16),
                HudLayout.place(320, 200, 1, new TokenHud(Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE)));

        expect(new HudLayout.StaminaPosition(160, 93), HudLayout.placeStamina(320, 200, true, 61, 9, defaults.staminaHud()));
        expect(new HudLayout.StaminaPosition(116, 90), HudLayout.placeStamina(320, 200, true, 61, 9, custom.staminaHud()));
        expect(new HudLayout.StaminaPosition(116, 105), HudLayout.placeStamina(320, 200, false, 61, 9, custom.staminaHud()));
        expect(new HudLayout.StaminaPosition(289, 191),
                HudLayout.placeStamina(320, 200, true, 61, 9, new StaminaHud(Integer.MAX_VALUE, Integer.MIN_VALUE)));
        return custom;
    }

    public static HudElement renderWith(ClientConfig config) {
        return (graphics, delta) -> RuneLegendHud.draw(graphics, config);
    }

    private static ClientConfig checkConfig() {
        try {
            var path = Files.createTempFile("runelegend-client-config-", ".json");
            try {
                Files.delete(path);
                expect(ClientConfig.DEFAULT, ClientConfig.loadOrGenerate(path));
                String generated = Files.readString(path);
                expect(ClientConfig.DEFAULT, ClientConfig.loadOrGenerate(path));
                expect(generated, Files.readString(path));

                Files.writeString(path, """
                        {"schemaVersion":1,"token_hud":{"offset_y":-18,"future_setting":true}}
                        """);
                expect(new ClientConfig(new TokenHud(0, -18, 6), new StaminaHud(0, 0)),
                        ClientConfig.loadOrGenerate(path));

                for (String invalid : new String[] {
                        "{",
                        "{\"schemaVersion\":2}",
                        "{\"schemaVersion\":1,\"token_hud\":{\"offset_x\":30,\"spacing\":-1}}",
                        "{\"schemaVersion\":1,\"token_hud\":{\"offset_x\":2147483648}}",
                        "{\"schemaVersion\":1,\"stamina_hud\":{\"offset_x\":1.5}}",
                        "{\"schemaVersion\":1,\"stamina_hud\":{\"offset_y\":\"1\"}}"
                }) {
                    Files.writeString(path, invalid);
                    expect(ClientConfig.DEFAULT, ClientConfig.loadOrGenerate(path));
                    expect(invalid, Files.readString(path));
                }

                Files.writeString(path, """
                        {
                          "schemaVersion": 1,
                          "token_hud": {"offset_x": 42, "offset_y": 30, "spacing": 18},
                          "stamina_hud": {"offset_x": -44, "offset_y": 3}
                        }
                        """);
                var custom = ClientConfig.loadOrGenerate(path);
                expect(new ClientConfig(new TokenHud(42, 30, 18), new StaminaHud(-44, 3)), custom);
                return custom;
            } finally {
                Files.deleteIfExists(path);
            }
        } catch (IOException error) {
            throw new AssertionError("Client HUD config fixture failed", error);
        }
    }

    private static void expect(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
