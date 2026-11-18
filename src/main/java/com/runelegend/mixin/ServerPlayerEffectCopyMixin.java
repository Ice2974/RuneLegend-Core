package com.runelegend.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.runelegend.token.FountainMobEffect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerEffectCopyMixin {
    @WrapOperation(method = "restoreFrom", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;getActiveEffects()Ljava/util/Collection;"), require = 1, allow = 1)
    private Collection<MobEffectInstance> runelegend$copyOrdinaryEffects(ServerPlayer previous,
            Operation<Collection<MobEffectInstance>> original) {
        return FountainMobEffect.ordinaryEffects(original.call(previous));
    }
}
