package com.runelegend.resonance;

import com.runelegend.RuneLegendMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Data-pack interface for entities that may gain destruction layers in later phases. */
public final class ResonanceTags {
    public static final TagKey<EntityType<?>> DESTRUCTION_ELIGIBLE =
            TagKey.create(Registries.ENTITY_TYPE, RuneLegendMod.id("destruction_eligible"));

    private ResonanceTags() {}

    public static boolean destructionEligible(Entity entity) {
        return destructionEligible(entity.getType());
    }

    public static boolean destructionEligible(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(DESTRUCTION_ELIGIBLE);
    }
}
