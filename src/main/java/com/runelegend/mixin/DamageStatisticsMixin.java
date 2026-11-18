package com.runelegend.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.runelegend.game.DamageStatistics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin({LivingEntity.class, Player.class})
abstract class DamageStatisticsMixin {
    // LivingEntity also writes absorption after health damage. Absorption has already been consumed at that point.
    @WrapOperation(method = "actuallyHurt", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setAbsorptionAmount(F)V", ordinal = 0),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setAbsorptionAmount(F)V", ordinal = 0)
    }, require = 1, allow = 1)
    private void runelegend$measureAbsorption(@Coerce LivingEntity victim, float amount, Operation<Void> original) {
        float before = victim.getAbsorptionAmount();
        original.call(victim, amount);
        DamageStatistics.absorptionChanged(victim, before, victim.getAbsorptionAmount());
    }

    @WrapOperation(method = "actuallyHurt", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V")
    }, require = 1, allow = 1)
    private void runelegend$measureHealth(@Coerce LivingEntity victim, float health, Operation<Void> original) {
        DamageStatistics.beginHealthWrite(victim);
        original.call(victim, health);
        // Covers entity overrides that do not delegate to LivingEntity.setHealth.
        DamageStatistics.finishHealthWrite(victim);
    }
}
