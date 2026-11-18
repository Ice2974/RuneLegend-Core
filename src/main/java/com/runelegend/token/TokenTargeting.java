package com.runelegend.token;

import com.runelegend.RuneLegendMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Optional;

public final class TokenTargeting {
    public static final TagKey<EntityType<?>> GHOST_FAMILY = TagKey.create(Registries.ENTITY_TYPE, RuneLegendMod.id("ghost_family"));
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, RuneLegendMod.id("bosses"));
    public static final TagKey<EntityType<?>> ENEMY_TYPES = TagKey.create(Registries.ENTITY_TYPE, RuneLegendMod.id("token_enemies"));
    private static final ResourceKey<LootItemCondition> ENEMY_PREDICATE = ResourceKey.create(Registries.PREDICATE, ENEMY_TYPES.location());

    private TokenTargeting() {}

    public static boolean ghostFamily(Entity entity) { return inTag(entity, GHOST_FAMILY); }
    public static boolean boss(Entity entity) { return inTag(entity, BOSSES); }
    public static boolean substitute(Entity entity) { return entity instanceof SubstituteMannequin; }

    public static boolean enemy(Entity entity) {
        if (entity == null || entity instanceof Player || substitute(entity)) return false;
        var configured = BuiltInRegistries.ENTITY_TYPE.get(ENEMY_TYPES);
        boolean eligibleType = configured.isPresent() && configured.get().size() > 0
                ? inTag(entity, ENEMY_TYPES) : entity instanceof Enemy;
        return eligibleType && matchesEnemyPredicate(entity);
    }

    private static boolean matchesEnemyPredicate(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        var predicate = level.getServer().reloadableRegistries().lookup().get(ENEMY_PREDICATE);
        if (predicate.isEmpty()) return true;

        var params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .create(LootContextParamSets.SELECTOR);
        var context = new LootContext.Builder(params).create(Optional.empty());
        var condition = predicate.get().value();
        context.pushVisitedElement(LootContext.createVisitedEntry(condition));
        return condition.test(context);
    }

    private static boolean inTag(Entity entity, TagKey<EntityType<?>> tag) {
        return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()).is(tag);
    }
}
