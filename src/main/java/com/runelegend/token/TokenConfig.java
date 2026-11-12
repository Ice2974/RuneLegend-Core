package com.runelegend.token;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.runelegend.RuneLegendMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** Server-wide numeric tuning for built-in tokens. Values are read once per server process. */
public record TokenConfig(
        SharpenedTipSettings sharpenedTip,
        SoulSiphonSettings soulSiphon,
        EchoSettings echo,
        ImmediateCounterSettings immediateCounter,
        VenomousTipSettings venomousTip,
        PoisonedArrowSettings poisonedArrow,
        AsuraFormSettings asuraForm) {
    private static final int VERSION = 1;
    private static final Set<String> KNOWN_IDS = Set.of(
            "runelegend:sharpened_tip", "runelegend:soul_siphon",
            "runelegend:echo", "runelegend:immediate_counter",
            "runelegend:venomous_tip", "runelegend:poisoned_arrow",
            "runelegend:asura_form");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public record SharpenedTipSettings(float chance, float bonusDamage) {
        public static final SharpenedTipSettings DEFAULT = new SharpenedTipSettings(0.50F, 2.0F);
        public static final Codec<SharpenedTipSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                field(ExtraCodecs.floatRange(0.0F, 1.0F), "chance", DEFAULT.chance()).forGetter(SharpenedTipSettings::chance),
                field(ExtraCodecs.NON_NEGATIVE_FLOAT, "bonus_damage", DEFAULT.bonusDamage()).forGetter(SharpenedTipSettings::bonusDamage)
        ).apply(instance, SharpenedTipSettings::new));

        public SharpenedTipSettings {
            TokenSettingValidation.requireFinite(chance, "chance");
            TokenSettingValidation.requireFinite(bonusDamage, "bonus_damage");
            if (chance < 0.0F || chance > 1.0F) throw TokenSettingValidation.invalid("chance must be between 0 and 1");
            if (bonusDamage < 0.0F) throw TokenSettingValidation.invalid("bonus_damage must be non-negative");
        }
    }

    public record SoulSiphonSettings(float chance, float maxHealthBonus) {
        public static final SoulSiphonSettings DEFAULT = new SoulSiphonSettings(0.05F, 2.0F);
        public static final Codec<SoulSiphonSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                field(ExtraCodecs.floatRange(0.0F, 1.0F), "chance", DEFAULT.chance()).forGetter(SoulSiphonSettings::chance),
                field(ExtraCodecs.NON_NEGATIVE_FLOAT, "max_health_bonus", DEFAULT.maxHealthBonus()).forGetter(SoulSiphonSettings::maxHealthBonus)
        ).apply(instance, SoulSiphonSettings::new));

        public SoulSiphonSettings {
            TokenSettingValidation.requireFinite(chance, "chance");
            TokenSettingValidation.requireFinite(maxHealthBonus, "max_health_bonus");
            if (chance < 0.0F || chance > 1.0F) throw TokenSettingValidation.invalid("chance must be between 0 and 1");
            if (maxHealthBonus < 0.0F) throw TokenSettingValidation.invalid("max_health_bonus must be non-negative");
        }
    }

    public record EchoSettings(float chance, double radius, float damage, int durationTicks, int intervalTicks) {
        public static final EchoSettings DEFAULT = new EchoSettings(0.50F, 5.0, 3.0F, 160, 20);
        public static final Codec<EchoSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                field(ExtraCodecs.floatRange(0.0F, 1.0F), "chance", DEFAULT.chance()).forGetter(EchoSettings::chance),
                field(Codec.DOUBLE.validate(EchoSettings::positiveRadius), "radius", DEFAULT.radius()).forGetter(EchoSettings::radius),
                field(ExtraCodecs.NON_NEGATIVE_FLOAT, "damage", DEFAULT.damage()).forGetter(EchoSettings::damage),
                field(ExtraCodecs.NON_NEGATIVE_INT, "duration_ticks", DEFAULT.durationTicks()).forGetter(EchoSettings::durationTicks),
                field(ExtraCodecs.POSITIVE_INT, "interval_ticks", DEFAULT.intervalTicks()).forGetter(EchoSettings::intervalTicks)
        ).apply(instance, EchoSettings::new));

        public EchoSettings {
            TokenSettingValidation.requireFinite(chance, "chance");
            TokenSettingValidation.requireFinite(damage, "damage");
            if (chance < 0.0F || chance > 1.0F) throw TokenSettingValidation.invalid("chance must be between 0 and 1");
            if (!Double.isFinite(radius) || radius <= 0.0) throw TokenSettingValidation.invalid("radius must be positive");
            if (damage < 0.0F) throw TokenSettingValidation.invalid("damage must be non-negative");
            if (durationTicks < 0) throw TokenSettingValidation.invalid("duration_ticks must be non-negative");
            if (intervalTicks <= 0) throw TokenSettingValidation.invalid("interval_ticks must be positive");
        }

        private static DataResult<Double> positiveRadius(double value) {
            return Double.isFinite(value) && value > 0.0
                    ? DataResult.success(value) : DataResult.error(() -> "radius must be positive");
        }
    }

    public record ImmediateCounterSettings(float bonusDamage, int durationTicks, int cooldownAfterEffectTicks) {
        public static final ImmediateCounterSettings DEFAULT = new ImmediateCounterSettings(3.0F, 160, 240);
        public static final Codec<ImmediateCounterSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                field(ExtraCodecs.NON_NEGATIVE_FLOAT, "bonus_damage", DEFAULT.bonusDamage()).forGetter(ImmediateCounterSettings::bonusDamage),
                field(ExtraCodecs.NON_NEGATIVE_INT, "duration_ticks", DEFAULT.durationTicks()).forGetter(ImmediateCounterSettings::durationTicks),
                field(ExtraCodecs.NON_NEGATIVE_INT, "cooldown_after_effect_ticks", DEFAULT.cooldownAfterEffectTicks())
                        .forGetter(ImmediateCounterSettings::cooldownAfterEffectTicks)
        ).apply(instance, ImmediateCounterSettings::new));

        public ImmediateCounterSettings {
            TokenSettingValidation.requireFinite(bonusDamage, "bonus_damage");
            if (bonusDamage < 0.0F) throw TokenSettingValidation.invalid("bonus_damage must be non-negative");
            if (durationTicks < 0) throw TokenSettingValidation.invalid("duration_ticks must be non-negative");
            if (cooldownAfterEffectTicks < 0) throw TokenSettingValidation.invalid("cooldown_after_effect_ticks must be non-negative");
        }
    }

    /** The poison level is fixed at II; only chance and duration are configurable. */
    public record VenomousTipSettings(float chance, int durationSeconds) {
        public static final VenomousTipSettings DEFAULT = new VenomousTipSettings(0.50F, 6);
        public static final Codec<VenomousTipSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                field(ExtraCodecs.floatRange(0.0F, 1.0F), "chance", DEFAULT.chance()).forGetter(VenomousTipSettings::chance),
                field(ExtraCodecs.POSITIVE_INT, "duration_seconds", DEFAULT.durationSeconds())
                        .forGetter(VenomousTipSettings::durationSeconds)
        ).apply(instance, VenomousTipSettings::new));

        public VenomousTipSettings {
            TokenSettingValidation.requireFinite(chance, "chance");
            if (chance < 0.0F || chance > 1.0F) throw TokenSettingValidation.invalid("chance must be between 0 and 1");
            if (durationSeconds <= 0 || durationSeconds > Integer.MAX_VALUE / 20) {
                throw TokenSettingValidation.invalid("duration_seconds must be positive and fit in ticks");
            }
        }

        public int durationTicks() { return durationSeconds * 20; }
    }

    /** {@code level} is 1-based: 1 applies Poison I, 2 applies Poison II. */
    public record PoisonedArrowSettings(float chance, int durationSeconds, int level) {
        public static final PoisonedArrowSettings DEFAULT = new PoisonedArrowSettings(0.50F, 12, 2);
        public static final Codec<PoisonedArrowSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                field(ExtraCodecs.floatRange(0.0F, 1.0F), "chance", DEFAULT.chance()).forGetter(PoisonedArrowSettings::chance),
                field(ExtraCodecs.POSITIVE_INT, "duration_seconds", DEFAULT.durationSeconds())
                        .forGetter(PoisonedArrowSettings::durationSeconds),
                field(ExtraCodecs.intRange(1, 256), "level", DEFAULT.level()).forGetter(PoisonedArrowSettings::level)
        ).apply(instance, PoisonedArrowSettings::new));

        public PoisonedArrowSettings {
            TokenSettingValidation.requireFinite(chance, "chance");
            if (chance < 0.0F || chance > 1.0F) throw TokenSettingValidation.invalid("chance must be between 0 and 1");
            if (durationSeconds <= 0 || durationSeconds > Integer.MAX_VALUE / 20) {
                throw TokenSettingValidation.invalid("duration_seconds must be positive and fit in ticks");
            }
            if (level < 1 || level > 256) throw TokenSettingValidation.invalid("level must be between 1 and 256");
        }

        public int durationTicks() { return durationSeconds * 20; }
        public int amplifier() { return level - 1; }
    }

    public record AsuraFormSettings(int requiredKills, int durationSeconds, float bonusDamage,
                                    int speedLevel, float temporaryHealth, int cooldownSeconds) {
        private static final int MAX_SECONDS = Integer.MAX_VALUE / 20;
        public static final AsuraFormSettings DEFAULT = new AsuraFormSettings(3, 12, 4, 2, 16, 9);
        public static final Codec<AsuraFormSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                field(wholeNumber(1, Integer.MAX_VALUE), "required_kills", DEFAULT.requiredKills())
                        .forGetter(AsuraFormSettings::requiredKills),
                field(wholeNumber(1, MAX_SECONDS), "duration_seconds", DEFAULT.durationSeconds())
                        .forGetter(AsuraFormSettings::durationSeconds),
                field(ExtraCodecs.floatRange(0, Float.MAX_VALUE), "bonus_damage", DEFAULT.bonusDamage())
                        .forGetter(AsuraFormSettings::bonusDamage),
                field(wholeNumber(1, Integer.MAX_VALUE), "speed_level", DEFAULT.speedLevel())
                        .forGetter(AsuraFormSettings::speedLevel),
                field(ExtraCodecs.floatRange(1, 1024), "temporary_health", DEFAULT.temporaryHealth())
                        .forGetter(AsuraFormSettings::temporaryHealth),
                field(wholeNumber(0, MAX_SECONDS), "cooldown_seconds", DEFAULT.cooldownSeconds())
                        .forGetter(AsuraFormSettings::cooldownSeconds)
        ).apply(instance, AsuraFormSettings::new));

        public AsuraFormSettings {
            TokenSettingValidation.requireFinite(bonusDamage, "bonus_damage");
            TokenSettingValidation.requireFinite(temporaryHealth, "temporary_health");
            if (requiredKills < 1) throw TokenSettingValidation.invalid("required_kills must be positive");
            if (durationSeconds < 1 || durationSeconds > MAX_SECONDS) {
                throw TokenSettingValidation.invalid("duration_seconds must be positive and fit in ticks");
            }
            if (bonusDamage < 0) throw TokenSettingValidation.invalid("bonus_damage must be non-negative");
            if (speedLevel < 1) throw TokenSettingValidation.invalid("speed_level must be positive");
            if (temporaryHealth < 1 || temporaryHealth > 1024) {
                throw TokenSettingValidation.invalid("temporary_health must be between 1 and 1024");
            }
            if (cooldownSeconds < 0 || cooldownSeconds > MAX_SECONDS) {
                throw TokenSettingValidation.invalid("cooldown_seconds must be non-negative and fit in ticks");
            }
        }

        public int durationTicks() { return durationSeconds * 20; }
        public int cooldownTicks() { return cooldownSeconds * 20; }

        // JsonOps' integer codec would silently truncate fractional input.
        private static Codec<Integer> wholeNumber(int min, int max) {
            return Codec.DOUBLE.validate(value -> value >= min && value <= max && value == Math.rint(value)
                    ? DataResult.success(value) : DataResult.error(() -> "Expected an integer between " + min + " and " + max))
                    .xmap(Double::intValue, Integer::doubleValue);
        }
    }

    public static final TokenConfig DEFAULT = new TokenConfig(SharpenedTipSettings.DEFAULT,
            SoulSiphonSettings.DEFAULT, EchoSettings.DEFAULT, ImmediateCounterSettings.DEFAULT,
            VenomousTipSettings.DEFAULT, PoisonedArrowSettings.DEFAULT, AsuraFormSettings.DEFAULT);

    public static Path defaultPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("runelegend-core/tokens.json");
    }

    public static TokenConfig loadOrGenerate(Path path) {
        if (Files.notExists(path)) {
            writeDefault(path);
            return DEFAULT;
        }
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return fromJson(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException | RuntimeException error) {
            RuneLegendMod.LOGGER.error("Could not read RuneLegend token config {}; using defaults", path, error);
            return DEFAULT;
        }
    }

    public static TokenConfig fromJson(JsonObject root) {
        if (!root.has("schemaVersion") || !root.get("schemaVersion").isJsonPrimitive()
                || root.get("schemaVersion").getAsInt() != VERSION) {
            RuneLegendMod.LOGGER.error("Invalid RuneLegend token config schema; expected schemaVersion {}", VERSION);
            return DEFAULT;
        }
        var tokenElement = root.get("tokens");
        if (tokenElement == null) return DEFAULT;
        if (!tokenElement.isJsonObject()) {
            RuneLegendMod.LOGGER.error("Invalid RuneLegend token config: tokens must be an object");
            return DEFAULT;
        }
        var tokens = tokenElement.getAsJsonObject();
        warnAboutUnknownTokens(tokens);
        return new TokenConfig(
                section(tokens, "runelegend:sharpened_tip", SharpenedTipSettings.CODEC, SharpenedTipSettings.DEFAULT),
                section(tokens, "runelegend:soul_siphon", SoulSiphonSettings.CODEC, SoulSiphonSettings.DEFAULT),
                section(tokens, "runelegend:echo", EchoSettings.CODEC, EchoSettings.DEFAULT),
                section(tokens, "runelegend:immediate_counter", ImmediateCounterSettings.CODEC, ImmediateCounterSettings.DEFAULT),
                section(tokens, "runelegend:venomous_tip", VenomousTipSettings.CODEC, VenomousTipSettings.DEFAULT),
                section(tokens, "runelegend:poisoned_arrow", PoisonedArrowSettings.CODEC, PoisonedArrowSettings.DEFAULT),
                section(tokens, "runelegend:asura_form", AsuraFormSettings.CODEC, AsuraFormSettings.DEFAULT));
    }

    public JsonObject toJson() {
        var tokens = new JsonObject();
        tokens.add("runelegend:sharpened_tip", encode(SharpenedTipSettings.CODEC, sharpenedTip));
        tokens.add("runelegend:soul_siphon", encode(SoulSiphonSettings.CODEC, soulSiphon));
        tokens.add("runelegend:echo", encode(EchoSettings.CODEC, echo));
        tokens.add("runelegend:immediate_counter", encode(ImmediateCounterSettings.CODEC, immediateCounter));
        tokens.add("runelegend:venomous_tip", encode(VenomousTipSettings.CODEC, venomousTip));
        tokens.add("runelegend:poisoned_arrow", encode(PoisonedArrowSettings.CODEC, poisonedArrow));
        tokens.add("runelegend:asura_form", encode(AsuraFormSettings.CODEC, asuraForm));
        var root = new JsonObject();
        root.addProperty("schemaVersion", VERSION);
        root.add("tokens", tokens);
        return root;
    }

    private static void writeDefault(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(DEFAULT.toJson()), StandardCharsets.UTF_8);
        } catch (IOException error) {
            RuneLegendMod.LOGGER.error("Could not write default RuneLegend token config {}", path, error);
        }
    }

    private static void warnAboutUnknownTokens(JsonObject tokens) {
        for (var key : tokens.keySet()) {
            if (KNOWN_IDS.contains(key)) continue;
            try {
                Identifier.parse(key);
                RuneLegendMod.LOGGER.warn("Unknown RuneLegend token config entry {} ignored", key);
            } catch (RuntimeException error) {
                RuneLegendMod.LOGGER.warn("Invalid RuneLegend token config ID {} ignored", key);
            }
        }
    }

    private static <T> T section(JsonObject tokens, String id, Codec<T> codec, T fallback) {
        var element = tokens.get(id);
        if (element == null || !element.isJsonObject()) return fallback;
        return codec.parse(JsonOps.INSTANCE, element).result().orElseGet(() -> {
            RuneLegendMod.LOGGER.error("Invalid RuneLegend config for token {}; using defaults", id);
            return fallback;
        });
    }

    private static <T> JsonElement encode(Codec<T> codec, T value) {
        return codec.encodeStart(JsonOps.INSTANCE, value).result()
                .orElseThrow(() -> new IllegalStateException("Could not encode RuneLegend token config"));
    }

    private static <A> MapCodec<A> field(Codec<A> codec, String name, A fallback) {
        return ExtraCodecs.optionalAlwaysPresentFieldOf(codec, name, fallback);
    }

    /** Formats a tick count as seconds for player-visible descriptions; 20 ticks = 1 second. */
    public static String seconds(int ticks) {
        return trimNumber(ticks / 20.0F);
    }

    public static String percent(float chance) {
        return trimNumber(chance * 100.0F) + "%";
    }

    private static String trimNumber(float value) {
        var text = String.format(Locale.ROOT, "%.2f", value);
        return text.endsWith(".00") ? text.substring(0, text.length() - 3)
                : text.endsWith("0") ? text.substring(0, text.length() - 1) : text;
    }
}
