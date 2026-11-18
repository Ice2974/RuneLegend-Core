package com.runelegend.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.runelegend.token.SubstituteTargets;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(StartAttacking.class)
abstract class StartAttackingSubstituteTargetMixin {
    @WrapOperation(method = "lambda$create$3", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/behavior/declarative/MemoryAccessor;set(Ljava/lang/Object;)V"),
            require = 1, allow = 1)
    private static void runelegend$redirectNewAttackMemory(MemoryAccessor<?, ?> memory, Object target,
                                                         Operation<Void> original, @Local(argsOnly = true) Mob mob) {
        original.call(memory, SubstituteTargets.redirect(mob, (LivingEntity) target));
    }
}
