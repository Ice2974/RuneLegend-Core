package com.runelegend.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.runelegend.RuneLegendMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public record ClientConfig(TokenHud tokenHud, StaminaHud staminaHud) {
    private static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).setPrettyPrinting().create();
    public static final ClientConfig DEFAULT = new ClientConfig(new TokenHud(0, 0, 6), new StaminaHud(0, 0));

    public record TokenHud(int offsetX, int offsetY, int spacing) {
        public TokenHud {
            if (spacing < 0) throw new IllegalArgumentException("token_hud.spacing must be non-negative");
        }
    }

    public record StaminaHud(int offsetX, int offsetY) {}

    public static ClientConfig load() {
        return loadFromDirectory(FabricLoader.getInstance().getConfigDir());
    }

    public static ClientConfig loadFromDirectory(Path configDirectory) {
        return loadOrGenerate(configDirectory.resolve("runelegend-core/client.json"));
    }

    public static ClientConfig loadOrGenerate(Path path) {
        path = path.toAbsolutePath();
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                try {
                    Files.writeString(path, GSON.toJson(DEFAULT.toJson()) + "\n",
                            StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                    return DEFAULT;
                } catch (FileAlreadyExistsException ignored) {
                    // Another client may have created the file in this instance.
                }
            }
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return fromJson(GSON.fromJson(reader, JsonObject.class));
            }
        } catch (IOException | RuntimeException error) {
            RuneLegendMod.LOGGER.error("Could not load RuneLegend client config {}; using defaults", path, error);
            return DEFAULT;
        }
    }

    private static ClientConfig fromJson(JsonObject root) {
        if (root == null || integer(root, "schemaVersion", 0) != VERSION) {
            throw new IllegalArgumentException("Expected schemaVersion " + VERSION);
        }
        var token = section(root, "token_hud");
        var stamina = section(root, "stamina_hud");
        return new ClientConfig(
                new TokenHud(integer(token, "offset_x", DEFAULT.tokenHud.offsetX),
                        integer(token, "offset_y", DEFAULT.tokenHud.offsetY),
                        integer(token, "spacing", DEFAULT.tokenHud.spacing)),
                new StaminaHud(integer(stamina, "offset_x", DEFAULT.staminaHud.offsetX),
                        integer(stamina, "offset_y", DEFAULT.staminaHud.offsetY)));
    }

    private static JsonObject section(JsonObject root, String key) {
        if (!root.has(key)) return new JsonObject();
        var value = root.get(key);
        if (!value.isJsonObject()) throw new IllegalArgumentException(key + " must be an object");
        return value.getAsJsonObject();
    }

    private static int integer(JsonObject section, String key, int fallback) {
        if (!section.has(key)) return fallback;
        var value = section.get(key);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            try {
                return value.getAsBigDecimal().intValueExact();
            } catch (ArithmeticException | NumberFormatException error) {
                throw new IllegalArgumentException(key + " must be a 32-bit integer", error);
            }
        }
        throw new IllegalArgumentException(key + " must be a 32-bit integer");
    }

    private JsonObject toJson() {
        var root = new JsonObject();
        root.addProperty("schemaVersion", VERSION);
        var token = new JsonObject();
        token.addProperty("offset_x", tokenHud.offsetX);
        token.addProperty("offset_y", tokenHud.offsetY);
        token.addProperty("spacing", tokenHud.spacing);
        root.add("token_hud", token);
        var stamina = new JsonObject();
        stamina.addProperty("offset_x", staminaHud.offsetX);
        stamina.addProperty("offset_y", staminaHud.offsetY);
        root.add("stamina_hud", stamina);
        return root;
    }
}
