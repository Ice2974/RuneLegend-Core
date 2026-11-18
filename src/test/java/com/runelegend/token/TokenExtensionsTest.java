package com.runelegend.token;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.runelegend.api.token.TokenEffect;
import com.runelegend.api.token.TokenEntrypoint;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TokenExtensionsTest {
    private static final Identifier FIRST = Identifier.parse("first:echo");
    private static final Identifier SECOND = Identifier.parse("second:echo");
    private static final Codec<Integer> SETTINGS = Codec.intRange(1, 10).fieldOf("ticks").codec();

    @Test void samePathUsesFullIdAndRegistrationFreezes() {
        var reference = new AtomicReference<TokenEntrypoint.Registrar>();
        var extensions = TokenExtensions.freeze(registrar -> {
            reference.set(registrar);
            register(registrar, FIRST, 2);
            register(registrar, SECOND, 7);
        });
        assertThrows(IllegalStateException.class, () -> register(reference.get(), Identifier.parse("third:echo"), 5));
        var defaults = new JsonObject();
        extensions.writeDefaults(defaults);
        assertEquals(2, defaults.getAsJsonObject(FIRST.toString()).get("ticks").getAsInt());
        assertEquals(7, defaults.getAsJsonObject(SECOND.toString()).get("ticks").getAsInt());
        var registry = TokenRegistry.create(TokenConfig.DEFAULT, extensions, defaults);
        assertEquals("token.first.echo", registry.get(FIRST).name().getString());
        assertEquals("token.second.echo.description", registry.get(SECOND).description().getString());
        assertEquals("Echo", registry.get(Identifier.parse("runelegend:echo")).name().getString());
    }

    @Test void rejectsDuplicateIdsAndInvalidDefaults() {
        var duplicate = assertThrows(IllegalArgumentException.class, () -> TokenExtensions.freeze(registrar -> {
            register(registrar, FIRST, 2);
            register(registrar, FIRST, 3);
        }));
        assertTrue(duplicate.getMessage().contains(FIRST.toString()));
        assertThrows(RuntimeException.class, () -> TokenExtensions.freeze(registrar -> register(registrar, FIRST, 0)));
        assertThrows(IllegalArgumentException.class, () -> TokenExtensions.freeze(registrar ->
                registrar.register(FIRST, Codec.INT, 2, Set.of(), _ -> new TokenEffect() {})));
        assertThrows(IllegalArgumentException.class, () -> TokenExtensions.freeze(registrar ->
                register(registrar, Identifier.parse("runelegend:echo"), 2)));
    }

    private static void register(TokenEntrypoint.Registrar registrar, Identifier id, int ticks) {
        registrar.register(id, SETTINGS, ticks, Set.of(), _ -> new TokenEffect() {});
    }
}
