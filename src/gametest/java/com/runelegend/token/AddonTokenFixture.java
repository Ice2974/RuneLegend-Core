package com.runelegend.token;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.runelegend.api.token.TokenContext;
import com.runelegend.api.token.TokenEffect;
import com.runelegend.api.token.TokenEntrypoint;
import com.runelegend.api.token.TokenTrigger;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Set;

public final class AddonTokenFixture implements TokenEntrypoint {
    public static final Identifier ID = Identifier.parse("runelegend_test:echo");
    public static final Identifier WITHOUT_ICON = Identifier.parse("runelegend_test:without_icon");
    static final String DELAYED = "runelegend_test_delayed";
    static final String CLEANED = "runelegend_test_cleaned";
    static final String PENETRATING = "runelegend_test_penetrating";
    static final String DEBUFF = "runelegend_test_debuff";
    static final Settings DEFAULT = new Settings(4, 60, 20);
    static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.floatRange(0, 20).fieldOf("damage").forGetter(Settings::damage),
            ExtraCodecs.POSITIVE_INT.fieldOf("cooldown_ticks").forGetter(Settings::cooldownTicks),
            ExtraCodecs.POSITIVE_INT.fieldOf("effect_ticks").forGetter(Settings::effectTicks)
    ).apply(instance, Settings::new));

    record Settings(float damage, int cooldownTicks, int effectTicks) {}

    @Override public void registerTokens(Registrar registrar) {
        registrar.register(ID, CODEC, DEFAULT, Set.of(TokenTrigger.Kind.HURT, TokenTrigger.Kind.GUARD_SUCCESS,
                TokenTrigger.Kind.MELEE, TokenTrigger.Kind.KILL), Effect::new);
        registrar.register(WITHOUT_ICON, CODEC, DEFAULT, Set.of(), _ -> new TokenEffect() {});
    }

    static final class Effect implements TokenEffect {
        private final Settings settings;
        private ServerPlayer owner;
        private TokenContext lastContext;

        Effect(Settings settings) { this.settings = settings; }

        @Override public void onEvent(TokenContext context, TokenTrigger event) {
            switch (event.kind()) {
                case HURT -> arm(context);
                case GUARD_SUCCESS -> {
                    if (event.target().entityTags().contains(DEBUFF)) {
                        context.applyDebuff(event.target(), new MobEffectInstance(MobEffects.POISON, settings.effectTicks()));
                    } else {
                        context.damage(event.target(), settings.damage());
                    }
                }
                case KILL -> context.startCooldown(settings.cooldownTicks());
                default -> { }
            }
        }

        private void arm(TokenContext context) {
            if (context.cooldownRemainingTicks() > 0) return;
            lastContext = context;
            owner = context.player();
            if (context.damage(owner, settings.damage())
                    || context.applyDebuff(owner, new MobEffectInstance(MobEffects.POISON, settings.effectTicks()))) {
                throw new AssertionError("Addon token harmed its owner");
            }
            context.trackEffect("speed", () -> speed(owner, false));
            speed(owner, true);
            context.schedule("speed", settings.effectTicks(), fresh -> {
                fresh.player().addTag(DELAYED);
                fresh.finishEffect("speed");
            });
            context.startCooldown(settings.cooldownTicks());
            context.flash();
        }

        @Override public float penetratingMeleeBonus(TokenContext context, LivingEntity target) {
            return target.entityTags().contains(PENETRATING) ? settings.damage() : 0;
        }

        @Override public void onPlayerReplaced(ServerPlayer previous, ServerPlayer replacement) {
            if (owner == previous && speed(previous, false)) speed(replacement, true);
            owner = replacement;
        }

        @Override public void onCleanup() {
            if (lastContext == null) return;
            try {
                lastContext.player();
                throw new AssertionError("A token callback retained a usable context");
            } catch (IllegalStateException expected) {
                owner.addTag(CLEANED);
            }
        }
    }

    private static boolean speed(ServerPlayer player, boolean active) {
        var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (!active) return attribute.removeModifier(ID);
        attribute.addOrUpdateTransientModifier(new AttributeModifier(ID, 0.05, AttributeModifier.Operation.ADD_VALUE));
        return true;
    }
}
