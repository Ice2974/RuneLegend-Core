package com.runelegend.resonance;

import com.runelegend.RuneLegendServer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.UUID;

public final class ResonanceChunkPersistenceGameTests {
    private static final int CHUNK_X = 10_000;
    private static final int CHUNK_Z = 10_000;
    private static final int MAX_WAIT_TICKS = 60;
    private static final int MAX_ENTITY_WAIT_TICKS = 40;

    @GameTest
    public void chunkUnloadAndReloadRetainsEntityLayers(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var service = server.resonance();
        var pos = new ChunkPos(CHUNK_X, CHUNK_Z);
        var blockX = pos.getMinBlockX() + 8;
        var blockZ = pos.getMinBlockZ() + 8;

        level.setChunkForced(CHUNK_X, CHUNK_Z, true);
        level.getChunk(CHUNK_X, CHUNK_Z);
        var blockY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        var husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
        test.assertTrue(husk != null, "The husk entity can be created");
        husk.setPos(blockX + 0.5, blockY, blockZ + 0.5);
        husk.setPersistenceRequired();
        husk.setNoAi(true);
        husk.setPermanentlyInvulnerable(true);
        test.assertTrue(level.addFreshEntity(husk), "The husk is added to the far chunk");

        var uuid = husk.getUUID();
        var expected = new ResonanceLayers(1, 2, 3);
        test.assertTrue(service.setLayers(husk, expected), "All three layers are written before unload");
        test.assertValueEqual(service.layers(uuid), expected, "All three layers are readable before unload");

        level.getChunkSource().save(true);
        level.getDataStorage().saveAndJoin();
        level.setChunkForced(CHUNK_X, CHUNK_Z, false);
        awaitUnload(test, level, server, pos, husk, uuid, expected, 0);
    }

    private static void awaitUnload(GameTestHelper test, ServerLevel level, RuneLegendServer server,
                                    ChunkPos pos, Mob original, UUID uuid, ResonanceLayers expected, int attempt) {
        if (level.getChunkSource().getChunkNow(pos.x(), pos.z()) == null) {
            test.runAfterDelay(2, () -> reload(test, level, server, pos, original, uuid, expected));
            return;
        }
        if (attempt >= MAX_WAIT_TICKS) {
            test.fail("The far chunk did not unload within the test window");
            return;
        }
        test.runAfterDelay(1, () -> awaitUnload(test, level, server, pos, original, uuid, expected, attempt + 1));
    }

    private static void reload(GameTestHelper test, ServerLevel level, RuneLegendServer server,
                               ChunkPos pos, Mob original, UUID uuid, ResonanceLayers expected) {
        test.assertTrue(level.getEntity(uuid) == null, "The entity is unloaded with its chunk");
        // Plain getChunk loads blocks only; a forced ticket raises the chunk back to entity-ticking so the
        // saved entity is recreated and visible through the level entity index.
        level.setChunkForced(pos.x(), pos.z(), true);
        level.getChunk(pos.x(), pos.z());
        awaitEntityReload(test, level, server, pos, original, uuid, expected, 0);
    }

    private static void awaitEntityReload(GameTestHelper test, ServerLevel level, RuneLegendServer server,
                                          ChunkPos pos, Mob original, UUID uuid, ResonanceLayers expected, int attempt) {
        var entity = level.getEntity(uuid);
        if (entity != null) {
            test.assertTrue(entity instanceof net.minecraft.world.entity.LivingEntity,
                    "The reloaded entity is a living entity");
            var reloaded = (net.minecraft.world.entity.LivingEntity) entity;
            test.assertValueEqual(reloaded.getUUID(), uuid, "The entity UUID survives unload/reload");
            test.assertTrue(reloaded != original, "Reloading creates a new entity instance");
            test.assertValueEqual(server.resonance().layers(uuid), expected,
                    "All three resonance layers survive unload/reload");
            server.resonance().clearLayers(reloaded);
            test.discard(reloaded);
            level.setChunkForced(pos.x(), pos.z(), false);
            test.succeed();
            return;
        }
        if (attempt >= MAX_ENTITY_WAIT_TICKS) {
            test.fail("The reloaded chunk did not recreate the entity within the test window");
            return;
        }
        test.runAfterDelay(1, () -> awaitEntityReload(test, level, server, pos, original, uuid, expected, attempt + 1));
    }
}
