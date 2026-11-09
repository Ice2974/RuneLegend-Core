package com.runelegend.token;

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
        return new Builder()
                .add("sharpened_tip", new SharpenedTipEffect(config.sharpenedTip()), MELEE)
                .add("soul_siphon", new SoulSiphonEffect(config.soulSiphon()), KILL)
                .add("echo", new EchoEffect(config.echo()), KILL)
                .add("immediate_counter", new ImmediateCounterEffect(config.immediateCounter()), GUARD_SUCCESS, MELEE)
                // Registered so the token system can recognise, equip, store and sync them.
                // Their effects are intentionally left unimplemented for now.
                .addNoEffect("venomous_tip")
                .addNoEffect("ghost_bane")
                .addNoEffect("phantom_wolf")
                .addNoEffect("substitute")
                .addNoEffect("wet_doom_arrow")
                .addNoEffect("fountain")
                .addNoEffect("full_blessing")
                .addNoEffect("retribution")
                .addNoEffect("trusting_sacrifice")
                .addNoEffect("group_taunt")
                .addNoEffect("revival")
                .addNoEffect("execution_clone")
                .addNoEffect("surging_fury")
                .addNoEffect("divine_healing")
                .addNoEffect("conjugate_healing")
                .addNoEffect("arrogance")
                .addNoEffect("retreat_to_advance")
                .build();
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
