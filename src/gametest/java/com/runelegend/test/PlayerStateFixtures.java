package com.runelegend.test;

import com.mojang.authlib.GameProfile;
import com.runelegend.RuneLegendServer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

public final class PlayerStateFixtures {
    public static void sneak(net.minecraft.server.level.ServerPlayer player, boolean sneaking) {
        player.connection.handlePlayerInput(new net.minecraft.network.protocol.game.ServerboundPlayerInputPacket(
                new net.minecraft.world.entity.player.Input(false, false, false, false, false, sneaking, false)));
    }
    private PlayerStateFixtures() {}
    public static ServerPlayer join(MinecraftServer minecraft, ServerLevel level, UUID uuid) {
        return join(minecraft, level, uuid, null);
    }
    public static ServerPlayer join(MinecraftServer minecraft, ServerLevel level, UUID uuid, Vec3 savedPosition) {
        return join(minecraft, level, uuid, "state-fixture", savedPosition);
    }
    static ServerPlayer join(MinecraftServer minecraft, ServerLevel level, UUID uuid, String name, Vec3 savedPosition) {
        var player = new ServerPlayer(minecraft, level, new GameProfile(uuid, name), ClientInformation.createDefault()) {
            @Override public boolean isClientAuthoritative() { return false; }
        };
        if (savedPosition != null) player.setPos(savedPosition);
        new ServerGamePacketListenerImpl(minecraft, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.initInventoryMenu();
        RuneLegendServer.find(minecraft).join(player);
        return player;
    }
}
