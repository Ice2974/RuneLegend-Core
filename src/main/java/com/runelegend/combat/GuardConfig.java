package com.runelegend.combat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.runelegend.RuneLegendMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.ExtraCodecs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server-wide tuning for the guard stamina system. Values are read once per server process. */
public record GuardConfig(int max, int minToStart, int drainPerTick, int regenPerTick, int regenDelayTicks) {
    private static final int VERSION = 1;
    /** Stamina values travel as an unsigned short, so the maximum must fit that range. */
    public static final int WIRE_MAX = 65_535;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

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

    public static Path defaultPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("runelegend-core/guard.json");
    }

    public static GuardConfig loadOrGenerate(Path path) {
        if (Files.notExists(path)) {
            writeDefault(path);
            return DEFAULT;
        }
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return fromJson(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException | RuntimeException error) {
            RuneLegendMod.LOGGER.error("Could not read RuneLegend guard config {}; using defaults", path, error);
            return DEFAULT;
        }
    }

    public static GuardConfig fromJson(JsonObject root) {
        try {
            if (!root.has("schemaVersion") || !root.get("schemaVersion").isJsonPrimitive()
                    || root.get("schemaVersion").getAsInt() != VERSION) {
                RuneLegendMod.LOGGER.error("Invalid RuneLegend guard config schema; expected schemaVersion {}", VERSION);
                return DEFAULT;
            }
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
        var root = new JsonObject();
        root.addProperty("schemaVersion", VERSION);
        encode(CODEC, this).getAsJsonObject().entrySet()
                .forEach(entry -> root.add(entry.getKey(), entry.getValue()));
        return root;
    }

    private static void writeDefault(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(DEFAULT.toJson()), StandardCharsets.UTF_8);
        } catch (IOException error) {
            RuneLegendMod.LOGGER.error("Could not write default RuneLegend guard config {}", path, error);
        }
    }

    private static <A> JsonElement encode(Codec<A> codec, A value) {
        return codec.encodeStart(JsonOps.INSTANCE, value).result()
                .orElseThrow(() -> new IllegalStateException("Could not encode RuneLegend guard config"));
    }

    private static <A> MapCodec<A> field(Codec<A> codec, String name, A fallback) {
        return ExtraCodecs.optionalAlwaysPresentFieldOf(codec, name, fallback);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid RuneLegend guard setting: " + message);
    }
}
