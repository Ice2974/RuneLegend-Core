package com.runelegend.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.runelegend.RuneLegendMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.UUIDUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server thread storage for loadouts shared by worlds in the same installation. */
public final class PlayerTokenStore {
    private static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).setPrettyPrinting().create();
    private static final Codec<Map<UUID, PlayerTokenData>> PLAYERS =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerTokenData.CODEC);

    private final Path path;
    private Map<UUID, PlayerTokenData> players;
    private boolean writable = true;

    private PlayerTokenStore(Path path, Map<UUID, PlayerTokenData> players) {
        this.path = path;
        this.players = players;
    }

    public static Path defaultPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("runelegend-core/players.json");
    }

    public static PlayerTokenStore inMemory() {
        return new PlayerTokenStore(null, Map.of());
    }

    public static PlayerTokenStore load(Path path) {
        var store = new PlayerTokenStore(path.toAbsolutePath(), Map.of());
        try {
            if (Files.notExists(store.path, LinkOption.NOFOLLOW_LINKS)) {
                store.write(store.players);
            } else {
                try (var reader = Files.newBufferedReader(store.path)) {
                    var root = GSON.fromJson(reader, JsonObject.class);
                    var version = root == null ? null : root.get("schemaVersion");
                    if (version == null || !Integer.toString(VERSION).equals(version.toString())) {
                        throw new IllegalArgumentException("Expected schemaVersion " + VERSION);
                    }
                    // Reject partial results: saving one would erase invalid or unread records.
                    var decoded = PLAYERS.fieldOf("players").codec().parse(JsonOps.INSTANCE, root).getOrThrow();
                    var nonEmpty = new HashMap<>(decoded);
                    nonEmpty.values().removeIf(data -> data.equipped().isEmpty());
                    store.players = nonEmpty;
                }
            }
        } catch (IOException | RuntimeException error) {
            store.players = Map.of();
            store.disableWrites(error);
        }
        return store;
    }

    public boolean writable() {
        return writable;
    }

    public PlayerTokenData get(UUID uuid) {
        return players.getOrDefault(uuid, PlayerTokenData.EMPTY);
    }

    boolean set(UUID uuid, PlayerTokenData data) {
        if (!writable) return false;
        var next = new HashMap<>(players);
        if (data.equipped().isEmpty()) next.remove(uuid);
        else next.put(uuid, data);
        try {
            if (path != null) write(next);
        } catch (IOException | RuntimeException error) {
            disableWrites(error);
            return false;
        }
        players = next;
        return true;
    }

    private void write(Map<UUID, PlayerTokenData> next) throws IOException {
        var root = new JsonObject();
        root.addProperty("schemaVersion", VERSION);
        root.add("players", PLAYERS.encodeStart(JsonOps.INSTANCE, next).getOrThrow());
        var directory = Files.createDirectories(path.getParent());
        var temporary = Files.createTempFile(directory, "players-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(root) + "\n");
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException error) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException | RuntimeException cleanup) {
                error.addSuppressed(cleanup);
            }
            throw error;
        }
    }

    private void disableWrites(Exception error) {
        writable = false;
        RuneLegendMod.LOGGER.error("Could not load or save RuneLegend player data {}; equipment is read-only", path, error);
    }
}
