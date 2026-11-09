package com.runelegend.token;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TokenRegistryTest {
    private static final List<String> IMPLEMENTED = List.of(
            "sharpened_tip", "soul_siphon", "echo", "immediate_counter");
    private static final List<String> REGISTERED_ONLY = List.of(
            "venomous_tip", "ghost_bane", "phantom_wolf", "substitute", "wet_doom_arrow",
            "spring_water", "full_blessing", "retribution", "trusting_sacrifice",
            "group_taunt", "revival", "execution_clone", "surging_fury", "divine_healing",
            "conjugate_healing", "arrogance", "retreat_to_advance");

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
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("runelegend", path);
    }
}
