package com.runelegend.player;

import net.minecraft.world.level.Level;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ReviveProgressTest {
    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:overworld"));
    private static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:the_nether"));
    private final DownedService downed = new DownedService(PlayerStateConfig.DEFAULT);
    private final UUID target = new UUID(0, 1), helper = new UUID(0, 2), other = new UUID(0, 3);
    private final List<UUID> revived = new ArrayList<>(), expired = new ArrayList<>();

    private void tick(long now, DownedService.Rescuer... players) {
        downed.tick(now, 1, List.of(players), record -> expired.add(record.playerUuid()), (record, uuid) -> {
            revived.add(record.playerUuid()); return true;
        });
    }
    private DownedService.Rescuer helper(UUID uuid, Vec3 pos, boolean sneak) {
        return new DownedService.Rescuer(uuid, OVERWORLD, pos, true, sneak);
    }
    private void add(UUID uuid, Vec3 pos) { downed.add(uuid, 1, 0, OVERWORLD, pos, 0, 0); }

    @Test void sphericalBoundaryAndContinuousDuration() {
        add(target, Vec3.ZERO);
        tick(1, helper(helper, new Vec3(0, 2.01, 0), true));
        assertNull(downed.rescue(target));
        tick(2, helper(helper, new Vec3(0, 2, 0), true));
        tick(61, helper(helper, new Vec3(0, 2, 0), true));
        assertTrue(revived.isEmpty());
        tick(62, helper(helper, new Vec3(0, 2, 0), true));
        assertEquals(List.of(target), revived);
        assertNull(downed.get(target));
    }

    @Test void releaseDisconnectAndDimensionChangeResetProgress() {
        add(target, Vec3.ZERO);
        tick(1, helper(helper, Vec3.ZERO, true));
        downed.cancelRescue(helper); // release and re-press within a single server tick
        tick(50, helper(helper, Vec3.ZERO, true));
        assertEquals(50, downed.rescue(target).startedAtTick());
        tick(60, new DownedService.Rescuer(helper, NETHER, Vec3.ZERO, true, true));
        assertNull(downed.rescue(target));
        tick(61, helper(helper, Vec3.ZERO, true));
        tick(62);
        assertNull(downed.rescue(target));
        tick(63, helper(helper, Vec3.ZERO, false));
        assertNull(downed.rescue(target));
    }

    @Test void oneToOneStableOwnershipAndFreshProgressOnHandoff() {
        add(target, Vec3.ZERO); add(other, new Vec3(1, 0, 0));
        tick(1, helper(helper, Vec3.ZERO, true));
        assertNotNull(downed.rescue(target)); assertNull(downed.rescue(other));
        var second = new UUID(0, 4);
        tick(30, helper(helper, new Vec3(0.5, 0, 0), true), helper(second, Vec3.ZERO, true));
        assertEquals(helper, downed.rescue(target).rescuerUuid());
        assertEquals(second, downed.rescue(other).rescuerUuid());
        tick(40, helper(second, Vec3.ZERO, true));
        assertNull(downed.rescue(target)); // second helper keeps the original target
        downed.cancelRescue(second);
        tick(41, helper(second, Vec3.ZERO, true));
        assertEquals(41, downed.rescue(target).startedAtTick());
    }

    @Test void expirationWinsTieAndDuplicateDownDoesNotExtendDeadline() {
        add(target, Vec3.ZERO);
        var first = downed.get(target);
        assertEquals(first, downed.add(target, 1, 200, NETHER, new Vec3(10, 0, 0), 0, 0));
        tick(240, helper(helper, Vec3.ZERO, true));
        tick(300, helper(helper, Vec3.ZERO, true));
        assertEquals(List.of(target), expired); assertTrue(revived.isEmpty());
        assertNull(downed.rescue(target));
        add(target, Vec3.ZERO);
        assertTrue(downed.get(target).sequence() > first.sequence());
        downed.clearRound(); downed.clearRound();
        assertTrue(downed.records().isEmpty());
    }
}
