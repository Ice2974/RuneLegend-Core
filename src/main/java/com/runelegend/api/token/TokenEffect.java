package com.runelegend.api.token;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** One effect instance belongs to one equipped token, player and runtime generation. */
public interface TokenEffect {
    default void onEvent(TokenContext context, TokenTrigger event) {}

    /** Called for MELEE subscriptions before vanilla damage settlement. Return a finite positive bonus. */
    default float penetratingMeleeBonus(TokenContext context, LivingEntity target) { return 0; }

    /** Moves any entity-bound state during a living replacement, before the new player takes over. */
    default void onPlayerReplaced(ServerPlayer previous, ServerPlayer replacement) {}

    /** Releases resources retained by this instance after Core invalidates its runtime generation. */
    default void onCleanup() {}
}
