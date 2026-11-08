package com.runelegend.resonance;

import com.runelegend.game.GameController;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-side business interface for resonance selection and layer data.
 *
 * <p>Layer reads are public, but raw layer writes intentionally stay package-private: phase three
 * mechanics must add controlled gain/loss entry points instead of exposing an arbitrary setter.</p>
 */
public final class ResonanceService {
    public enum Result { CHANGED, UNCHANGED, RUNNING, READ_ONLY, UNKNOWN, NOT_SELECTABLE }

    private final GameController game;
    private final ResonanceSavedData data;

    public ResonanceService(GameController game, ResonanceSavedData data) {
        this.game = game;
        this.data = data;
    }

    public Optional<Identifier> guardSelection(UUID player) {
        return data.guardSelection(player);
    }

    public Optional<ResonanceAttribute> guardAttribute(UUID player) {
        return guardSelection(player).flatMap(ResonanceAttribute::byId);
    }

    public Result equip(UUID player, Identifier attribute) {
        var rejection = rejectWrite();
        if (rejection != null) return rejection;
        if (attribute == null) return Result.UNKNOWN;
        var definition = ResonanceAttribute.byId(attribute);
        if (definition.isEmpty()) return Result.UNKNOWN;
        if (!definition.get().guardSelectable()) return Result.NOT_SELECTABLE;
        if (data.guardSelection(player).filter(attribute::equals).isPresent()) return Result.UNCHANGED;
        data.writeGuardSelection(player, Optional.of(attribute));
        return Result.CHANGED;
    }

    public Result unequip(UUID player) {
        var rejection = rejectWrite();
        if (rejection != null) return rejection;
        if (data.guardSelection(player).isEmpty()) return Result.UNCHANGED;
        data.writeGuardSelection(player, Optional.empty());
        return Result.CHANGED;
    }

    public ResonanceLayers layers(LivingEntity holder) {
        return layers(holder.getUUID());
    }

    public ResonanceLayers layers(UUID holder) {
        return data.layers(holder);
    }

    /**
     * Internal validated layer write for future effect/mechanism code and persistence tests.
     * No production code calls this in phase one.
     */
    boolean setLayers(LivingEntity holder, ResonanceLayers layers) {
        if (holder == null || layers == null || !(holder.level() instanceof ServerLevel) || !data.writable()) return false;
        var previous = data.layers(holder.getUUID());
        if (layers.destruction() > 0 && holder instanceof Player) return false;
        if (layers.destruction() > previous.destruction() && !ResonanceTags.destructionEligible(holder)) {
            return false;
        }
        return data.writeLayers(holder.getUUID(), layers);
    }

    /** Cleanup path: erasing data never checks destruction eligibility. No production caller in phase one. */
    public boolean clearLayers(LivingEntity holder) {
        if (holder == null || !data.writable()) return false;
        return data.eraseLayers(holder.getUUID());
    }

    private Result rejectWrite() {
        if (game.getRules().running()) return Result.RUNNING;
        return data.writable() ? null : Result.READ_ONLY;
    }
}
