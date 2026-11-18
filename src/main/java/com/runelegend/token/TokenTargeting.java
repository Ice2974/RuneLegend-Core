package com.runelegend.token;

import com.runelegend.RuneLegendMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

public final class TokenTargeting {
    public static final TagKey<EntityType<?>> GHOST_FAMILY = TagKey.create(Registries.ENTITY_TYPE, RuneLegendMod.id("ghost_family"));
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, RuneLegendMod.id("bosses"));
    public static final TagKey<EntityType<?>> ENEMY_TYPES = TagKey.create(Registries.ENTITY_TYPE, RuneLegendMod.id("token_enemies"));

    private TokenTargeting() {}

    public static boolean ghostFamily(Entity entity) { return inTag(entity, GHOST_FAMILY); }
    public static boolean boss(Entity entity) { return inTag(entity, BOSSES); }
    public static boolean substitute(Entity entity) { return entity instanceof SubstituteMannequin; }

    public static boolean enemy(Entity entity) {
        if (entity == null || entity instanceof Player || substitute(entity)) return false;
        var configured = BuiltInRegistries.ENTITY_TYPE.get(ENEMY_TYPES);
        if (configured.isPresent() && configured.get().size() > 0) {
            return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()).is(ENEMY_TYPES);
        }
        return entity instanceof Enemy;
    }

    private static boolean inTag(Entity entity, TagKey<EntityType<?>> tag) {
        return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()).is(tag);
    }
}
