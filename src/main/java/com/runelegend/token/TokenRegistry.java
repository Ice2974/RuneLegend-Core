package com.runelegend.token;

import com.google.gson.JsonObject;
import com.runelegend.RuneLegendMod;
import com.runelegend.token.effect.*;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.runelegend.token.TokenEvent.Kind.*;

public final class TokenRegistry {
    /** Shared handler for tokens that are registered but whose effects are intentionally not implemented yet. */
    private static final TokenEffectHandler NO_EFFECT = new TokenEffectHandler() {};
    private final Map<Identifier, TokenDefinition> definitions;

    private TokenRegistry(Map<Identifier, TokenDefinition> definitions) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public static TokenRegistry builtIn() {
        return builtIn(TokenConfig.DEFAULT);
    }

    public static TokenRegistry builtIn(TokenConfig config) {
        return builtIns(config).build();
    }

    public static TokenRegistry create(TokenConfig config, TokenExtensions extensions, JsonObject tokenSections) {
        var builder = builtIns(config);
        extensions.install(builder, tokenSections);
        return builder.build();
    }

    private static Builder builtIns(TokenConfig config) {
        return new Builder()
                .add("soul_siphon", new SoulSiphonEffect(config.soulSiphon()), KILL)
                .add("echo", new EchoEffect(config.echo()), KILL)
                .add("immediate_counter", new ImmediateCounterEffect(config.immediateCounter()), GUARD_SUCCESS, MELEE)
                .add("venomous_tip", new VenomousTipEffect(config.venomousTip()), MELEE)
                .add("poisoned_arrow", new PoisonedArrowEffect(config.poisonedArrow()), ARROW)
                .add("asura_form", new AsuraFormEffect(config.asuraForm()), KILL, MELEE)
                .add("resonance_healing", new ResonanceHealingEffect(config.resonanceHealing()), GUARD_SUCCESS)
                .add("vernal_tide", new VernalTideEffect(config.vernalTide()), MELEE)
                .add("substitute", new SubstituteEffect(config.substitute()), HEALTH)
                .addNoEffect("fountain_of_life")
                .add("full_blessing", new FullBlessingEffect(config.fullBlessing()), HEALTH)
                .add("retribution", new RetributionEffect(config.retribution()), HURT, MELEE)
                .add("execution_echo", new ExecutionEchoEffect(config.executionEcho()), MELEE)
                .add("arrogance", new ArroganceEffect(config.arrogance()), HEALTH, KILL, MELEE);
    }

    public TokenDefinition get(Identifier id) { return definitions.get(id); }
    public Collection<TokenDefinition> all() { return definitions.values(); }

    public static final class Builder {
        private final Map<Identifier, TokenDefinition> entries = new LinkedHashMap<>();
        private boolean built;

        public Builder register(TokenDefinition definition) {
            if (built) throw new IllegalStateException("Token registry is frozen");
            if (entries.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Duplicate token: " + definition.id());
            }
            return this;
        }

        private Builder add(String id, TokenEffectHandler effect, TokenEvent.Kind... events) {
            return register(new TokenDefinition(RuneLegendMod.id(id), Set.of(events), effect));
        }

        private Builder addNoEffect(String id) {
            return register(new TokenDefinition(RuneLegendMod.id(id), Set.of(), NO_EFFECT));
        }

        public TokenRegistry build() {
            if (built) throw new IllegalStateException("Token registry is frozen");
            built = true;
            return new TokenRegistry(entries);
        }
    }
}
