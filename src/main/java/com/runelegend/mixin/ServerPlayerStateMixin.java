package com.runelegend.mixin;

import com.runelegend.RuneLegendServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerStateMixin {
    @Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void runelegend$downAfterTotem(DamageSource source, CallbackInfo ci) {
        var player = (ServerPlayer) (Object) this;
        var server = RuneLegendServer.find(player.level().getServer());
        if (server != null && server.interceptDeath(player)) ci.cancel();
    }

    @Inject(method = "setLastClientInput(Lnet/minecraft/world/entity/player/Input;)V", at = @At("RETURN"), require = 1, allow = 1)
    private void runelegend$cancelRescueOnRelease(Input input, CallbackInfo ci) {
        var player = (ServerPlayer) (Object) this;
        var server = RuneLegendServer.find(player.level().getServer());
        if (server != null && !input.shift()) server.releasedSneak(player);
    }
}
