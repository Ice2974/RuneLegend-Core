package com.runelegend.resonance;

import com.runelegend.RuneLegendMod;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ResonanceStorageGameTests {
    @GameTest
    public void resonanceReloadsAndFailedReadsNeverOverwriteTheFile(GameTestHelper test) throws Exception {
        var directory = Files.createTempDirectory("runelegend-resonance-test-");
        var file = RuneLegendMod.id("resonance.dat").resolveAgainst(directory);
        var holder = UUID.randomUUID();
        var player = UUID.randomUUID();
        var expectedLayers = new ResonanceLayers(4, 5, 6);
        var expectedSelection = Identifier.parse("runelegend:protection");
        try {
            try (var storage = storage(test, directory)) {
                var data = ResonanceSavedData.load(storage, directory);
                test.assertTrue(data.writable(), "A missing file starts writable");
                test.assertValueEqual(data.layers(holder), ResonanceLayers.NONE, "A missing file has no layers");
                test.assertTrue(data.guardSelection(player).isEmpty(), "A missing file has no selection");
                test.assertTrue(data.writeLayers(holder, expectedLayers), "Layer data is written");
                test.assertTrue(data.writeGuardSelection(player, Optional.of(expectedSelection)),
                        "Guard selection is written");
                storage.saveAndJoin();
            }
            try (var storage = storage(test, directory)) {
                var data = ResonanceSavedData.load(storage, directory);
                test.assertValueEqual(data.layers(holder), expectedLayers, "A fresh storage reads saved layers");
                test.assertValueEqual(data.guardSelection(player), Optional.of(expectedSelection),
                        "A fresh storage reads the saved selection");
                test.assertTrue(!data.isDirty(), "Loading valid data does not request another write");
            }

            var encoded = (CompoundTag) ResonanceSavedData.CODEC.encodeStart(NbtOps.INSTANCE,
                    new ResonanceSavedData(Map.of(), Map.of(), true)).getOrThrow();
            encoded.putInt("schemaVersion", 2);
            var unsupported = new CompoundTag();
            unsupported.put("data", encoded);
            NbtIo.writeCompressed(unsupported, file);
            assertRejectedAndPreserved(test, directory, file);

            Files.write(file, new byte[]{1, 2, 3, 4});
            assertRejectedAndPreserved(test, directory, file);
            test.succeed();
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.getParent());
            Files.deleteIfExists(directory);
        }
    }

    private static SavedDataStorage storage(GameTestHelper test, Path directory) {
        var server = test.getLevel().getServer();
        return new SavedDataStorage(directory, server.getFixerUpper(), server.registryAccess());
    }

    private static void assertRejectedAndPreserved(GameTestHelper test, Path directory, Path file) throws Exception {
        byte[] original = Files.readAllBytes(file);
        try (var storage = storage(test, directory)) {
            var data = ResonanceSavedData.load(storage, directory);
            test.assertTrue(!data.writable(), "Failed reads disable resonance writes");
            test.assertValueEqual(data.layers(UUID.randomUUID()), ResonanceLayers.NONE,
                    "Failed reads expose no partial layer data");
            storage.saveAndJoin();
        }
        test.assertTrue(Arrays.equals(original, Files.readAllBytes(file)),
                "Saving and closing after rejection preserves the original bytes");
    }
}
