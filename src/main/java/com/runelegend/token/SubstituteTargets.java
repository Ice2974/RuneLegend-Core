package com.runelegend.token;

import com.runelegend.RuneLegendServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.warden.Warden;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Selects from this round's live mannequins when vanilla has chosen a player as a target. */
public final class SubstituteTargets {
    private record WardenTarget(UUID original, SubstituteMannequin substitute) {}

    private final Map<ServerLevel, Set<SubstituteMannequin>> active = new HashMap<>();
    private final Map<Warden, WardenTarget> wardenTargets = new WeakHashMap<>();

    void add(ServerLevel level, SubstituteMannequin model) {
        active.computeIfAbsent(level, _ -> new HashSet<>()).add(model);
    }

    void remove(ServerLevel level, SubstituteMannequin model) {
        var models = active.get(level);
        if (models != null && models.remove(model) && models.isEmpty()) active.remove(level);
        wardenTargets.values().removeIf(target -> target.substitute() == model);
    }

    public static LivingEntity redirect(Mob mob, LivingEntity target) {
        return redirect(mob, target, _ -> true);
    }

    public static LivingEntity redirect(Mob mob, LivingEntity target, Predicate<LivingEntity> canTarget) {
        if (!(target instanceof ServerPlayer player) || player.isCreative() || !(mob.level() instanceof ServerLevel level)
                || player.level() != level || mob.isNoAi()) return target;
        var server = RuneLegendServer.find(level.getServer());
        if (server == null) return target;
        var index = server.tokens().substitutes();
        var models = index.active.get(level);
        if (models == null || !server.tokens().eligible(player) || !player.isAlive() || !TokenTargeting.enemy(mob)) return target;

        double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        SubstituteMannequin best = null;
        double bestDistance = range * range;
        for (var model : models) {
            double distance = mob.distanceToSqr(model);
            if (!model.isAlive() || distance > bestDistance || !mob.canAttack(model) || !canTarget.test(model)
                    || mob instanceof Warden warden && !warden.canTargetEntity(model)
                    || !mob.getSensing().hasLineOfSight(model)) continue;
            if (best == null || distance < bestDistance || model.getUUID().compareTo(best.getUUID()) < 0) {
                best = model;
                bestDistance = distance;
            }
        }
        if (best == null) return target;
        if (mob instanceof Warden warden) index.wardenTargets.put(warden, new WardenTarget(player.getUUID(), best));
        return best;
    }

    /** Keep the Warden's anger on the player even while its attack memory points at a mannequin. */
    public static Entity angerSource(Warden warden, Entity target) {
        if (!(target instanceof SubstituteMannequin) || !(warden.level() instanceof ServerLevel level)) return target;
        var server = RuneLegendServer.find(level.getServer());
        if (server == null) return target;
        var redirected = server.tokens().substitutes().wardenTargets.get(warden);
        if (redirected == null || redirected.substitute() != target) return target;
        var original = server.currentPlayer(redirected.original());
        return original != null && original.level() == level && server.tokens().eligible(original) ? original : target;
    }
}
