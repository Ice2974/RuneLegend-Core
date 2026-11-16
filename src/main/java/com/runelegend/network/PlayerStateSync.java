package com.runelegend.network;

import com.runelegend.RuneLegendServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static com.runelegend.network.RuneLegendPayloads.*;

public final class PlayerStateSync {
    private static final class Peer {
        long revision;
        long staminaSent;
        long resyncAt = -20;
        View last;
        long rulesRevision = -1;
    }

    private final RuneLegendServer server;
    private final Map<UUID, Peer> peers = new HashMap<>();

    public PlayerStateSync(RuneLegendServer server) { this.server = server; }

    public void join(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, PlayerSnapshot.TYPE)) return;
        peers.put(player.getUUID(), new Peer());
        rules(player);
        snapshot(player);
    }

    public void leave(ServerPlayer player) { peers.remove(player.getUUID()); }
    public void clear() { peers.clear(); }

    public void rules(ServerPlayer player) {
        if (peers.containsKey(player.getUUID())) ServerPlayNetworking.send(player, new Rules(server.game().getRules()));
    }

    public void flash(ServerPlayer player, Identifier token) {
        var rules = server.game().getRules();
        if (server.tokenEligible(player) && player.isAlive() && peers.containsKey(player.getUUID())) {
            ServerPlayNetworking.send(player, new TokenFlash(token, rules.round()));
        }
    }

    public void resync(ServerPlayer player) {
        var peer = peers.get(player.getUUID());
        if (peer == null || server.tick() - peer.resyncAt < 20) return;
        peer.resyncAt = server.tick();
        rules(player);
        snapshot(player);
    }

    private void snapshot(ServerPlayer player) {
        var peer = peers.get(player.getUUID());
        if (peer == null) return;
        peer.last = current(player);
        peer.staminaSent = server.tick();
        var rules = server.game().getRules();
        peer.rulesRevision = rules.revision();
        ServerPlayNetworking.send(player, new PlayerSnapshot(++peer.revision, server.tick(), peer.last, rules.round(), rules.revision()));
    }

    public void update(ServerPlayer player) {
        if (!server.isCurrent(player)) return;
        var peer = peers.get(player.getUUID());
        if (peer == null || peer.last == null) return;
        if (peer.rulesRevision != server.game().getRules().revision()) {
            snapshot(player);
            return;
        }
        var before = peer.last;
        var after = current(player);
        int mask = 0;
        if (!before.tokens().equals(after.tokens())) mask |= TOKENS;
        if (before.playerState() != after.playerState() || before.spectateNextRound() != after.spectateNextRound()) mask |= IDENTITY;
        var previousMarkers = new HashMap<UUID, DownedMarker>();
        before.downed().forEach(marker -> previousMarkers.put(marker.player(), marker));
        var currentMarkers = new HashMap<UUID, DownedMarker>();
        after.downed().forEach(marker -> currentMarkers.put(marker.player(), marker));
        var upserts = after.downed().stream().filter(marker -> !marker.equals(previousMarkers.get(marker.player()))).toList();
        var removed = before.downed().stream().map(DownedMarker::player).filter(uuid -> !currentMarkers.containsKey(uuid)).toList();
        if (!upserts.isEmpty() || !removed.isEmpty()) mask |= DOWNED;
        if (before.stamina() != after.stamina() && (server.tick() - peer.staminaSent >= 4
                || after.stamina() == 0 || after.stamina() == after.staminaMax())) {
            mask |= STAMINA;
            peer.staminaSent = server.tick();
        }
        if (mask == 0) return;
        long base = peer.revision;
        var patch = new View(after.tokens(), after.stamina(), after.staminaMax(), after.playerState(), after.spectateNextRound(), upserts);
        peer.last = merge(before, patch, mask, removed);
        var rules = server.game().getRules();
        ServerPlayNetworking.send(player, new PlayerDelta(base, ++peer.revision, server.tick(), mask, patch, removed, rules.round(), rules.revision()));
    }

    private View current(ServerPlayer player) {
        var stamina = server.stamina();
        var markers = server.downed().records().stream().filter(record -> record.dimension().equals(player.level().dimension()))
                .sorted(java.util.Comparator.comparing(com.runelegend.player.DownedService.DownedRecord::playerUuid))
                .map(record -> new DownedMarker(record.playerUuid(), record.round(), record.sequence(), record.dimension().identifier(),
                        record.position().x, record.position().y, record.position().z,
                        (int) Math.max(0, (record.expiresAtTick() - server.tick() + 19) / 20))).toList();
        return new View(server.tokens().views(player), stamina.stamina(player), stamina.config().max(),
                server.playerStates().state(player.getUUID()), server.playerStates().spectateNextRound(player.getUUID()), markers);
    }
}
