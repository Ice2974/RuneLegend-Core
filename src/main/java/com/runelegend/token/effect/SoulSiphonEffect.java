package com.runelegend.token.effect;

import com.runelegend.RuneLegendMod;
import com.runelegend.token.TokenConfig;
import com.runelegend.token.TokenEffectHandler;
import com.runelegend.token.TokenEvent;
import com.runelegend.token.TokenEngine;
import com.runelegend.token.TokenRuntimeState;
import com.runelegend.token.TokenTargeting;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class SoulSiphonEffect implements TokenEffectHandler {
    private static final Identifier MODIFIER = RuneLegendMod.id("soul_siphon");
    private final TokenConfig.SoulSiphonSettings settings;

    public SoulSiphonEffect(TokenConfig.SoulSiphonSettings settings) {
        this.settings = settings;
    }

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        if (!TokenTargeting.enemy(event.target()) || !context.roll(settings.chance())) return;
        var player = context.player();
        var attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) return;
        attribute.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER, settings.maxHealthBonus(),
                AttributeModifier.Operation.ADD_VALUE));
        state.flash(context.tick());
    }

    public static void transferBonus(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        if (!alive) return;
        var oldAttribute = oldPlayer.getAttribute(Attributes.MAX_HEALTH);
        var newAttribute = newPlayer.getAttribute(Attributes.MAX_HEALTH);
        if (oldAttribute == null || newAttribute == null) return;
        var modifier = oldAttribute.getModifier(MODIFIER);
        if (modifier == null) return;
        // Vanilla copies only permanent modifiers when replacing a living player.
        newAttribute.addOrUpdateTransientModifier(modifier);
        if (newPlayer.getHealth() < oldPlayer.getHealth()) newPlayer.setHealth(oldPlayer.getHealth());
    }

    public static void clearBonus(ServerPlayer player) {
        var attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null || !attribute.removeModifier(MODIFIER)) return;
        float maximum = player.getMaxHealth();
        if (player.getHealth() > maximum) player.setHealth(maximum);
    }

    @Override public Object[] descriptionArguments() {
        return new Object[]{TokenConfig.percent(settings.chance()), settings.maxHealthBonus()};
    }
}
