package com.runelegend.game;

import com.runelegend.RuneLegendMod;
import com.runelegend.api.PlayerStateEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.Locale;
import java.util.UUID;

public final class PlayerStateNotifications {
    private static final Identifier DOWNED = RuneLegendMod.id("player_downed");
    private static final Identifier REVIVED = RuneLegendMod.id("player_revived");
    private static final Identifier ELIMINATED = RuneLegendMod.id("player_eliminated");

    private PlayerStateNotifications() {}

    public static void downed(MinecraftServer server, PlayerStateEvents.Downed event) {
        FunctionNotifications.publish(server, DOWNED, () -> PlayerStateEvents.DOWNED.invoker().onDowned(server, event),
                () -> arguments(event.round(), event.playerId(), event.anchor()));
    }

    public static void revived(MinecraftServer server, PlayerStateEvents.Revived event) {
        FunctionNotifications.publish(server, REVIVED, () -> PlayerStateEvents.REVIVED.invoker().onRevived(server, event), () -> {
            var args = arguments(event.round(), event.playerId(), event.anchor());
            args.putString("rescuer_uuid", event.rescuerId().map(UUID::toString).orElse(""));
            return args;
        });
    }

    public static void eliminated(MinecraftServer server, PlayerStateEvents.Eliminated event) {
        FunctionNotifications.publish(server, ELIMINATED, () -> PlayerStateEvents.ELIMINATED.invoker().onEliminated(server, event), () -> {
            var args = arguments(event.round(), event.playerId(), event.anchor().orElse(null));
            args.putString("reason", event.reason().name().toLowerCase(Locale.ROOT));
            args.putString("previous_identity", event.previousIdentity().name());
            return args;
        });
    }

    private static CompoundTag arguments(long round, UUID playerId, PlayerStateEvents.DownedAnchor anchor) {
        var args = new CompoundTag();
        args.putLong("round", round);
        args.putString("player_uuid", playerId.toString());
        var location = new CompoundTag();
        if (anchor != null) {
            location.putLong("sequence", anchor.sequence());
            location.putString("dimension", anchor.dimension().identifier().toString());
            location.putDouble("x", anchor.position().x);
            location.putDouble("y", anchor.position().y);
            location.putDouble("z", anchor.position().z);
            location.putFloat("yaw", anchor.yaw());
            location.putFloat("pitch", anchor.pitch());
        }
        args.put("anchor", location);
        return args;
    }
}
