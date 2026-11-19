package com.runelegend.token;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.runelegend.RuneLegendMod;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.Locale;
import java.util.Set;

/** Server-wide numeric tuning for built-in tokens. */
public record TokenConfig(
        SoulSiphonSettings soulSiphon,
        EchoSettings echo,
        ImmediateCounterSettings immediateCounter,
        VenomousTipSettings venomousTip,
        PoisonedArrowSettings poisonedArrow,
        AsuraFormSettings asuraForm,
        ResonanceHealingSettings resonanceHealing,
        VernalTideSettings vernalTide,
        ArroganceSettings arrogance,
        ExecutionEchoSettings executionEcho,
        RetributionSettings retribution,
        SubstituteSettings substitute,
        FullBlessingSettings fullBlessing,
        FountainSettings fountainOfLife) {
    private static final Set<String> KNOWN_IDS = Set.of(
            "runelegend:soul_siphon",
            "runelegend:echo", "runelegend:immediate_counter",
            "runelegend:venomous_tip", "runelegend:poisoned_arrow",
            "runelegend:asura_form", "runelegend:resonance_healing",
            "runelegend:vernal_tide", "runelegend:arrogance", "runelegend:execution_echo", "runelegend:retribution",
            "runelegend:substitute", "runelegend:full_blessing", "runelegend:fountain_of_life");

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

    /** The radius is fixed by design; only the chance, damage, and timing are configurable. */
    public record EchoSettings(float chance, float damage, int durationTicks, int intervalTicks) {
        public static final double RADIUS = 5.0;
        public static final int MIN_INTERVAL_TICKS = 10;
        public static final EchoSettings DEFAULT = new EchoSettings(0.50F, 3.0F, 160, 20);
        public static final Codec<EchoSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                field(ExtraCodecs.floatRange(0.0F, 1.0F), "chance", DEFAULT.chance()).forGetter(EchoSettings::chance),
                field(ExtraCodecs.NON_NEGATIVE_FLOAT, "damage", DEFAULT.damage()).forGetter(EchoSettings::damage),
                field(ExtraCodecs.NON_NEGATIVE_INT, "duration_ticks", DEFAULT.durationTicks()).forGetter(EchoSettings::durationTicks),
                field(Codec.INT.validate(EchoSettings::validInterval), "interval_ticks", DEFAULT.intervalTicks())
                        .forGetter(EchoSettings::intervalTicks)
        ).apply(instance, EchoSettings::new));

        public EchoSettings {
            TokenSettingValidation.requireFinite(chance, "chance");
            TokenSettingValidation.requireFinite(damage, "damage");
            if (chance < 0.0F || chance > 1.0F) throw TokenSettingValidation.invalid("chance must be between 0 and 1");
            if (damage < 0.0F) throw TokenSettingValidation.invalid("damage must be non-negative");
            if (durationTicks < 0) throw TokenSettingValidation.invalid("duration_ticks must be non-negative");
            if (intervalTicks < MIN_INTERVAL_TICKS) {
                throw TokenSettingValidation.invalid("interval_ticks must be at least " + MIN_INTERVAL_TICKS);
            }
        }

        private static DataResult<Integer> validInterval(int value) {
            return value >= MIN_INTERVAL_TICKS ? DataResult.success(value)
                    : DataResult.error(() -> "interval_ticks must be at least " + MIN_INTERVAL_TICKS);
        }
    }

    public record ImmediateCounterSettings(float bonusDamage, int durationTicks, int cooldownAfterEffectTicks) {
        public static final ImmediateCounterSettings DEFAULT = new ImmediateCounterSettings(4.0F, 160, 160);
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
        public static final VenomousTipSettings DEFAULT = new VenomousTipSettings(0.50F, 8);
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

    /** The applied poison level is fixed at Poison II; only the chance and duration are configurable. */
    public record PoisonedArrowSettings(float chance, int durationSeconds) {
        public static final PoisonedArrowSettings DEFAULT = new PoisonedArrowSettings(0.50F, 16);
        public static final Codec<PoisonedArrowSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                field(ExtraCodecs.floatRange(0.0F, 1.0F), "chance", DEFAULT.chance()).forGetter(PoisonedArrowSettings::chance),
                field(ExtraCodecs.POSITIVE_INT, "duration_seconds", DEFAULT.durationSeconds())
                        .forGetter(PoisonedArrowSettings::durationSeconds)
        ).apply(instance, PoisonedArrowSettings::new));

        public PoisonedArrowSettings {
            TokenSettingValidation.requireFinite(chance, "chance");
            if (chance < 0.0F || chance > 1.0F) throw TokenSettingValidation.invalid("chance must be between 0 and 1");
            if (durationSeconds <= 0 || durationSeconds > Integer.MAX_VALUE / 20) {
                throw TokenSettingValidation.invalid("duration_seconds must be positive and fit in ticks");
            }
        }

        public int durationTicks() { return durationSeconds * 20; }
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
    }

    public record ResonanceHealingSettings(float healthThreshold, double radius, float healPerEnemy, int cooldownSeconds) {
        private static final int MAX_SECONDS = Integer.MAX_VALUE / 20;
        public static final ResonanceHealingSettings DEFAULT = new ResonanceHealingSettings(0.5F, 5.0, 1.0F, 10);
        public static final Codec<ResonanceHealingSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                field(ExtraCodecs.floatRange(0.0F, 1.0F), "health_threshold", DEFAULT.healthThreshold())
                        .forGetter(ResonanceHealingSettings::healthThreshold),
                field(Codec.DOUBLE.validate(ResonanceHealingSettings::positiveRadius), "radius", DEFAULT.radius())
                        .forGetter(ResonanceHealingSettings::radius),
                field(ExtraCodecs.NON_NEGATIVE_FLOAT, "heal_per_enemy", DEFAULT.healPerEnemy())
                        .forGetter(ResonanceHealingSettings::healPerEnemy),
                field(wholeNumber(0, MAX_SECONDS), "cooldown_seconds", DEFAULT.cooldownSeconds())
                        .forGetter(ResonanceHealingSettings::cooldownSeconds)
        ).apply(instance, ResonanceHealingSettings::new));

        public ResonanceHealingSettings {
            TokenSettingValidation.requireFinite(healthThreshold, "health_threshold");
            TokenSettingValidation.requireFinite(healPerEnemy, "heal_per_enemy");
            if (healthThreshold < 0.0F || healthThreshold > 1.0F) {
                throw TokenSettingValidation.invalid("health_threshold must be between 0 and 1");
            }
            if (!Double.isFinite(radius) || radius <= 0.0) {
                throw TokenSettingValidation.invalid("radius must be positive");
            }
            if (healPerEnemy < 0.0F) throw TokenSettingValidation.invalid("heal_per_enemy must be non-negative");
            if (cooldownSeconds < 0 || cooldownSeconds > MAX_SECONDS) {
                throw TokenSettingValidation.invalid("cooldown_seconds must be non-negative and fit in ticks");
            }
        }

        public int cooldownTicks() { return cooldownSeconds * 20; }

        private static DataResult<Double> positiveRadius(double value) {
            return Double.isFinite(value) && value > 0.0
                    ? DataResult.success(value) : DataResult.error(() -> "radius must be positive");
        }
    }

    /** The struck target takes the bonus as part of its own hit; only other enemies use the radius. */
    public record VernalTideSettings(float chance, double radius, float damage) {
        public static final VernalTideSettings DEFAULT = new VernalTideSettings(0.50F, 5.0, 3.0F);
        public static final Codec<VernalTideSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                field(ExtraCodecs.floatRange(0.0F, 1.0F), "chance", DEFAULT.chance()).forGetter(VernalTideSettings::chance),
                field(Codec.DOUBLE.validate(VernalTideSettings::positiveRadius), "radius", DEFAULT.radius())
                        .forGetter(VernalTideSettings::radius),
                field(ExtraCodecs.NON_NEGATIVE_FLOAT, "damage", DEFAULT.damage()).forGetter(VernalTideSettings::damage)
        ).apply(instance, VernalTideSettings::new));

        public VernalTideSettings {
            TokenSettingValidation.requireFinite(chance, "chance");
            TokenSettingValidation.requireFinite(damage, "damage");
            if (chance < 0.0F || chance > 1.0F) throw TokenSettingValidation.invalid("chance must be between 0 and 1");
            if (!Double.isFinite(radius) || radius <= 0.0) throw TokenSettingValidation.invalid("radius must be positive");
            if (damage < 0.0F) throw TokenSettingValidation.invalid("damage must be non-negative");
        }

        private static DataResult<Double> positiveRadius(double value) {
            return Double.isFinite(value) && value > 0.0
                    ? DataResult.success(value) : DataResult.error(() -> "radius must be positive");
        }
    }

    public record ArroganceSettings(float healthThreshold, int killsPerIncrease, float damagePerIncrease,
                                    float maxBonusDamage) {
        public static final ArroganceSettings DEFAULT = new ArroganceSettings(0.6F, 1, 1.0F, 5.0F);
        public static final Codec<ArroganceSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                field(ExtraCodecs.floatRange(0, 1), "health_threshold", DEFAULT.healthThreshold())
                        .forGetter(ArroganceSettings::healthThreshold),
                field(wholeNumber(1, Integer.MAX_VALUE), "kills_per_increase", DEFAULT.killsPerIncrease())
                        .forGetter(ArroganceSettings::killsPerIncrease),
                field(ExtraCodecs.floatRange(Float.MIN_VALUE, Float.MAX_VALUE), "damage_per_increase",
                        DEFAULT.damagePerIncrease()).forGetter(ArroganceSettings::damagePerIncrease),
                field(ExtraCodecs.floatRange(Float.MIN_VALUE, Float.MAX_VALUE), "max_bonus_damage",
                        DEFAULT.maxBonusDamage()).forGetter(ArroganceSettings::maxBonusDamage)
        ).apply(instance, ArroganceSettings::new));

        public ArroganceSettings {
            TokenSettingValidation.requireFinite(healthThreshold, "health_threshold");
            TokenSettingValidation.requireFinite(damagePerIncrease, "damage_per_increase");
            TokenSettingValidation.requireFinite(maxBonusDamage, "max_bonus_damage");
            if (healthThreshold < 0 || healthThreshold > 1) {
                throw TokenSettingValidation.invalid("health_threshold must be between 0 and 1");
            }
            if (killsPerIncrease < 1) throw TokenSettingValidation.invalid("kills_per_increase must be positive");
            if (damagePerIncrease <= 0) throw TokenSettingValidation.invalid("damage_per_increase must be positive");
            if (maxBonusDamage <= 0) throw TokenSettingValidation.invalid("max_bonus_damage must be positive");
        }
    }

    /**
     * The echo records the kill swing's damage after token additions and before absorption, then adds it back
     * as true damage that decays to zero; the decay and cooldown are configurable, the cap bounds the record.
     */
    public record ExecutionEchoSettings(float decayPerInterval, int decayIntervalSeconds, int cooldownSeconds,
                                        int maxRecordedDamage) {
        private static final int MAX_SECONDS = Integer.MAX_VALUE / 20;
        public static final ExecutionEchoSettings DEFAULT = new ExecutionEchoSettings(2.0F, 1, 10, 10);
        public static final Codec<ExecutionEchoSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                field(ExtraCodecs.floatRange(0.01F, 1024.0F), "decay_per_interval", DEFAULT.decayPerInterval())
                        .forGetter(ExecutionEchoSettings::decayPerInterval),
                field(wholeNumber(1, MAX_SECONDS), "decay_interval_seconds", DEFAULT.decayIntervalSeconds())
                        .forGetter(ExecutionEchoSettings::decayIntervalSeconds),
                field(wholeNumber(0, MAX_SECONDS), "cooldown_seconds", DEFAULT.cooldownSeconds())
                        .forGetter(ExecutionEchoSettings::cooldownSeconds),
                field(wholeNumber(1, 1024), "max_recorded_damage", DEFAULT.maxRecordedDamage())
                        .forGetter(ExecutionEchoSettings::maxRecordedDamage)
        ).apply(instance, ExecutionEchoSettings::new));

        public ExecutionEchoSettings {
            TokenSettingValidation.requireFinite(decayPerInterval, "decay_per_interval");
            if (decayPerInterval < 0.01F || decayPerInterval > 1024.0F) {
                throw TokenSettingValidation.invalid("decay_per_interval must be between 0.01 and 1024");
            }
            if (decayIntervalSeconds < 1 || decayIntervalSeconds > MAX_SECONDS) {
                throw TokenSettingValidation.invalid("decay_interval_seconds must be positive and fit in ticks");
            }
            if (cooldownSeconds < 0 || cooldownSeconds > MAX_SECONDS) {
                throw TokenSettingValidation.invalid("cooldown_seconds must be non-negative and fit in ticks");
            }
            if (maxRecordedDamage < 1 || maxRecordedDamage > 1024) {
                throw TokenSettingValidation.invalid("max_recorded_damage must be between 1 and 1024");
            }
        }

        public int decayIntervalTicks() { return decayIntervalSeconds * 20; }
        public int cooldownTicks() { return cooldownSeconds * 20; }
    }

    public record RetributionSettings(int durationSeconds, int cooldownSeconds) {
        private static final int MAX_SECONDS = Integer.MAX_VALUE / 20;
        public static final RetributionSettings DEFAULT = new RetributionSettings(5, 10);
        public static final Codec<RetributionSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                field(wholeNumber(1, MAX_SECONDS), "duration_seconds", DEFAULT.durationSeconds())
                        .forGetter(RetributionSettings::durationSeconds),
                field(wholeNumber(0, MAX_SECONDS), "cooldown_seconds", DEFAULT.cooldownSeconds())
                        .forGetter(RetributionSettings::cooldownSeconds)
        ).apply(instance, RetributionSettings::new));

        public RetributionSettings {
            if (durationSeconds < 1 || durationSeconds > MAX_SECONDS) {
                throw TokenSettingValidation.invalid("duration_seconds must be positive and fit in ticks");
            }
            if (cooldownSeconds < 0 || cooldownSeconds > MAX_SECONDS) {
                throw TokenSettingValidation.invalid("cooldown_seconds must be non-negative and fit in ticks");
            }
        }

        public int durationTicks() { return durationSeconds * 20; }
        public int cooldownTicks() { return cooldownSeconds * 20; }
    }

    public record SubstituteSettings(float healthThreshold, int durationSeconds, int cooldownSeconds,
                                     double explosionRadius, float explosionDamage) {
        private static final int MAX_SECONDS = Integer.MAX_VALUE / 20;
        public static final SubstituteSettings DEFAULT = new SubstituteSettings(0.5F, 20, 40, 5.0, 20.0F);
        public static final Codec<SubstituteSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                field(ExtraCodecs.floatRange(0.0F, 1.0F), "health_threshold", DEFAULT.healthThreshold())
                        .forGetter(SubstituteSettings::healthThreshold),
                field(wholeNumber(1, MAX_SECONDS), "duration_seconds", DEFAULT.durationSeconds())
                        .forGetter(SubstituteSettings::durationSeconds),
                field(wholeNumber(0, MAX_SECONDS), "cooldown_seconds", DEFAULT.cooldownSeconds())
                        .forGetter(SubstituteSettings::cooldownSeconds),
                field(Codec.DOUBLE.validate(value -> Double.isFinite(value) && value > 0
                        ? DataResult.success(value) : DataResult.error(() -> "explosion_radius must be positive and finite")),
                        "explosion_radius", DEFAULT.explosionRadius()).forGetter(SubstituteSettings::explosionRadius),
                field(ExtraCodecs.POSITIVE_FLOAT, "explosion_damage", DEFAULT.explosionDamage())
                        .forGetter(SubstituteSettings::explosionDamage)
        ).apply(instance, SubstituteSettings::new));

        public SubstituteSettings {
            TokenSettingValidation.requireFinite(healthThreshold, "health_threshold");
            TokenSettingValidation.requireFinite(explosionDamage, "explosion_damage");
            if (!Double.isFinite(explosionRadius) || explosionRadius <= 0) {
                throw TokenSettingValidation.invalid("explosion_radius must be positive and finite");
            }
            if (explosionDamage <= 0) throw TokenSettingValidation.invalid("explosion_damage must be positive");
            if (healthThreshold < 0 || healthThreshold > 1) {
                throw TokenSettingValidation.invalid("health_threshold must be between 0 and 1");
            }
            if (durationSeconds < 1 || durationSeconds > MAX_SECONDS) {
                throw TokenSettingValidation.invalid("duration_seconds must be positive and fit in ticks");
            }
            if (cooldownSeconds < 0 || cooldownSeconds > MAX_SECONDS) {
                throw TokenSettingValidation.invalid("cooldown_seconds must be non-negative and fit in ticks");
            }
        }

        public int durationTicks() { return durationSeconds * 20; }
        public int cooldownTicks() { return cooldownSeconds * 20; }
    }

    public record FullBlessingSettings(int blessingsPerTrigger, float healthThreshold, float healAmount,
                                       int maxStoredBlessings, int cooldownSeconds) {
        private static final int MAX_BLESSINGS = 1024;
        private static final int MAX_SECONDS = Integer.MAX_VALUE / 20;
        public static final FullBlessingSettings DEFAULT = new FullBlessingSettings(3, 0.5F, 4.0F, 6, 20);
        public static final Codec<FullBlessingSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                field(wholeNumber(1, MAX_BLESSINGS), "blessings_per_trigger", DEFAULT.blessingsPerTrigger())
                        .forGetter(FullBlessingSettings::blessingsPerTrigger),
                field(ExtraCodecs.floatRange(0, 1), "health_threshold", DEFAULT.healthThreshold())
                        .forGetter(FullBlessingSettings::healthThreshold),
                field(ExtraCodecs.floatRange(Float.MIN_VALUE, Float.MAX_VALUE), "heal_amount", DEFAULT.healAmount())
                        .forGetter(FullBlessingSettings::healAmount),
                field(wholeNumber(0, MAX_BLESSINGS), "max_stored_blessings", DEFAULT.maxStoredBlessings())
                        .forGetter(FullBlessingSettings::maxStoredBlessings),
                field(wholeNumber(0, MAX_SECONDS), "cooldown_seconds", DEFAULT.cooldownSeconds())
                        .forGetter(FullBlessingSettings::cooldownSeconds)
        ).apply(instance, FullBlessingSettings::new));

        public FullBlessingSettings {
            TokenSettingValidation.requireFinite(healthThreshold, "health_threshold");
            TokenSettingValidation.requireFinite(healAmount, "heal_amount");
            if (blessingsPerTrigger < 1 || blessingsPerTrigger > MAX_BLESSINGS) {
                throw TokenSettingValidation.invalid("blessings_per_trigger must be between 1 and " + MAX_BLESSINGS);
            }
            if (healthThreshold < 0 || healthThreshold > 1) {
                throw TokenSettingValidation.invalid("health_threshold must be between 0 and 1");
            }
            if (healAmount <= 0) throw TokenSettingValidation.invalid("heal_amount must be positive");
            if (maxStoredBlessings < 0 || maxStoredBlessings > MAX_BLESSINGS) {
                throw TokenSettingValidation.invalid("max_stored_blessings must be between 0 and " + MAX_BLESSINGS);
            }
            if (cooldownSeconds < 0 || cooldownSeconds > MAX_SECONDS) {
                throw TokenSettingValidation.invalid("cooldown_seconds must be non-negative and fit in ticks");
            }
        }

        public int cooldownTicks() { return cooldownSeconds * 20; }
    }

    /** The horizontal radius is fixed by design; every other value is configurable. */
    public record FountainSettings(float healthThreshold, int regenerationLevel, int resistanceLevel,
                                   int regenerationLingerSeconds, int resistanceLingerSeconds,
                                   int durationSeconds, int cooldownSeconds) {
        private static final int MAX_SECONDS = Integer.MAX_VALUE / 20;
        private static final int MAX_LEVEL = MobEffectInstance.MAX_AMPLIFIER + 1;
        public static final double RADIUS = 3.0;
        public static final FountainSettings DEFAULT = new FountainSettings(0.5F, 2, 2, 1, 5, 10, 10);
        public static final Codec<FountainSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                field(ExtraCodecs.floatRange(0, 1), "health_threshold", DEFAULT.healthThreshold())
                        .forGetter(FountainSettings::healthThreshold),
                field(wholeNumber(1, MAX_LEVEL), "regeneration_level", DEFAULT.regenerationLevel())
                        .forGetter(FountainSettings::regenerationLevel),
                field(wholeNumber(1, MAX_LEVEL), "resistance_level", DEFAULT.resistanceLevel())
                        .forGetter(FountainSettings::resistanceLevel),
                field(wholeNumber(0, MAX_SECONDS), "regeneration_linger_seconds", DEFAULT.regenerationLingerSeconds())
                        .forGetter(FountainSettings::regenerationLingerSeconds),
                field(wholeNumber(0, MAX_SECONDS), "resistance_linger_seconds", DEFAULT.resistanceLingerSeconds())
                        .forGetter(FountainSettings::resistanceLingerSeconds),
                field(wholeNumber(1, MAX_SECONDS), "duration_seconds", DEFAULT.durationSeconds())
                        .forGetter(FountainSettings::durationSeconds),
                field(wholeNumber(0, MAX_SECONDS), "cooldown_seconds", DEFAULT.cooldownSeconds())
                        .forGetter(FountainSettings::cooldownSeconds)
        ).apply(instance, FountainSettings::new));

        public FountainSettings {
            TokenSettingValidation.requireFinite(healthThreshold, "health_threshold");
            if (healthThreshold < 0 || healthThreshold > 1) {
                throw TokenSettingValidation.invalid("health_threshold must be between 0 and 1");
            }
            if (regenerationLevel < 1 || regenerationLevel > MAX_LEVEL || resistanceLevel < 1 || resistanceLevel > MAX_LEVEL) {
                throw TokenSettingValidation.invalid("effect levels must be between 1 and " + MAX_LEVEL);
            }
            if (durationSeconds < 1 || durationSeconds > MAX_SECONDS || cooldownSeconds < 0 || cooldownSeconds > MAX_SECONDS
                    || regenerationLingerSeconds < 0 || regenerationLingerSeconds > MAX_SECONDS
                    || resistanceLingerSeconds < 0 || resistanceLingerSeconds > MAX_SECONDS) {
                throw TokenSettingValidation.invalid("durations must fit in ticks and duration_seconds must be positive");
            }
        }

        int regenerationLingerTicks() { return regenerationLingerSeconds * 20; }
        int resistanceLingerTicks() { return resistanceLingerSeconds * 20; }
        int durationTicks() { return durationSeconds * 20; }
        int cooldownTicks() { return cooldownSeconds * 20; }

        int maxLingerTicks() { return Math.max(regenerationLingerTicks(), resistanceLingerTicks()); }
    }

    // JsonOps' integer codec would silently truncate fractional input.
    private static Codec<Integer> wholeNumber(int min, int max) {
        return Codec.DOUBLE.validate(value -> value >= min && value <= max && value == Math.rint(value)
                ? DataResult.success(value) : DataResult.error(() -> "Expected an integer between " + min + " and " + max))
                .xmap(Double::intValue, Integer::doubleValue);
    }

    public static final TokenConfig DEFAULT = new TokenConfig(
            SoulSiphonSettings.DEFAULT, EchoSettings.DEFAULT, ImmediateCounterSettings.DEFAULT,
            VenomousTipSettings.DEFAULT, PoisonedArrowSettings.DEFAULT, AsuraFormSettings.DEFAULT,
            ResonanceHealingSettings.DEFAULT, VernalTideSettings.DEFAULT, ArroganceSettings.DEFAULT,
            ExecutionEchoSettings.DEFAULT, RetributionSettings.DEFAULT, SubstituteSettings.DEFAULT, FullBlessingSettings.DEFAULT,
            FountainSettings.DEFAULT);

    public static TokenConfig fromJson(JsonObject tokens) {
        return fromJson(tokens, TokenExtensions.EMPTY);
    }

    public static TokenConfig fromJson(JsonObject tokens, TokenExtensions extensions) {
        warnAboutUnknownTokens(tokens, extensions);
        return new TokenConfig(
                section(tokens, "runelegend:soul_siphon", SoulSiphonSettings.CODEC, SoulSiphonSettings.DEFAULT),
                section(tokens, "runelegend:echo", EchoSettings.CODEC, EchoSettings.DEFAULT),
                section(tokens, "runelegend:immediate_counter", ImmediateCounterSettings.CODEC, ImmediateCounterSettings.DEFAULT),
                section(tokens, "runelegend:venomous_tip", VenomousTipSettings.CODEC, VenomousTipSettings.DEFAULT),
                section(tokens, "runelegend:poisoned_arrow", PoisonedArrowSettings.CODEC, PoisonedArrowSettings.DEFAULT),
                section(tokens, "runelegend:asura_form", AsuraFormSettings.CODEC, AsuraFormSettings.DEFAULT),
                section(tokens, "runelegend:resonance_healing", ResonanceHealingSettings.CODEC,
                        ResonanceHealingSettings.DEFAULT),
                section(tokens, "runelegend:vernal_tide", VernalTideSettings.CODEC, VernalTideSettings.DEFAULT),
                section(tokens, "runelegend:arrogance", ArroganceSettings.CODEC, ArroganceSettings.DEFAULT),
                section(tokens, "runelegend:execution_echo", ExecutionEchoSettings.CODEC, ExecutionEchoSettings.DEFAULT),
                section(tokens, "runelegend:retribution", RetributionSettings.CODEC, RetributionSettings.DEFAULT),
                section(tokens, "runelegend:substitute", SubstituteSettings.CODEC, SubstituteSettings.DEFAULT),
                section(tokens, "runelegend:full_blessing", FullBlessingSettings.CODEC, FullBlessingSettings.DEFAULT),
                section(tokens, "runelegend:fountain_of_life", FountainSettings.CODEC, FountainSettings.DEFAULT));
    }

    public JsonObject toJson() {
        var tokens = new JsonObject();
        tokens.add("runelegend:soul_siphon", encode(SoulSiphonSettings.CODEC, soulSiphon));
        tokens.add("runelegend:echo", encode(EchoSettings.CODEC, echo));
        tokens.add("runelegend:immediate_counter", encode(ImmediateCounterSettings.CODEC, immediateCounter));
        tokens.add("runelegend:venomous_tip", encode(VenomousTipSettings.CODEC, venomousTip));
        tokens.add("runelegend:poisoned_arrow", encode(PoisonedArrowSettings.CODEC, poisonedArrow));
        tokens.add("runelegend:asura_form", encode(AsuraFormSettings.CODEC, asuraForm));
        tokens.add("runelegend:resonance_healing", encode(ResonanceHealingSettings.CODEC, resonanceHealing));
        tokens.add("runelegend:vernal_tide", encode(VernalTideSettings.CODEC, vernalTide));
        tokens.add("runelegend:arrogance", encode(ArroganceSettings.CODEC, arrogance));
        tokens.add("runelegend:execution_echo", encode(ExecutionEchoSettings.CODEC, executionEcho));
        tokens.add("runelegend:retribution", encode(RetributionSettings.CODEC, retribution));
        tokens.add("runelegend:substitute", encode(SubstituteSettings.CODEC, substitute));
        tokens.add("runelegend:full_blessing", encode(FullBlessingSettings.CODEC, fullBlessing));
        tokens.add("runelegend:fountain_of_life", encode(FountainSettings.CODEC, fountainOfLife));
        return tokens;
    }

    private static void warnAboutUnknownTokens(JsonObject tokens, TokenExtensions extensions) {
        for (var key : tokens.keySet()) {
            if (KNOWN_IDS.contains(key) || extensions.contains(key)) continue;
            try {
                Identifier.parse(key);
                RuneLegendMod.LOGGER.warn("Unknown RuneLegend token config entry {} ignored", key);
            } catch (RuntimeException error) {
                RuneLegendMod.LOGGER.warn("Invalid RuneLegend token config ID {} ignored", key);
            }
        }
    }

    static <T> T section(JsonObject tokens, String id, Codec<T> codec, T fallback) {
        var element = tokens.get(id);
        if (element == null) return fallback;
        if (!element.isJsonObject()) {
            RuneLegendMod.LOGGER.error("Invalid RuneLegend config for token {}; expected an object, using defaults", id);
            return fallback;
        }
        try {
            return codec.parse(JsonOps.INSTANCE, element).result().orElseGet(() -> {
                RuneLegendMod.LOGGER.error("Invalid RuneLegend config for token {}; using defaults", id);
                return fallback;
            });
        } catch (RuntimeException error) {
            RuneLegendMod.LOGGER.error("Invalid RuneLegend config for token {}; using defaults", id, error);
            return fallback;
        }
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
