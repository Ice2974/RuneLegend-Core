package com.runelegend.player;

import com.google.gson.JsonParser;
import com.runelegend.game.GameController;
import com.runelegend.token.TokenRegistry;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static com.runelegend.player.PlayerTokenService.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerTokenStoreTest {
    private static final UUID FIRST = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID SECOND = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
    private static final Identifier ECHO = Identifier.parse("runelegend:echo");
    private static final Identifier REMOVED = Identifier.parse("removed:token");

    @TempDir Path directory;

    @Test void createsAndReopensGlobalFileAndKeepsItWhenEmpty() throws IOException {
        var path = directory.resolve("config/runelegend-core/players.json");
        var store = PlayerTokenStore.load(path);
        assertTrue(store.writable());
        assertTrue(savedPlayers(path).entrySet().isEmpty());
        assertEquals(PlayerTokenData.EMPTY, store.get(FIRST));

        assertTrue(store.set(FIRST, new PlayerTokenData(List.of(ECHO, REMOVED))));
        assertTrue(store.set(SECOND, new PlayerTokenData(List.of(REMOVED))));
        var record = savedPlayers(path).getAsJsonObject(FIRST.toString());
        assertEquals(JsonParser.parseString("""
                {"equippedTokens":["runelegend:echo","removed:token"]}
                """), record);
        var reopened = PlayerTokenStore.load(path);
        assertEquals(List.of(ECHO, REMOVED), reopened.get(FIRST).equipped());
        assertEquals(List.of(REMOVED), reopened.get(SECOND).equipped());
        var service = service(reopened);
        assertEquals(CHANGED, service.unequip(FIRST, ECHO));
        assertEquals(List.of(REMOVED), PlayerTokenStore.load(path).get(FIRST).equipped());
        assertEquals(CHANGED, service.clear(FIRST));
        assertEquals(List.of(REMOVED), PlayerTokenStore.load(path).get(SECOND).equipped());
        assertEquals(CHANGED, service.clear(SECOND));
        assertEquals(UNCHANGED, service.clear(SECOND));
        assertTrue(savedPlayers(path).entrySet().isEmpty());
    }

    @Test void invalidFilesDoNotLoadPartialDataOrAllowWrites() throws IOException {
        var path = directory.resolve("players.json");
        for (var invalid : List.of(
                "{",
                "{\"schemaVersion\":1,\"players\":{},}",
                "{\"schemaVersion\":2,\"players\":{}}",
                "{\"schemaVersion\":1,\"players\":{\"not-a-uuid\":{\"equippedTokens\":[]}}}",
                withInvalidSecondRecord("{\"equipped\":[\"a:a\"]}"),
                withInvalidSecondRecord("{\"equippedTokens\":[\"a:a\",\"a:a\"]}"),
                withInvalidSecondRecord("{\"equippedTokens\":[\"a:a\",\"a:b\",\"a:c\",\"a:d\"]}"),
                withInvalidSecondRecord("{\"equippedTokens\":[\"not a token ID\"]}"))) {
            Files.writeString(path, invalid);
            var store = PlayerTokenStore.load(path);
            assertFalse(store.writable(), invalid);
            assertEquals(PlayerTokenData.EMPTY, store.get(FIRST), invalid);
            assertEquals(READ_ONLY, service(store).equip(FIRST, ECHO), invalid);
            assertEquals(invalid, Files.readString(path));
        }
    }

    @Test void failedAtomicReplacementKeepsLastCommittedLoadoutAndDisablesWrites() throws IOException {
        var path = directory.resolve("players.json");
        var backup = directory.resolve("original.json");
        var store = PlayerTokenStore.load(path);
        var service = service(store);
        assertEquals(CHANGED, service.equip(FIRST, ECHO));
        var original = Files.readString(path);
        Files.move(path, backup);
        Files.createDirectory(path);
        var sentinel = Files.writeString(path.resolve("keep.txt"), "keep");

        assertEquals(READ_ONLY, service.clear(FIRST));
        assertEquals(List.of(ECHO), service.get(FIRST).equipped());
        assertEquals("keep", Files.readString(sentinel));
        assertEquals(original, Files.readString(backup));
        try (var files = Files.list(directory)) {
            assertEquals(2, files.count(), "Failed replacement removes its temporary file");
        }

        Files.delete(sentinel);
        Files.delete(path);
        Files.move(backup, path);
        assertEquals(READ_ONLY, service.clear(FIRST));
        assertEquals(original, Files.readString(path));
        assertEquals(CHANGED, service(PlayerTokenStore.load(path)).clear(FIRST));
    }

    private static String withInvalidSecondRecord(String record) {
        return """
                {"schemaVersion":1,"players":{"%s":{"equippedTokens":["runelegend:echo"]},"%s":%s}}
                """.formatted(FIRST, SECOND, record);
    }

    private static com.google.gson.JsonObject savedPlayers(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonObject("players");
    }

    private static PlayerTokenService service(PlayerTokenStore store) {
        return new PlayerTokenService(new GameController(() -> {}, (before, after) -> {}), store, TokenRegistry.builtIn());
    }
}
