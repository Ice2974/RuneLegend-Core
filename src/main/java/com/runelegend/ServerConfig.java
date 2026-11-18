package com.runelegend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.runelegend.combat.GuardConfig;
import com.runelegend.player.PlayerStateConfig;
import com.runelegend.token.TokenConfig;
import com.runelegend.token.TokenExtensions;
import com.runelegend.token.TokenRegistry;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Instance settings loaded once when a logical server starts. */
record ServerConfig(GuardConfig guard, PlayerStateConfig playerStates, TokenConfig tokens, JsonObject extensionSettings) {
    private static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).setPrettyPrinting().create();
    static final ServerConfig DEFAULT = new ServerConfig(
            GuardConfig.DEFAULT, PlayerStateConfig.DEFAULT, TokenConfig.DEFAULT, new JsonObject());

    static ServerConfig load(TokenExtensions extensions) {
        return loadOrGenerate(FabricLoader.getInstance().getConfigDir().resolve("runelegend-core/server.json"), extensions);
    }

    static ServerConfig loadOrGenerate(Path path) {
        return loadOrGenerate(path, TokenExtensions.EMPTY);
    }

    private static ServerConfig loadOrGenerate(Path path, TokenExtensions extensions) {
        path = path.toAbsolutePath();
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                try {
                    Files.writeString(path, GSON.toJson(DEFAULT.toJson(extensions)) + "\n",
                            StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                    return DEFAULT;
                } catch (FileAlreadyExistsException ignored) {
                    // Another server may have created the file in this instance.
                }
            }
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return fromJson(GSON.fromJson(reader, JsonObject.class), extensions);
            }
        } catch (IOException | RuntimeException error) {
            RuneLegendMod.LOGGER.error("Could not load RuneLegend server config {}; using defaults", path, error);
            return DEFAULT;
        }
    }

    private static ServerConfig fromJson(JsonObject root, TokenExtensions extensions) {
        if (root == null || !Integer.toString(VERSION).equals(String.valueOf(root.get("schemaVersion")))) {
            throw new IllegalArgumentException("Expected integer schemaVersion " + VERSION);
        }
        var tokens = section(root, "tokens");
        return new ServerConfig(
                GuardConfig.fromJson(section(root, "guard")),
                PlayerStateConfig.fromJson(section(root, "player_states")),
                TokenConfig.fromJson(tokens, extensions), extensions.readSections(tokens));
    }

    TokenRegistry tokenRegistry(TokenExtensions extensions) {
        return TokenRegistry.create(tokens, extensions, extensionSettings);
    }

    private static JsonObject section(JsonObject root, String key) {
        var element = root.get(key);
        if (element != null) {
            if (element.isJsonObject()) return element.getAsJsonObject();
            RuneLegendMod.LOGGER.error("Invalid RuneLegend server config section {}; expected an object, using defaults", key);
        }
        return new JsonObject();
    }

    private JsonObject toJson(TokenExtensions extensions) {
        var root = new JsonObject();
        root.addProperty("schemaVersion", VERSION);
        root.add("guard", guard.toJson());
        root.add("player_states", playerStates.toJson());
        var sections = tokens.toJson();
        extensions.writeDefaults(sections);
        root.add("tokens", sections);
        return root;
    }
}
