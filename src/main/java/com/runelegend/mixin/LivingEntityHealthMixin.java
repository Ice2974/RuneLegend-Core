package com.runelegend.mixin;

import com.runelegend.RuneLegendServer;
import com.runelegend.game.DamageStatistics;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LivingEntityHealthMixin {
    @Inject(method = "setHealth(F)V", at = @At("RETURN"), require = 1, allow = 1)
    private void runelegend$observeHealth(float value, CallbackInfo ci) {
        DamageStatistics.finishHealthWrite((LivingEntity) (Object) this);
        if ((Object) this instanceof ServerPlayer player && player.connection != null) {
            var minecraft = player.level().getServer();
            var server = RuneLegendServer.find(minecraft);
            if (server != null && minecraft.isSameThread()) server.tokens().updateHealth(player);
        }
    }
}
