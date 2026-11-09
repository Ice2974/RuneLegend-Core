package com.runelegend.network;

import com.runelegend.token.TokenEngine;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.runelegend.network.RuneLegendPayloads.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolStateTest {
    private static final TokenEngine.View TOKEN =
            new TokenEngine.View(Identifier.parse("runelegend:echo"), TokenEngine.ACTIVE, 0, 50, 0);

    @Test void staminaDeltaDoesNotReplaceEquipment() {
        var previous = new View(List.of(TOKEN), 20000, 20000);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PlayerDelta.CODEC.encode(buffer, new PlayerDelta(3, 4, 21, STAMINA, new View(List.of(), 19500, 20000)));
            var packet = PlayerDelta.CODEC.decode(buffer);
            var merged = merge(previous, packet.state(), packet.mask());
            assertEquals(new View(List.of(TOKEN), 19500, 20000), merged);
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test void staminaDeltaCarriesTheConfiguredCap() {
        var previous = new View(List.of(TOKEN), 20000, 20000);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PlayerDelta.CODEC.encode(buffer, new PlayerDelta(3, 4, 21, STAMINA, new View(List.of(), 9000, 12000)));
            var packet = PlayerDelta.CODEC.decode(buffer);
            var merged = merge(previous, packet.state(), packet.mask());
            assertEquals(new View(List.of(TOKEN), 9000, 12000), merged);
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test void tokenDeltaKeepsStaminaAndCap() {
        var previous = new View(List.of(TOKEN), 18000, 20000);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PlayerDelta.CODEC.encode(buffer, new PlayerDelta(3, 4, 21, TOKENS, new View(List.of(), 0, 0)));
            var packet = PlayerDelta.CODEC.decode(buffer);
            var merged = merge(previous, packet.state(), packet.mask());
            assertEquals(new View(List.of(), 18000, 20000), merged);
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test void snapshotRoundTripsStaminaAndCap() {
        var snapshot = new PlayerSnapshot(7, 42, new View(List.of(TOKEN), 12345, 20000));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PlayerSnapshot.CODEC.encode(buffer, snapshot);
            assertEquals(snapshot, PlayerSnapshot.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test void staminaUsesTheUnsignedShortRangeOnTheWire() {
        var snapshot = new PlayerSnapshot(1, 2, new View(List.of(), 65535, 65535));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PlayerSnapshot.CODEC.encode(buffer, snapshot);
            assertEquals(snapshot, PlayerSnapshot.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test void snapshotRejectsAnOversizedSlotCount() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarLong(1);
            buffer.writeVarLong(10);
            buffer.writeByte(4);
            assertThrows(IllegalArgumentException.class, () -> PlayerSnapshot.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test void snapshotRejectsStaminaAboveCap() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarLong(1);
            buffer.writeVarLong(10);
            buffer.writeByte(0);
            buffer.writeShort(20001);
            buffer.writeShort(20000);
            assertThrows(IllegalArgumentException.class, () -> PlayerSnapshot.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test void snapshotRejectsZeroStaminaCap() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarLong(1);
            buffer.writeVarLong(10);
            buffer.writeByte(0);
            buffer.writeShort(0);
            buffer.writeShort(0);
            assertThrows(IllegalArgumentException.class, () -> PlayerSnapshot.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
