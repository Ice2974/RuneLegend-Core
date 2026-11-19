package com.runelegend.token;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TokenRegistryTest {
    private static final List<String> BUILT_INS = List.of(
            "soul_siphon", "echo", "venomous_tip",
            "poisoned_arrow", "asura_form", "resonance_healing", "vernal_tide", "arrogance", "execution_echo", "retribution",
            "substitute", "full_blessing", "fountain_of_life");

    @Test void builtInRegistryContainsEveryTokenWithAUniqueId() {
        var registry = TokenRegistry.builtIn();
        var ids = new LinkedHashSet<Identifier>();
        registry.all().forEach(definition -> ids.add(definition.id()));
        assertEquals(BUILT_INS.size(), ids.size(), "Built-in token IDs are unique");
        assertEquals(registry.all().size(), ids.size(), "Every registered token has a distinct ID");
        for (String path : BUILT_INS) {
            assertNotNull(registry.get(id(path)), path + " is registered");
        }
    }

    @Test void implementedTokensKeepTheirEventKinds() {
        var registry = TokenRegistry.builtIn();
        assertEquals(Set.of(TokenEvent.Kind.KILL), registry.get(id("soul_siphon")).events());
        assertEquals(Set.of(TokenEvent.Kind.KILL), registry.get(id("echo")).events());
        assertEquals(Set.of(TokenEvent.Kind.MELEE), registry.get(id("venomous_tip")).events());
        assertEquals(Set.of(TokenEvent.Kind.ARROW), registry.get(id("poisoned_arrow")).events());
        assertEquals(Set.of(TokenEvent.Kind.KILL, TokenEvent.Kind.MELEE), registry.get(id("asura_form")).events());
        assertEquals(Set.of(TokenEvent.Kind.GUARD_SUCCESS), registry.get(id("resonance_healing")).events());
        assertEquals(Set.of(TokenEvent.Kind.MELEE), registry.get(id("vernal_tide")).events());
        assertEquals(Set.of(TokenEvent.Kind.HEALTH, TokenEvent.Kind.KILL, TokenEvent.Kind.MELEE),
                registry.get(id("arrogance")).events());
        assertEquals(Set.of(TokenEvent.Kind.MELEE), registry.get(id("execution_echo")).events());
        assertEquals(Set.of(TokenEvent.Kind.HURT, TokenEvent.Kind.MELEE), registry.get(id("retribution")).events());
        assertEquals(Set.of(TokenEvent.Kind.HEALTH), registry.get(id("substitute")).events());
        assertEquals(Set.of(TokenEvent.Kind.HEALTH), registry.get(id("full_blessing")).events());
        assertEquals(Set.of(TokenEvent.Kind.HEALTH), registry.get(id("fountain_of_life")).events());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("runelegend", path);
    }
}
