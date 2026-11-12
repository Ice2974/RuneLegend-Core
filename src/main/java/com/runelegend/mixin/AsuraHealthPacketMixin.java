package com.runelegend.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.runelegend.token.effect.AsuraHealth;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

@Mixin(ClientboundUpdateAttributesPacket.class)
abstract class AsuraHealthPacketMixin {
    @WrapOperation(method = "<init>(ILjava/util/Collection;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;getBaseValue()D"), require = 1, allow = 1)
    private double runelegend$temporaryBase(AttributeInstance attribute, Operation<Double> original) {
        var form = AsuraHealth.on(attribute);
        return form == null ? original.call(attribute) : form.temporaryMaximum();
    }

    @WrapOperation(method = "<init>(ILjava/util/Collection;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;getModifiers()Ljava/util/Set;"),
            require = 1, allow = 1)
    private Set<AttributeModifier> runelegend$temporaryModifiers(AttributeInstance attribute,
                                                                Operation<Set<AttributeModifier>> original) {
        return AsuraHealth.on(attribute) == null ? original.call(attribute) : Set.of();
    }
}
