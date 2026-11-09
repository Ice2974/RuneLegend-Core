package com.runelegend.network;

import com.runelegend.RuneLegendMod;
import com.runelegend.api.GameState;
import com.runelegend.api.RuleSnapshot;
import com.runelegend.player.PlayerTokenData;
import com.runelegend.token.TokenEngine;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public final class RuneLegendPayloads {
    public static final int PROTOCOL = 3;
    public static final int TOKENS = 1, STAMINA = 2, ALL = TOKENS | STAMINA;

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

    /** The current stamina cap travels with the value so the client HUD scales configurable values. */
    public record View(List<TokenEngine.View> tokens, int stamina, int staminaMax) {
        public View { tokens = List.copyOf(tokens); }
    }

    public record PlayerSnapshot(long revision, long tick, View state) implements CustomPacketPayload {
        public static final Type<PlayerSnapshot> TYPE = id("player_snapshot");
        static final StreamCodec<FriendlyByteBuf, PlayerSnapshot> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeVarLong(value.revision); buf.writeVarLong(value.tick); writeView(buf, value.state, ALL);
        }, buf -> new PlayerSnapshot(buf.readVarLong(), buf.readVarLong(), readView(buf, ALL)));
        @Override public Type<PlayerSnapshot> type() { return TYPE; }
    }

    public record PlayerDelta(long base, long revision, long tick, int mask, View state) implements CustomPacketPayload {
        public static final Type<PlayerDelta> TYPE = id("player_delta");
        static final StreamCodec<FriendlyByteBuf, PlayerDelta> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeVarLong(value.base); buf.writeVarLong(value.revision); buf.writeVarLong(value.tick);
            buf.writeByte(value.mask); writeView(buf, value.state, value.mask);
        }, buf -> {
            long base = buf.readVarLong(), revision = buf.readVarLong(), tick = buf.readVarLong();
            int mask = buf.readUnsignedByte();
            if (mask == 0 || (mask & ~ALL) != 0) throw new IllegalArgumentException("Invalid RuneLegend delta");
            return new PlayerDelta(base, revision, tick, mask, readView(buf, mask));
        });
        @Override public Type<PlayerDelta> type() { return TYPE; }
    }

    public static View merge(View old, View incoming, int mask) {
        boolean staminaChanged = (mask & STAMINA) != 0;
        return new View((mask & TOKENS) != 0 ? incoming.tokens : old.tokens,
                staminaChanged ? incoming.stamina : old.stamina,
                staminaChanged ? incoming.staminaMax : old.staminaMax);
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
        if ((mask & STAMINA) == 0) return new View(tokens, 0, 0);
        int stamina = buf.readUnsignedShort();
        int staminaMax = buf.readUnsignedShort();
        if (staminaMax == 0 || stamina > staminaMax) throw new IllegalArgumentException("Invalid RuneLegend stamina");
        return new View(tokens, stamina, staminaMax);
    }
}
