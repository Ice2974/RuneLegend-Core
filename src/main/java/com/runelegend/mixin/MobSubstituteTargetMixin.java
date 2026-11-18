package com.runelegend.mixin;

import com.runelegend.token.SubstituteTargets;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
abstract class MobSubstituteTargetMixin {
    @ModifyVariable(method = "setTarget(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"),
            argsOnly = true, require = 1, allow = 1)
    private LivingEntity runelegend$redirectNewTarget(LivingEntity target) {
        return SubstituteTargets.redirect((Mob) (Object) this, target);
    }

    @Inject(method = "serverAiStep()V", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/entity/ai/sensing/Sensing;tick()V"), require = 1, allow = 1)
    private void runelegend$redirectExistingTarget(CallbackInfo ci) {
        var mob = (Mob) (Object) this;
        var target = mob.getTargetUnchecked();
        var redirected = SubstituteTargets.redirect(mob, target);
        if (redirected != target) mob.setTarget(redirected);
    }
}
