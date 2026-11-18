package com.runelegend;

import com.google.gson.JsonParser;
import com.runelegend.combat.GuardConfig;
import com.runelegend.player.PlayerStateConfig;
import com.runelegend.token.TokenConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerConfigTest {
    @TempDir Path directory;

    @Test void generatesAndReadsOneCompleteDefaultFile() throws Exception {
        var path = directory.resolve("config/runelegend-core/server.json");
        assertEquals(ServerConfig.DEFAULT, ServerConfig.loadOrGenerate(path));

        var root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals(Set.of("schemaVersion", "guard", "player_states", "tokens"), root.keySet());
        assertEquals(1, root.get("schemaVersion").getAsInt());
        assertEquals(GuardConfig.DEFAULT.toJson(), root.get("guard"));
        assertEquals(PlayerStateConfig.DEFAULT.toJson(), root.get("player_states"));
        assertEquals(JsonParser.parseString(TokenConfig.DEFAULT.toJson().toString()), root.get("tokens"));
        assertEquals(ServerConfig.DEFAULT, ServerConfig.loadOrGenerate(path));
    }

    @Test void readsCustomValuesFromAllSections() throws Exception {
        var path = directory.resolve("server.json");
        Files.writeString(path, """
                {
                  "schemaVersion": 1,
                  "guard": {"max": 30000},
                  "player_states": {"revive_distance": 4},
                  "tokens": {"runelegend:soul_siphon": {"chance": 1}}
                }
                """);

        var config = ServerConfig.loadOrGenerate(path);
        assertEquals(new GuardConfig(30000, 2000, 125, 100, 10), config.guard());
        assertEquals(new PlayerStateConfig(4, 2, 15, 50), config.playerStates());
        assertEquals(new TokenConfig.SoulSiphonSettings(1, 2), config.tokens().soulSiphon());
    }

    @Test void invalidOrMissingSectionsKeepOtherSections() throws Exception {
        var path = directory.resolve("server.json");
        Files.writeString(path, """
                {
                  "schemaVersion": 1,
                  "guard": {"max": 30000, "min_to_start": 40000},
                  "player_states": [],
                  "tokens": {"runelegend:soul_siphon": {"chance": 1}}
                }
                """);

        var config = ServerConfig.loadOrGenerate(path);
        assertEquals(GuardConfig.DEFAULT, config.guard());
        assertEquals(PlayerStateConfig.DEFAULT, config.playerStates());
        assertEquals(new TokenConfig.SoulSiphonSettings(1, 2), config.tokens().soulSiphon());

        Files.writeString(path, """
                {"schemaVersion": 1, "guard": {"max": 30000}, "tokens": null}
                """);
        config = ServerConfig.loadOrGenerate(path);
        assertEquals(new GuardConfig(30000, 2000, 125, 100, 10), config.guard());
        assertEquals(PlayerStateConfig.DEFAULT, config.playerStates());
        assertEquals(TokenConfig.DEFAULT, config.tokens());
    }

    @Test void invalidFilesUseDefaultsWithoutBeingOverwritten() throws Exception {
        var path = directory.resolve("server.json");
        for (var invalid : new String[]{"", "broken", "[]", "{}",
                "{\"schemaVersion\":2,\"guard\":{\"max\":30000}}",
                "{\"schemaVersion\":\"1\",\"guard\":{\"max\":30000}}",
                "{\"schemaVersion\":1.5,\"guard\":{\"max\":30000}}"}) {
            Files.writeString(path, invalid);
            assertEquals(ServerConfig.DEFAULT, ServerConfig.loadOrGenerate(path), invalid);
            assertEquals(invalid, Files.readString(path));
        }
        assertEquals(ServerConfig.DEFAULT, ServerConfig.loadOrGenerate(directory));
    }
}
