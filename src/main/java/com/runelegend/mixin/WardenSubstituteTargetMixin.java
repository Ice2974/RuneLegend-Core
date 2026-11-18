package com.runelegend.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.runelegend.token.SubstituteTargets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.AngerManagement;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Warden.class)
abstract class WardenSubstituteTargetMixin {
    @WrapOperation(method = "getActiveAnger()I", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/monster/warden/AngerManagement;getActiveAnger(Lnet/minecraft/world/entity/Entity;)I"),
            require = 1, allow = 1)
    private int runelegend$useOriginalAnger(AngerManagement anger, Entity target, Operation<Integer> original) {
        return original.call(anger, SubstituteTargets.angerSource((Warden) (Object) this, target));
    }
}
