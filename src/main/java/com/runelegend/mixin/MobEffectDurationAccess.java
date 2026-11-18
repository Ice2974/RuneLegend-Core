package com.runelegend.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MobEffectInstance.class)
public interface MobEffectDurationAccess {
    @Invoker("tickDownDuration")
    void runelegend$tickDownDuration();

    @Invoker("downgradeToHiddenEffect")
    boolean runelegend$downgradeToHiddenEffect();
}
