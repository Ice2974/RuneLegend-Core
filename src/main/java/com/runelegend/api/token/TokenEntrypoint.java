package com.runelegend.api.token;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

import java.util.Set;
import java.util.function.Function;

/** Fabric common entrypoint named {@code runelegend:tokens}. */
@FunctionalInterface
public interface TokenEntrypoint {
    void registerTokens(Registrar registrar);

    interface Registrar {
        /**
         * Registers a token during entrypoint invocation. The codec must encode an object and validate
         * every setting. The factory receives the server's settings and creates one effect per player runtime.
         */
        <S> void register(Identifier id, Codec<S> codec, S defaults, Set<TokenTrigger.Kind> events,
                          Function<S, ? extends TokenEffect> factory);
    }
}
