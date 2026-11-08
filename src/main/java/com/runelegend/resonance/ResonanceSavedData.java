package com.runelegend.resonance;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.runelegend.RuneLegendMod;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * World-scoped persistent resonance data.
 *
 * <p>Low-level layer writes are package-private on purpose: callers must go through the validated
 * {@link ResonanceService} business interface so future destruction gains cannot bypass the
 * eligibility rules.</p>
 */
public final class ResonanceSavedData extends SavedData {
    private static final int VERSION = 1;
    private static final String NAME = "resonance";
    private static final Codec<Integer> VERSION_CODEC = Codec.INT.validate(version -> version == VERSION
            ? DataResult.success(version)
            : DataResult.error(() -> "Unsupported RuneLegend resonance schema " + version));
    static final Codec<ResonanceSavedData> CODEC = RecordCodecBuilder.<ResonanceSavedData>create(instance -> instance.group(
            VERSION_CODEC.fieldOf("schemaVersion").forGetter(data -> VERSION),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, ResonanceLayers.CODEC)
                    .optionalFieldOf("layers", Map.<UUID, ResonanceLayers>of())
                    .forGetter(data -> Map.copyOf(data.layers)),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Identifier.CODEC)
                    .optionalFieldOf("guardSelections", Map.<UUID, Identifier>of())
                    .forGetter(data -> Map.copyOf(data.guardSelections))
    ).apply(instance, (version, layers, selections) -> new ResonanceSavedData(layers, selections, true)))
            .mapResult(new Codec.ResultFunction<>() {
                @Override
                public <T> DataResult<Pair<ResonanceSavedData, T>> apply(DynamicOps<T> ops, T input,
                                                                          DataResult<Pair<ResonanceSavedData, T>> decoded) {
                    // SavedDataStorage accepts a partial result; never allow a partial save to overwrite missing records.
                    return decoded.error().<DataResult<Pair<ResonanceSavedData, T>>>map(error ->
                            DataResult.error(error.messageSupplier())).orElse(decoded);
                }

                @Override
                public <T> DataResult<T> coApply(DynamicOps<T> ops, ResonanceSavedData input, DataResult<T> encoded) {
                    return encoded;
                }
            });
    private static final SavedDataType<ResonanceSavedData> TYPE = new SavedDataType<>(
            RuneLegendMod.id(NAME), () -> new ResonanceSavedData(Map.of(), Map.of(), true), CODEC, null);

    private final Map<UUID, ResonanceLayers> layers;
    private final Map<UUID, Identifier> guardSelections;
    private final boolean writable;

    ResonanceSavedData(Map<UUID, ResonanceLayers> layers, Map<UUID, Identifier> guardSelections, boolean writable) {
        this.layers = new HashMap<>(layers);
        this.guardSelections = new HashMap<>(guardSelections);
        this.writable = writable;
    }

    public static ResonanceSavedData load(MinecraftServer server) {
        var dimension = DimensionType.getStorageFolder(server.overworld().dimension(),
                server.getWorldPath(LevelResource.ROOT));
        return load(server.overworld().getDataStorage(), dimension.resolve("data"));
    }

    static ResonanceSavedData load(SavedDataStorage storage, Path directory) {
        var file = RuneLegendMod.id(NAME + ".dat").resolveAgainst(directory);
        if (!Files.notExists(file)) {
            var existing = storage.get(TYPE);
            if (existing != null) return existing;
            RuneLegendMod.LOGGER.error("Cannot read RuneLegend resonance data {}; resonance writes are disabled", file);
            return new ResonanceSavedData(Map.of(), Map.of(), false);
        }
        return storage.computeIfAbsent(TYPE);
    }

    boolean writable() {
        return writable;
    }

    ResonanceLayers layers(UUID holder) {
        return layers.getOrDefault(holder, ResonanceLayers.NONE);
    }

    Optional<Identifier> guardSelection(UUID player) {
        return Optional.ofNullable(guardSelections.get(player));
    }

    boolean writeLayers(UUID holder, ResonanceLayers value) {
        requireWritable();
        var old = layers.get(holder);
        if (value.equals(old)) return false;
        if (value.isEmpty()) {
            if (layers.remove(holder) == null) return false;
            setDirty();
            return true;
        }
        layers.put(holder, value);
        setDirty();
        return true;
    }

    boolean eraseLayers(UUID holder) {
        requireWritable();
        if (layers.remove(holder) == null) return false;
        setDirty();
        return true;
    }

    boolean writeGuardSelection(UUID player, Optional<Identifier> value) {
        requireWritable();
        var old = guardSelections.get(player);
        if (value.isEmpty()) {
            if (guardSelections.remove(player) == null) return false;
            setDirty();
            return true;
        }
        var next = value.get();
        if (next.equals(old)) return false;
        guardSelections.put(player, next);
        setDirty();
        return true;
    }

    private void requireWritable() {
        if (!writable) throw new IllegalStateException("RuneLegend resonance data is read-only");
    }
}
