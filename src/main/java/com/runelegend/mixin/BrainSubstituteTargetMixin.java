package com.runelegend.mixin;

import com.runelegend.token.SubstituteTargets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Brain.class)
abstract class BrainSubstituteTargetMixin {
    @Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;startEachNonRunningBehavior"
                    + "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"),
            require = 1, allow = 1)
    private void runelegend$redirectAttackMemory(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        if (!(entity instanceof Mob mob)) return;
        var brain = (Brain<?>) (Object) this;
        var memory = MemoryModuleType.ATTACK_TARGET;
        if (!brain.hasMemoryValue(memory)) return;
        var target = brain.getMemory(memory).orElseThrow();
        var redirected = SubstituteTargets.redirect(mob, target);
        if (redirected != target) brain.setMemoryWithExpiry(memory, redirected, brain.getTimeUntilExpiry(memory));
    }
}
