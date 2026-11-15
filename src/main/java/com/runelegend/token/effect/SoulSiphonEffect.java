package com.runelegend.token.effect;

import com.mojang.serialization.Codec;
import com.runelegend.RuneLegendMod;
import com.runelegend.api.RuleSnapshot;
import com.runelegend.token.TokenConfig;
import com.runelegend.token.TokenEffectHandler;
import com.runelegend.token.TokenEvent;
import com.runelegend.token.TokenEngine;
import com.runelegend.token.TokenRuntimeState;
import com.runelegend.token.TokenTargeting;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class SoulSiphonEffect implements TokenEffectHandler {
    private static final Identifier MODIFIER = RuneLegendMod.id("soul_siphon");
    private static AttachmentType<Long> bonusRound;
    private final TokenConfig.SoulSiphonSettings settings;

    public static void registerAttachment() {
        bonusRound = AttachmentRegistry.createPersistent(RuneLegendMod.id("soul_siphon_round"), Codec.LONG);
    }

    public SoulSiphonEffect(TokenConfig.SoulSiphonSettings settings) {
        this.settings = settings;
    }

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        if (!TokenTargeting.enemy(event.target()) || !context.roll(settings.chance())) return;
        var player = context.player();
        var attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) return;
        float health = player.getHealth();
        // Save the bonus and its round together; neither is copied on death respawn.
        attribute.removeModifier(MODIFIER);
        attribute.addPermanentModifier(new AttributeModifier(MODIFIER, settings.maxHealthBonus(),
                AttributeModifier.Operation.ADD_VALUE));
        player.setAttached(bonusRound, context.round());
        if (player.getHealth() < health) player.setHealth(health);
        state.flash(context.tick());
    }

    public static void clearStaleBonus(ServerPlayer player, RuleSnapshot rules) {
        var round = player.getAttached(bonusRound);
        if (!rules.running() || round == null || round != rules.round()) clearBonus(player);
    }

    public static void clearBonus(ServerPlayer player) {
        player.removeAttached(bonusRound);
        var attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null || !attribute.removeModifier(MODIFIER)) return;
        float maximum = player.getMaxHealth();
        if (player.getHealth() > maximum) player.setHealth(maximum);
    }

    @Override public Object[] descriptionArguments() {
        return new Object[]{TokenConfig.percent(settings.chance()), settings.maxHealthBonus()};
    }
}
