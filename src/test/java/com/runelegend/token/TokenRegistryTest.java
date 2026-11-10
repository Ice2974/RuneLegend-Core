package com.runelegend.token;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TokenRegistryTest {
    private static final List<String> IMPLEMENTED = List.of(
            "sharpened_tip", "soul_siphon", "echo", "immediate_counter", "venomous_tip");
    private static final List<String> REGISTERED_ONLY = List.of(
            "ghost_bane", "substitute", "poisoned_arrow",
            "fountain", "full_blessing", "retribution",
            "asura_form", "vernal_tide", "execution_momentum", "surging_fury",
            "resonance_healing", "arrogance", "deflective_backlash");

    @Test void builtInRegistryContainsEveryTokenWithAUniqueId() {
        var registry = TokenRegistry.builtIn();
        var ids = new LinkedHashSet<Identifier>();
        registry.all().forEach(definition -> ids.add(definition.id()));
        assertEquals(IMPLEMENTED.size() + REGISTERED_ONLY.size(), ids.size(), "Built-in token IDs are unique");
        assertEquals(registry.all().size(), ids.size(), "Every registered token has a distinct ID");
        for (String path : IMPLEMENTED) {
            assertNotNull(registry.get(id(path)), path + " is still registered");
        }
        for (String path : REGISTERED_ONLY) {
            assertNotNull(registry.get(id(path)), path + " is registered");
        }
    }

    @Test void registeredOnlyTokensHaveNoEventListeners() {
        var registry = TokenRegistry.builtIn();
        for (String path : REGISTERED_ONLY) {
            var definition = registry.get(id(path));
            assertNotNull(definition.effect(), path + " has a no-op effect handler");
            assertEquals(Set.of(), definition.events(), path + " subscribes to no token events");
        }
    }

    @Test void implementedTokensKeepTheirEventKinds() {
        var registry = TokenRegistry.builtIn();
        assertEquals(Set.of(TokenEvent.Kind.MELEE), registry.get(id("sharpened_tip")).events());
        assertEquals(Set.of(TokenEvent.Kind.KILL), registry.get(id("soul_siphon")).events());
        assertEquals(Set.of(TokenEvent.Kind.KILL), registry.get(id("echo")).events());
        assertEquals(Set.of(TokenEvent.Kind.GUARD_SUCCESS, TokenEvent.Kind.MELEE),
                registry.get(id("immediate_counter")).events());
        assertEquals(Set.of(TokenEvent.Kind.MELEE), registry.get(id("venomous_tip")).events());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("runelegend", path);
    }
}
