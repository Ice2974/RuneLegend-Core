package com.runelegend.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.runelegend.token.SubstituteTargets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WitherBoss.class)
abstract class WitherSubstituteTargetMixin {
    @Shadow @Final private static TargetingConditions TARGETING_CONDITIONS;

    @ModifyVariable(method = "setAlternativeTarget(II)V", at = @At("HEAD"), argsOnly = true, ordinal = 1,
            require = 1, allow = 1)
    private int runelegend$redirectNewSideHeadTarget(int id, @Local(argsOnly = true, ordinal = 0) int head) {
        var wither = (WitherBoss) (Object) this;
        return head > 0 && wither.level() instanceof ServerLevel level ? runelegend$redirect(wither, level, id) : id;
    }

    @Inject(method = "customServerAiStep(Lnet/minecraft/server/level/ServerLevel;)V", at = @At("HEAD"),
            require = 1, allow = 1)
    private void runelegend$redirectExistingSideHeadTargets(ServerLevel level, CallbackInfo ci) {
        var wither = (WitherBoss) (Object) this;
        for (int head = 1; head <= 2; head++) {
            int target = wither.getAlternativeTarget(head);
            int redirected = runelegend$redirect(wither, level, target);
            if (redirected != target) wither.setAlternativeTarget(head, redirected);
        }
    }

    @Unique
    private static int runelegend$redirect(WitherBoss wither, ServerLevel level, int id) {
        if (id <= 0 || !(level.getEntity(id) instanceof LivingEntity target)) return id;
        return SubstituteTargets.redirect(wither, target, model -> TARGETING_CONDITIONS.test(level, wither, model)).getId();
    }
}
