package com.runelegend.api.token;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Trigger data for the current callback. Target and source can be null. */
public record TokenTrigger(Kind kind, LivingEntity target, DamageSource source, float incomingDamage,
                           float damageBeforeMitigation, float blockedDamage, float damageBeforeAbsorption) {
    public enum Kind { MELEE, HURT, KILL, ARROW, GUARD_SUCCESS, HEALTH }
}
