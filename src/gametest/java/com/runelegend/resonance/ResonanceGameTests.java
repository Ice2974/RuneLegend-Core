package com.runelegend.resonance;

import com.runelegend.RuneLegendServer;
import com.runelegend.test.GuardGameTests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;

import java.util.List;

public final class ResonanceGameTests {
    @GameTest
    public void attributesAndDestructionTagDefaults(GameTestHelper test) {
        test.assertValueEqual(ResonanceAttribute.selectable(),
                List.of(ResonanceAttribute.PROTECTION, ResonanceAttribute.LOST),
                "Only protection and lost are guard-selectable");
        test.assertTrue(!ResonanceAttribute.DESTRUCTION.guardSelectable(),
                "Destruction is not a guard-selectable attribute");
        test.assertTrue(ResonanceTags.destructionEligible(EntityTypes.HUSK),
                "The test data pack marks husks as destruction-eligible");
        test.assertTrue(!ResonanceTags.destructionEligible(EntityTypes.ZOMBIE),
                "Unlisted entity types are not destruction-eligible");
        test.succeed();
    }

    @GameTest
    public void validatedLayerWritesRespectDestructionEligibility(GameTestHelper test) {
        var server = RuneLegendServer.find(test.getLevel().getServer());
        var service = server.resonance();
        var husk = test.spawnWithNoFreeWill(EntityTypes.HUSK, 2, 2, 2);
        var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        var player = GuardGameTests.fixture(test);
        try {
            var all = new ResonanceLayers(1, 2, 3);
            test.assertTrue(service.setLayers(husk, all), "A tagged husk accepts all three layers");
            test.assertValueEqual(service.layers(husk), all, "All three layers are readable");

            test.assertTrue(!service.setLayers(zombie, all), "An untagged zombie cannot gain destruction");
            test.assertValueEqual(service.layers(zombie), ResonanceLayers.NONE,
                    "A rejected write does not persist any layers");
            test.assertTrue(service.setLayers(zombie, new ResonanceLayers(1, 2, 0)),
                    "Protection and lost are not gated by the destruction tag");
            test.assertValueEqual(service.layers(zombie), new ResonanceLayers(1, 2, 0),
                    "Protection and lost are stored on an untagged monster");

            test.assertTrue(!service.setLayers(player, new ResonanceLayers(0, 0, 1)),
                    "Players cannot hold destruction layers");
            test.assertTrue(service.setLayers(player, new ResonanceLayers(1, 1, 0)),
                    "Players can hold protection and lost layers");
            test.assertValueEqual(service.layers(player), new ResonanceLayers(1, 1, 0),
                    "Player layer data uses the same storage");

            test.assertTrue(service.clearLayers(husk), "Cleanup erases a tagged holder's data");
            test.assertValueEqual(service.layers(husk), ResonanceLayers.NONE, "Cleanup removes all three layers");
            test.succeed();
        } finally {
            service.clearLayers(husk);
            service.clearLayers(zombie);
            service.clearLayers(player);
            server.leave(player);
            test.discard(husk);
            test.discard(zombie);
        }
    }
}
