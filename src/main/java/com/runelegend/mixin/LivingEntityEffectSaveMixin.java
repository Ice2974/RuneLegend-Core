package com.runelegend.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.runelegend.token.FountainMobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;
import java.util.List;

@Mixin(LivingEntity.class)
abstract class LivingEntityEffectSaveMixin {
    @WrapOperation(method = "addAdditionalSaveData", at = @At(value = "INVOKE",
            target = "Ljava/util/List;copyOf(Ljava/util/Collection;)Ljava/util/List;"), require = 1, allow = 1)
    private static List<?> runelegend$saveOrdinaryEffects(Collection<MobEffectInstance> effects, Operation<List<?>> original) {
        return original.call(FountainMobEffect.ordinaryEffects(effects));
    }
}
