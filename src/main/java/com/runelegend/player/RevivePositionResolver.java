package com.runelegend.player;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;

public final class RevivePositionResolver {
    private RevivePositionResolver() {}

    public static Vec3 resolve(ServerLevel level, ServerPlayer player, Vec3 origin) {
        var candidates = new ArrayList<Vec3>();
        candidates.add(origin);
        var base = BlockPos.containing(origin);
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            if (x * x + z * z > 16) continue;
            for (int y = -3; y <= 3; y++) candidates.add(Vec3.atBottomCenterOf(base.offset(x, y, z)));
        }
        candidates.sort(Comparator.comparingDouble((Vec3 point) -> point.distanceToSqr(origin))
                .thenComparingDouble(Vec3::x).thenComparingDouble(Vec3::y).thenComparingDouble(Vec3::z));
        return candidates.stream().filter(point -> safe(level, player, point)).findFirst().orElse(origin);
    }

    private static boolean safe(ServerLevel level, ServerPlayer player, Vec3 point) {
        var box = player.getDimensions(Pose.STANDING).makeBoundingBox(point);
        if (box.minY < level.getMinY() || box.maxY > level.getMaxY() || !level.getWorldBorder().isWithinBounds(box)) return false;
        var floor = BlockPos.containing(point.x, point.y - 0.01, point.z);
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY - 0.1, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ()))) return false;
            var state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                    || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.CAMPFIRE)
                    || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.POWDER_SNOW)
                    || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)) return false;
        }
        // No spectator collision context: validate the ordinary standing body and a supporting surface.
        return level.noCollision(null, box) && !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
                && !level.noCollision(null, box.move(0, -0.05, 0));
    }
}
