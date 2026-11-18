package com.runelegend.combat;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.runelegend.RuneLegendMod;
import net.minecraft.util.ExtraCodecs;

/** Server-wide tuning for the guard stamina system. */
public record GuardConfig(int max, int minToStart, int drainPerTick, int regenPerTick, int regenDelayTicks) {
    /** Stamina values travel as an unsigned short, so the maximum must fit that range. */
    public static final int WIRE_MAX = 65_535;

    public static final GuardConfig DEFAULT = new GuardConfig(20_000, 2_000, 125, 100, 10);
    public static final Codec<GuardConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            field(ExtraCodecs.intRange(1, WIRE_MAX), "max", DEFAULT.max()).forGetter(GuardConfig::max),
            field(ExtraCodecs.NON_NEGATIVE_INT, "min_to_start", DEFAULT.minToStart()).forGetter(GuardConfig::minToStart),
            field(ExtraCodecs.NON_NEGATIVE_INT, "drain_per_tick", DEFAULT.drainPerTick()).forGetter(GuardConfig::drainPerTick),
            field(ExtraCodecs.NON_NEGATIVE_INT, "regen_per_tick", DEFAULT.regenPerTick()).forGetter(GuardConfig::regenPerTick),
            field(ExtraCodecs.NON_NEGATIVE_INT, "regen_delay_ticks", DEFAULT.regenDelayTicks())
                    .forGetter(GuardConfig::regenDelayTicks)
    ).apply(instance, GuardConfig::new));

    public GuardConfig {
        if (max < 1 || max > WIRE_MAX) throw invalid("max must be between 1 and " + WIRE_MAX);
        if (minToStart < 0 || minToStart > max) throw invalid("min_to_start must be between 0 and max");
        if (drainPerTick < 0) throw invalid("drain_per_tick must be non-negative");
        if (regenPerTick < 0) throw invalid("regen_per_tick must be non-negative");
        if (regenDelayTicks < 0) throw invalid("regen_delay_ticks must be non-negative");
    }

    public static GuardConfig fromJson(JsonObject root) {
        try {
            return CODEC.parse(JsonOps.INSTANCE, root).result().orElseGet(() -> {
                RuneLegendMod.LOGGER.error("Invalid RuneLegend guard config; using defaults");
                return DEFAULT;
            });
        } catch (RuntimeException error) {
            RuneLegendMod.LOGGER.error("Invalid RuneLegend guard config; using defaults", error);
            return DEFAULT;
        }
    }

    public JsonObject toJson() {
        return CODEC.encodeStart(JsonOps.INSTANCE, this).result()
                .orElseThrow(() -> new IllegalStateException("Could not encode RuneLegend guard config")).getAsJsonObject();
    }

    private static <A> MapCodec<A> field(Codec<A> codec, String name, A fallback) {
        return ExtraCodecs.optionalAlwaysPresentFieldOf(codec, name, fallback);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid RuneLegend guard setting: " + message);
    }
}
