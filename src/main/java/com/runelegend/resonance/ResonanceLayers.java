package com.runelegend.resonance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

/** Persistent layer counts shared by players and eligible monsters. */
public record ResonanceLayers(int protection, int lost, int destruction) {
    public static final ResonanceLayers NONE = new ResonanceLayers(0, 0, 0);
    public static final Codec<ResonanceLayers> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("protection", 0).forGetter(ResonanceLayers::protection),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("lost", 0).forGetter(ResonanceLayers::lost),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("destruction", 0).forGetter(ResonanceLayers::destruction)
    ).apply(instance, ResonanceLayers::new));

    public ResonanceLayers {
        if (protection < 0 || lost < 0 || destruction < 0) {
            throw new IllegalArgumentException("RuneLegend resonance layers must be non-negative");
        }
    }

    public int get(ResonanceAttribute attribute) {
        return switch (attribute) {
            case PROTECTION -> protection;
            case LOST -> lost;
            case DESTRUCTION -> destruction;
        };
    }

    public ResonanceLayers with(ResonanceAttribute attribute, int value) {
        return switch (attribute) {
            case PROTECTION -> new ResonanceLayers(value, lost, destruction);
            case LOST -> new ResonanceLayers(protection, value, destruction);
            case DESTRUCTION -> new ResonanceLayers(protection, lost, value);
        };
    }

    public boolean isEmpty() {
        return protection == 0 && lost == 0 && destruction == 0;
    }
}
