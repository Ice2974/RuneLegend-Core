package com.runelegend.mixin;

import com.runelegend.token.effect.AsuraHealth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AttributeInstance.class)
abstract class AsuraHealthAttributeMixin implements AsuraHealth.AttributeAccess {
    @Unique private AsuraHealth runelegend$form;

    @Shadow protected abstract void setDirty();

    @Override @Invoker("calculateValue") public abstract double runelegend$normalValue();

    @Override public AsuraHealth runelegend$asuraHealth() { return runelegend$form; }

    @Override public void runelegend$setAsuraHealth(AsuraHealth form) {
        runelegend$form = form;
        setDirty();
    }

    @Inject(method = "getValue()D", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void runelegend$temporaryMaximum(CallbackInfoReturnable<Double> cir) {
        if (runelegend$form != null) cir.setReturnValue((double) runelegend$form.temporaryMaximum());
    }
}
