package com.runelegend.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.List;

/** Ordered slots are independent of which token definitions are installed. */
public record PlayerTokenData(List<Identifier> equipped) {
    public static final int MAX_SLOTS = 3;
    public static final PlayerTokenData EMPTY = new PlayerTokenData(List.of());
    private static final Codec<List<Identifier>> SLOTS = Identifier.CODEC.listOf(0, MAX_SLOTS)
            .validate(ids -> new HashSet<>(ids).size() == ids.size()
                    ? DataResult.success(ids) : DataResult.error(() -> "Duplicate RuneLegend token IDs"));
    public static final Codec<PlayerTokenData> CODEC =
            SLOTS.fieldOf("equippedTokens").xmap(PlayerTokenData::new, PlayerTokenData::equipped).codec();

    public PlayerTokenData {
        equipped = List.copyOf(equipped);
        if (equipped.size() > MAX_SLOTS || new HashSet<>(equipped).size() != equipped.size()) {
            throw new IllegalArgumentException("Invalid RuneLegend slots");
        }
    }
}
