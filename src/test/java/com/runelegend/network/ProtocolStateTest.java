package com.runelegend.network;

import com.runelegend.token.TokenEngine;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static com.runelegend.network.RuneLegendPayloads.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolStateTest {
    @Test void snapshotCarriesIdentityRoundAndDeathAnchors() {
        var marker = marker(new java.util.UUID(0, 1), 15);
        var snapshot = new PlayerSnapshot(7, 42, new View(List.of(TOKEN), 12345, 20000,
                com.runelegend.player.PlayerState.DOWNED, false, List.of(marker)), 3, 5);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PlayerSnapshot.CODEC.encode(buffer, snapshot);
            assertEquals(snapshot, PlayerSnapshot.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
        assertEquals(6, PROTOCOL);
    }

    @Test void markerDeltaUpdatesAndRemovesOnlyNamedAnchors() {
        var a = new java.util.UUID(0, 1); var b = new java.util.UUID(0, 2); var c = new java.util.UUID(0, 3);
        var before = new View(List.of(TOKEN), 15000, 20000, com.runelegend.player.PlayerState.ALIVE,
                false, List.of(marker(a, 15), marker(b, 14)));
        var patch = new PlayerDelta(7, 8, 62, DOWNED, new View(List.of(), 0, 0, null, false,
                List.of(marker(b, 13), marker(c, 15))), List.of(a), 3, 5);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PlayerDelta.CODEC.encode(buffer, patch);
            var decoded = PlayerDelta.CODEC.decode(buffer);
            assertEquals(patch, decoded);
            var after = merge(before, decoded.state(), decoded.mask(), decoded.removed());
            assertEquals(List.of(marker(b, 13), marker(c, 15)), after.downed());
            assertEquals(before.tokens(), after.tokens());
            assertEquals(before.playerState(), after.playerState());
            assertEquals(before.stamina(), after.stamina());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void tabSnapshotAndDeltaPreservePersonalStateAndApplyIndependentRemovals() {
        var alive = UUID.randomUUID();
        var downed = UUID.randomUUID();
        var departing = UUID.randomUUID();
        var before = new View(List.of(TOKEN), 15000, 20000, com.runelegend.player.PlayerState.ALIVE, false,
                List.of(marker(downed, 15)), Map.of(alive, TabStatus.health(20, 20),
                downed, new TabStatus(TabStatus.Kind.DOWNED, 15), departing, TabStatus.ELIMINATED));
        var snapshot = new PlayerSnapshot(7, 42, before, 3, 5);
        var changed = Map.of(alive, TabStatus.health(15.5F, 20), downed, TabStatus.ELIMINATED);
        var delta = new PlayerDelta(7, 8, 62, TAB, new View(List.of(), 0, 0).withTab(changed),
                List.of(), List.of(departing), 3, 5);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PlayerSnapshot.CODEC.encode(buffer, snapshot);
            assertEquals(snapshot, PlayerSnapshot.CODEC.decode(buffer));
            PlayerDelta.CODEC.encode(buffer, delta);
            var decoded = PlayerDelta.CODEC.decode(buffer);
            assertEquals(delta, decoded);
            assertEquals(0, buffer.readableBytes());
            assertEquals(before.withTab(changed), merge(before, decoded));
        } finally {
            buffer.release();
        }
    }

    @Test void tabColorsUseUnroundedHealthAndTheCurrentMaximum() {
        assertEquals(new TabStatus(TabStatus.Kind.HIGH_HEALTH, 16), TabStatus.health(16, 20));
        assertEquals(new TabStatus(TabStatus.Kind.HIGH_HEALTH, 17), TabStatus.health(Math.nextUp(16F), 20));
        assertEquals(new TabStatus(TabStatus.Kind.MEDIUM_HEALTH, 16), TabStatus.health(Math.nextDown(16F), 20));
        assertEquals(new TabStatus(TabStatus.Kind.MEDIUM_HEALTH, 16), TabStatus.health(15.5F, 20));
        assertEquals(new TabStatus(TabStatus.Kind.MEDIUM_HEALTH, 7), TabStatus.health(Math.nextUp(6F), 20));
        assertEquals(new TabStatus(TabStatus.Kind.LOW_HEALTH, 6), TabStatus.health(6, 20));
        assertEquals(new TabStatus(TabStatus.Kind.LOW_HEALTH, 6), TabStatus.health(Math.nextDown(6F), 20));
        assertEquals(new TabStatus(TabStatus.Kind.LOW_HEALTH, 16), TabStatus.health(16, 60));
    }

    @Test void invalidOrDuplicateMarkersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DownedMarker(java.util.UUID.randomUUID(), 1, 1,
                Identifier.parse("minecraft:overworld"), Double.NaN, 0, 0, 15));
        var same = marker(java.util.UUID.randomUUID(), 15);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PlayerSnapshot.CODEC.encode(buffer, new PlayerSnapshot(1, 1,
                    new View(List.of(), 20000, 20000, null, false, List.of(same, same)), 3, 5));
            assertThrows(IllegalArgumentException.class, () -> PlayerSnapshot.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    private static DownedMarker marker(java.util.UUID uuid, int seconds) {
        return new DownedMarker(uuid, 3, 10, Identifier.parse("minecraft:overworld"), 1.25, 64, -3.5, seconds);
    }
    private static final TokenEngine.View TOKEN =
            new TokenEngine.View(Identifier.parse("runelegend:echo"), TokenEngine.ACTIVE, 0, 50, 0);

    @Test void tokenFlashCarriesOnlyTheTokenAndRound() {
        var flash = new TokenFlash(Identifier.parse("runelegend:arrogance"), 256);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            TokenFlash.CODEC.encode(buffer, flash);
            assertEquals(flash, TokenFlash.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

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
