package com.runelegend.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.runelegend.RuneLegendServer;
import com.runelegend.combat.GuardItems;
import com.runelegend.token.TokenEventBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class PlayerAttackMixin {
    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void runelegend$noMeleeWhileGuarding(Entity target, CallbackInfo ci) {
        if (runelegend$usingGuardItem()) ci.cancel();
    }

    @Inject(method = "cannotAttackWithItem(Lnet/minecraft/world/item/ItemStack;I)Z", at = @At("HEAD"), cancellable = true)
    private void runelegend$noAttackPacketWhileGuarding(ItemStack stack, int ticks, CallbackInfoReturnable<Boolean> cir) {
        if (runelegend$usingGuardItem()) cir.setReturnValue(true);
    }

    @Unique
    private boolean runelegend$usingGuardItem() {
        if (!((Object) this instanceof ServerPlayer player)) return false;
        var server = RuneLegendServer.find(player.level().getServer());
        if (server == null) return false;
        server.stamina().validateUse(player);
        return GuardItems.using(player);
    }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), require = 1, allow = 1)
    private boolean runelegend$primaryMelee(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
        if (!((Object) this instanceof ServerPlayer attacker) || !(target instanceof LivingEntity living)) {
            return original.call(target, source, amount);
        }
        var server = RuneLegendServer.find(attacker.level().getServer());
        if (server == null || !server.game().getRules().running()) return original.call(target, source, amount);
        var boost = server.tokens().melee(attacker, living, amount);
        boolean result = TokenEventBridge.melee(attacker, target, source, boost.bonusDamage(), () -> original.call(target, source, amount));
        if (result) server.tokens().confirmBoost(attacker, boost.applied());
        return result;
    }

    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/player/Player;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"), allow = 1)
    private float runelegend$addDirectBonus(Player victim, DamageSource source, float amount, Operation<Float> original) {
        return TokenEventBridge.afterMagic(victim, source, original.call(victim, source, amount),
                (tokenSource, bonus) -> original.call(victim, tokenSource, bonus));
    }
}
