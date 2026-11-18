package com.runelegend.token;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.runelegend.RuneLegendMod;
import com.runelegend.api.token.TokenEffect;
import com.runelegend.api.token.TokenEntrypoint;
import com.runelegend.api.token.TokenTrigger;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Frozen extension definitions, shared by the logical servers in this process. */
public final class TokenExtensions {
    public static final TokenExtensions EMPTY = new TokenExtensions(Map.of());
    private final Map<String, Registration<?>> entries;

    private TokenExtensions(Map<String, Registration<?>> entries) {
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    public static TokenExtensions load() {
        return freeze(registrar -> FabricLoader.getInstance().invokeEntrypoints("runelegend:tokens",
                TokenEntrypoint.class, entrypoint -> entrypoint.registerTokens(registrar)));
    }

    static TokenExtensions freeze(Consumer<TokenEntrypoint.Registrar> register) {
        var registrar = new Registrar();
        try {
            register.accept(registrar);
            return new TokenExtensions(registrar.entries);
        } finally {
            registrar.frozen = true;
            registrar.entries.clear();
        }
    }

    boolean contains(String id) { return entries.containsKey(id); }

    public void writeDefaults(JsonObject tokens) {
        entries.forEach((id, entry) -> tokens.add(id, entry.encodedDefaults().deepCopy()));
    }

    public JsonObject readSections(JsonObject tokens) {
        var sections = new JsonObject();
        entries.keySet().forEach(id -> {
            if (tokens.has(id)) sections.add(id, tokens.get(id));
        });
        return sections;
    }

    void install(TokenRegistry.Builder registry, JsonObject tokens) {
        entries.forEach((id, entry) -> registry.register(entry.definition(tokens)));
    }

    private static final class Registrar implements TokenEntrypoint.Registrar {
        private final Thread registrationThread = Thread.currentThread();
        private final Map<String, Registration<?>> entries = new LinkedHashMap<>();
        private boolean frozen;

        @Override public <S> void register(Identifier id, Codec<S> codec, S defaults, Set<TokenTrigger.Kind> events,
                                          Function<S, ? extends TokenEffect> factory) {
            if (Thread.currentThread() != registrationThread) throw new IllegalStateException("Expected the token entrypoint thread");
            if (frozen) throw new IllegalStateException("Token registry is frozen");
            if (id.getPath().isEmpty() || id.getNamespace().isEmpty() || id.getNamespace().equals(RuneLegendMod.MOD_ID)
                    || id.getNamespace().equals("minecraft")) {
                throw new IllegalArgumentException("Expected an addon token ID outside the reserved namespaces: " + id);
            }
            if (entries.containsKey(id.toString())) throw new IllegalArgumentException("Duplicate token: " + id);
            Objects.requireNonNull(defaults, "defaults");
            Objects.requireNonNull(factory, "factory");
            var encoded = codec.encodeStart(JsonOps.INSTANCE, defaults).getOrThrow();
            if (!encoded.isJsonObject()) throw new IllegalArgumentException("Expected object settings for token " + id);
            var validatedDefaults = codec.parse(JsonOps.INSTANCE, encoded).getOrThrow();
            var kinds = events.stream().map(kind -> TokenEvent.Kind.valueOf(kind.name())).collect(Collectors.toUnmodifiableSet());
            entries.put(id.toString(), new Registration<>(id, codec, validatedDefaults, encoded.getAsJsonObject(), kinds, factory));
        }
    }

    private record Registration<S>(Identifier id, Codec<S> codec, S defaults, JsonObject encodedDefaults,
                                   Set<TokenEvent.Kind> events, Function<S, ? extends TokenEffect> factory) {
        TokenDefinition definition(JsonObject tokens) {
            var settings = TokenConfig.section(tokens, id.toString(), codec, defaults);
            return new TokenDefinition(id, events, new ExtensionTokenEffect(id, () -> factory.apply(settings)));
        }
    }
}
