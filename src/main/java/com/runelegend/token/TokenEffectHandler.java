package com.runelegend.token;

import net.minecraft.server.level.ServerPlayer;

public interface TokenEffectHandler {
    default float addMeleeDamage(TokenEngine.Context context, TokenRuntimeState state) { return 0; }
    default boolean flashOnMeleeBoost() { return true; }
    default void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {}
    default boolean ready(ServerPlayer player, TokenRuntimeState state, long tick) { return true; }
    default boolean preparing(ServerPlayer player, TokenRuntimeState state, long tick) { return false; }
    default Object[] descriptionArguments() { return new Object[0]; }
}
