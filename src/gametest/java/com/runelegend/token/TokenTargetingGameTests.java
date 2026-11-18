package com.runelegend.token;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;

public final class TokenTargetingGameTests {
    private static final Identifier PACK = Identifier.parse("runelegend_test:instance_targets");
    private static final String ENEMY_TAG = "runelegend_test.enemy";

    @GameTest
    public void reloadFiltersExistingEntitiesByTypeAndInstance(GameTestHelper test) {
        var server = test.getLevel().getServer();
        var repository = server.getPackRepository();
        var originalPacks = List.copyOf(repository.getSelectedIds());
        // The GameTest server selects packs available at boot. Register this pack only for the test.
        var container = FabricLoader.getInstance().getModContainer(PACK.getNamespace()).orElseThrow();
        ResourceLoader.registerBuiltinPack(PACK, "resourcepacks/instance_targets", container, PackActivationType.NORMAL);
        repository.reload();
        test.assertTrue(repository.isAvailable(PACK.toString()), "The target fixture pack is available for reload");
        var withFilter = new ArrayList<>(originalPacks);
        withFilter.add(PACK.toString());
        var boss = test.spawnWithNoFreeWill(EntityTypes.WITHER, 1, 2, 1);
        var npc = test.spawnWithNoFreeWill(EntityTypes.WITHER, 3, 2, 1);
        var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 3);
        var pig = test.spawnWithNoFreeWill(EntityTypes.PIG, 3, 2, 3);
        var player = (ServerPlayer) test.makeMockServerPlayer(GameType.SURVIVAL);
        var substitute = new SubstituteMannequin(player);
        boss.addTag(ENEMY_TAG);
        zombie.addTag(ENEMY_TAG);
        pig.addTag(ENEMY_TAG);
        player.addTag(ENEMY_TAG);
        substitute.addTag(ENEMY_TAG);

        test.assertTrue(TokenTargeting.enemy(boss) && TokenTargeting.enemy(npc) && !TokenTargeting.enemy(pig),
                "An empty type tag and absent predicate use vanilla Enemy");
        try {
            server.reloadResources(withFilter).join();
            test.assertTrue(TokenTargeting.enemy(boss) && !TokenTargeting.enemy(npc),
                    "A predicate distinguishes already spawned entities of the same type");
            test.assertTrue(TokenTargeting.boss(npc), "Boss classification still uses the boss type tag");
            test.assertFalse(TokenTargeting.enemy(zombie), "The predicate cannot admit a type omitted from the nonempty tag");
            test.assertTrue(TokenTargeting.enemy(pig), "A tagged non-Enemy type can pass the predicate");
            test.assertFalse(TokenTargeting.enemy(player), "Players are excluded even when both filters match");
            test.assertFalse(TokenTargeting.enemy(substitute), "Substitutes are excluded even when both filters match");
            npc.addTag(ENEMY_TAG);
            test.assertTrue(TokenTargeting.enemy(npc), "The next evaluation sees a changed entity tag");
            npc.removeTag(ENEMY_TAG);
        } finally {
            server.reloadResources(originalPacks).join();
        }
        test.assertTrue(TokenTargeting.enemy(npc) && TokenTargeting.enemy(zombie) && !TokenTargeting.enemy(pig),
                "Removing the pack restores the original Enemy logic");
        test.succeed();
    }
}
