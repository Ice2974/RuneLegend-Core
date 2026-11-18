package com.runelegend.test;

import com.mojang.authlib.GameProfile;
import com.runelegend.RuneLegendMod;
import com.runelegend.client.network.ClientStateCache;
import com.runelegend.token.TokenEngine;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class SubstituteClientGameTest implements FabricClientGameTest {
    private static final net.minecraft.resources.Identifier SUBSTITUTE = RuneLegendMod.id("substitute");

    @Override public void runTest(ClientGameTestContext context) {
        var properties = new Properties();
        properties.setProperty("online-mode", "false");
        properties.setProperty("level-type", "minecraft:flat");
        var modelId = new AtomicInteger();
        var profile = new AtomicReference<GameProfile>();
        try (var dedicated = context.worldBuilder().createServer(properties); var connection = dedicated.connect()) {
            context.waitFor(client -> client.player != null && ClientStateCache.INSTANCE.view() != null);
            dedicated.runCommand("runelegend admin @p clear");
            dedicated.runCommand("runelegend admin @p equip runelegend:substitute");
            dedicated.runCommand("difficulty normal");
            dedicated.runCommand("time set noon");
            dedicated.runCommand("runelegend game start");
            context.waitFor(client -> hasFlag(TokenEngine.READY));
            dedicated.runOnServer(server -> {
                var player = connection.getServerPlayer();
                player.getFoodData().setFoodLevel(10);
                player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(32);
                player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                player.setYRot(180);
                player.setYHeadRot(180);
                profile.set(player.getGameProfile());
                player.setHealth(16);
                var models = player.level().getEntitiesOfClass(Mannequin.class, player.getBoundingBox().inflate(0.5));
                modelId.set(models.getFirst().getId());
            });
            dedicated.runCommand("execute as @p at @s run tp @s ~ ~ ~-4 0 0");
            connection.waitForChunksRender();
            context.waitFor(client -> client.level != null && client.level.getEntity(modelId.get()) instanceof Mannequin model
                    && model.getProfile().partialProfile().equals(profile.get())
                    && model.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE)
                    && model.getMaxHealth() == 32 && model.getHealth() == 32 && hasFlag(TokenEngine.ACTIVE));
            context.takeScreenshot("runelegend-substitute");

            dedicated.runOnServer(server -> {
                var level = connection.getServerPlayer().level();
                var model = (Mannequin) level.getEntity(modelId.get());
                model.hurtServer(level, level.damageSources().genericKill(), 1000);
            });
            context.waitFor(client -> hasFlag(TokenEngine.COOLDOWN));
            context.runOnClient(client -> {
                var cache = ClientStateCache.INSTANCE;
                long ticks = view().cooldownUntil() - cache.serverTick();
                if (ticks <= 0 || ticks > 800) throw new AssertionError("Unexpected substitute cooldown: " + ticks);
            });
        }
    }

    private static TokenEngine.View view() {
        var current = ClientStateCache.INSTANCE.view();
        return current == null ? null : current.tokens().stream().filter(token -> token.id().equals(SUBSTITUTE))
                .findFirst().orElse(null);
    }

    private static boolean hasFlag(int flag) {
        var token = view();
        return token != null && (token.flags() & flag) != 0;
    }
}
