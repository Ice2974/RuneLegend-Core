package com.runelegend.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.runelegend.token.effect.AsuraHealth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class AsuraHealthLivingEntityMixin {
    @Inject(method = "heal(F)V", at = @At("HEAD"), require = 1, allow = 1)
    private void runelegend$healNormalBar(float amount, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && player.isAlive()) {
            var form = AsuraHealth.on(player.getAttribute(Attributes.MAX_HEALTH));
            if (form != null) form.healNormalBar(amount);
        }
    }

    @WrapOperation(method = "addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getHealth()F"), require = 1, allow = 1)
    private float runelegend$saveNormalBar(LivingEntity entity, Operation<Float> original) {
        if (entity instanceof ServerPlayer player && !player.isDeadOrDying()) {
            var form = AsuraHealth.on(player.getAttribute(Attributes.MAX_HEALTH));
            if (form != null) return form.healthForSave();
        }
        return original.call(entity);
    }
}
