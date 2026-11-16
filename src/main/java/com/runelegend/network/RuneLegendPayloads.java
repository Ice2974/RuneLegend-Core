package com.runelegend.network;

import com.runelegend.RuneLegendMod;
import com.runelegend.api.GameState;
import com.runelegend.api.RuleSnapshot;
import com.runelegend.player.PlayerTokenData;
import com.runelegend.player.PlayerState;
import com.runelegend.token.TokenEngine;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.LinkedHashMap;

public final class RuneLegendPayloads {
    public static final int PROTOCOL = 5;
    public static final int TOKENS = 1, STAMINA = 2, IDENTITY = 4, DOWNED = 8, ALL = TOKENS | STAMINA | IDENTITY | DOWNED;
    private static final int MAX_MARKERS = 4096;

    private RuneLegendPayloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> id(String name) {
        return new CustomPacketPayload.Type<>(RuneLegendMod.id(name));
    }

    public static void register() {
        PayloadTypeRegistry.clientboundConfiguration().register(Hello.TYPE, Hello.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(HelloAck.TYPE, HelloAck.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StateResync.TYPE, StateResync.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Rules.TYPE, Rules.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PlayerSnapshot.TYPE, PlayerSnapshot.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PlayerDelta.TYPE, PlayerDelta.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TokenFlash.TYPE, TokenFlash.CODEC);
    }

    public record Hello(int version, long nonce) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = id("hello");
        static final StreamCodec<FriendlyByteBuf, Hello> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeVarInt(value.version); buf.writeLong(value.nonce); },
                buf -> new Hello(buf.readVarInt(), buf.readLong()));
        @Override public Type<Hello> type() { return TYPE; }
    }

    public record HelloAck(int version, long nonce) implements CustomPacketPayload {
        public static final Type<HelloAck> TYPE = id("hello_ack");
        static final StreamCodec<FriendlyByteBuf, HelloAck> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeVarInt(value.version); buf.writeLong(value.nonce); },
                buf -> new HelloAck(buf.readVarInt(), buf.readLong()));
        @Override public Type<HelloAck> type() { return TYPE; }
    }

    public record StateResync() implements CustomPacketPayload {
        public static final Type<StateResync> TYPE = id("state_resync");
        static final StreamCodec<FriendlyByteBuf, StateResync> CODEC = StreamCodec.unit(new StateResync());
        @Override public Type<StateResync> type() { return TYPE; }
    }

    public record Rules(RuleSnapshot rules) implements CustomPacketPayload {
        public static final Type<Rules> TYPE = id("rules");
        static final StreamCodec<FriendlyByteBuf, Rules> CODEC = StreamCodec.of((buf, value) -> {
            var rules = value.rules;
            buf.writeBoolean(rules.running());
            buf.writeVarLong(rules.round());
            buf.writeVarLong(rules.revision());
        }, buf -> new Rules(new RuleSnapshot(buf.readBoolean() ? GameState.RUNNING : GameState.WAITING,
                buf.readVarLong(), buf.readVarLong())));
        @Override public Type<Rules> type() { return TYPE; }
    }

    /** A server-authoritative death anchor, independent of the spectator's current position. */
    public record DownedMarker(UUID player, long round, long sequence, Identifier dimension,
                               double x, double y, double z, int secondsRemaining) {
        public DownedMarker {
            if (round < 0 || sequence < 1 || secondsRemaining < 0 || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Invalid downed marker");
        }
    }

    public record View(List<TokenEngine.View> tokens, int stamina, int staminaMax, PlayerState playerState,
                       boolean spectateNextRound, List<DownedMarker> downed) {
        public View { tokens = List.copyOf(tokens); downed = List.copyOf(downed); }
        public View(List<TokenEngine.View> tokens, int stamina, int staminaMax) { this(tokens, stamina, staminaMax, null, false, List.of()); }
    }

    public record PlayerSnapshot(long revision, long tick, View state, long round, long rulesRevision) implements CustomPacketPayload {
        public PlayerSnapshot(long revision, long tick, View state) { this(revision, tick, state, 0, 0); }
        public static final Type<PlayerSnapshot> TYPE = id("player_snapshot");
        static final StreamCodec<FriendlyByteBuf, PlayerSnapshot> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeVarLong(value.revision); buf.writeVarLong(value.tick); writeView(buf, value.state, ALL);
            buf.writeVarLong(value.round); buf.writeVarLong(value.rulesRevision);
        }, buf -> new PlayerSnapshot(buf.readVarLong(), buf.readVarLong(), readView(buf, ALL), buf.readVarLong(), buf.readVarLong()));
        @Override public Type<PlayerSnapshot> type() { return TYPE; }
    }

    public record PlayerDelta(long base, long revision, long tick, int mask, View state, List<UUID> removed,
                              long round, long rulesRevision) implements CustomPacketPayload {
        public PlayerDelta { removed = List.copyOf(removed); }
        public PlayerDelta(long base, long revision, long tick, int mask, View state) { this(base, revision, tick, mask, state, List.of(), 0, 0); }
        public static final Type<PlayerDelta> TYPE = id("player_delta");
        static final StreamCodec<FriendlyByteBuf, PlayerDelta> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeVarLong(value.base); buf.writeVarLong(value.revision); buf.writeVarLong(value.tick);
            buf.writeByte(value.mask); writeView(buf, value.state, value.mask);
            buf.writeVarInt(value.removed.size());
            value.removed.forEach(buf::writeUUID);
            buf.writeVarLong(value.round); buf.writeVarLong(value.rulesRevision);
        }, buf -> {
            long base = buf.readVarLong(), revision = buf.readVarLong(), tick = buf.readVarLong();
            int mask = buf.readUnsignedByte();
            if (mask == 0 || (mask & ~ALL) != 0) throw new IllegalArgumentException("Invalid RuneLegend delta");
            var view = readView(buf, mask);
            int size = markerCount(buf);
            var removed = new ArrayList<UUID>(size);
            for (int i = 0; i < size; i++) removed.add(buf.readUUID());
            if (size > 0 && (mask & DOWNED) == 0) throw new IllegalArgumentException("Marker removal without mask");
            return new PlayerDelta(base, revision, tick, mask, view, removed, buf.readVarLong(), buf.readVarLong());
        });
        @Override public Type<PlayerDelta> type() { return TYPE; }
    }

    public record TokenFlash(Identifier tokenId, long round) implements CustomPacketPayload {
        public static final Type<TokenFlash> TYPE = id("token_flash");
        static final StreamCodec<FriendlyByteBuf, TokenFlash> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeIdentifier(value.tokenId); buf.writeVarLong(value.round); },
                buf -> new TokenFlash(buf.readIdentifier(), buf.readVarLong()));
        @Override public Type<TokenFlash> type() { return TYPE; }
    }

    public static View merge(View old, View incoming, int mask) {
        return merge(old, incoming, mask, List.of());
    }

    public static View merge(View old, View incoming, int mask, List<UUID> removed) {
        boolean staminaChanged = (mask & STAMINA) != 0;
        var markers = new LinkedHashMap<UUID, DownedMarker>();
        old.downed.forEach(marker -> markers.put(marker.player(), marker));
        if ((mask & DOWNED) != 0) {
            removed.forEach(markers::remove);
            incoming.downed.forEach(marker -> markers.put(marker.player(), marker));
        }
        return new View((mask & TOKENS) != 0 ? incoming.tokens : old.tokens,
                staminaChanged ? incoming.stamina : old.stamina,
                staminaChanged ? incoming.staminaMax : old.staminaMax,
                (mask & IDENTITY) != 0 ? incoming.playerState : old.playerState,
                (mask & IDENTITY) != 0 ? incoming.spectateNextRound : old.spectateNextRound, List.copyOf(markers.values()));
    }

    private static void writeView(FriendlyByteBuf buf, View view, int mask) {
        if ((mask & TOKENS) != 0) {
            buf.writeByte(view.tokens.size());
            for (var token : view.tokens) {
                buf.writeIdentifier(token.id());
                buf.writeByte(token.flags());
                buf.writeVarLong(token.triggeredUntil());
                buf.writeVarLong(token.activeUntil());
                buf.writeVarLong(token.cooldownUntil());
            }
        }
        if ((mask & STAMINA) != 0) {
            buf.writeShort(view.stamina);
            buf.writeShort(view.staminaMax);
        }
        if ((mask & IDENTITY) != 0) {
            buf.writeByte(view.playerState == null ? 0 : view.playerState.ordinal() + 1);
            buf.writeBoolean(view.spectateNextRound);
        }
        if ((mask & DOWNED) != 0) {
            buf.writeVarInt(view.downed.size());
            for (var marker : view.downed) {
                buf.writeUUID(marker.player); buf.writeVarLong(marker.round); buf.writeVarLong(marker.sequence);
                buf.writeIdentifier(marker.dimension); buf.writeDouble(marker.x); buf.writeDouble(marker.y); buf.writeDouble(marker.z);
                buf.writeVarInt(marker.secondsRemaining);
            }
        }
    }

    private static View readView(FriendlyByteBuf buf, int mask) {
        List<TokenEngine.View> tokens = List.of();
        if ((mask & TOKENS) != 0) {
            int size = buf.readUnsignedByte();
            if (size > PlayerTokenData.MAX_SLOTS) throw new IllegalArgumentException("Invalid RuneLegend slot count");
            var list = new ArrayList<TokenEngine.View>(size);
            for (int i = 0; i < size; i++) {
                list.add(new TokenEngine.View(buf.readIdentifier(), buf.readUnsignedByte(),
                        buf.readVarLong(), buf.readVarLong(), buf.readVarLong()));
            }
            tokens = list;
        }
        int stamina = 0, staminaMax = 0;
        if ((mask & STAMINA) != 0) {
            stamina = buf.readUnsignedShort();
            staminaMax = buf.readUnsignedShort();
            if (staminaMax == 0 || stamina > staminaMax) throw new IllegalArgumentException("Invalid RuneLegend stamina");
        }
        PlayerState identity = null;
        boolean spectate = false;
        if ((mask & IDENTITY) != 0) {
            int code = buf.readUnsignedByte();
            if (code > PlayerState.values().length) throw new IllegalArgumentException("Invalid player state");
            identity = code == 0 ? null : PlayerState.values()[code - 1];
            spectate = buf.readBoolean();
        }
        var markers = new ArrayList<DownedMarker>();
        if ((mask & DOWNED) != 0) {
            int size = markerCount(buf);
            var seen = new java.util.HashSet<UUID>();
            for (int i = 0; i < size; i++) {
                var marker = new DownedMarker(buf.readUUID(), buf.readVarLong(), buf.readVarLong(), buf.readIdentifier(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt());
                if (!seen.add(marker.player())) throw new IllegalArgumentException("Duplicate downed player");
                markers.add(marker);
            }
        }
        return new View(tokens, stamina, staminaMax, identity, spectate, markers);
    }

    private static int markerCount(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_MARKERS) throw new IllegalArgumentException("Invalid marker count");
        return size;
    }
}
