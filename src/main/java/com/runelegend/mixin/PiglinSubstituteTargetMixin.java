package com.runelegend.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.runelegend.token.SubstituteTargets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(PiglinAi.class)
abstract class PiglinSubstituteTargetMixin {
    @ModifyReturnValue(method = "findNearestValidAttackTarget", at = @At("RETURN"), require = 1)
    private static Optional<? extends LivingEntity> runelegend$matchRedirectedTarget(Optional<? extends LivingEntity> target,
                                                                                    ServerLevel level, Piglin piglin) {
        return target.map(entity -> SubstituteTargets.redirect(piglin, entity));
    }
}
