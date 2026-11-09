package com.runelegend.combat;

import com.runelegend.game.GameController;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class GuardStaminaService {
    private final Map<UUID, PlayerCombatRuntime> players = new HashMap<>();
    private final GameController game;
    private final LongSupplier clock;
    private final StaminaManager stamina;

    public GuardStaminaService(GameController game, LongSupplier clock, GuardConfig config) {
        this.game = game;
        this.clock = clock;
        this.stamina = new StaminaManager(config);
    }

    /** Server-wide tuning used by the guard stamina calculations and the client HUD sync. */
    public GuardConfig config() { return stamina.config(); }

    public void join(ServerPlayer player) { players.put(player.getUUID(), new PlayerCombatRuntime(stamina.config())); }

    public void leave(ServerPlayer player) { players.remove(player.getUUID()); }

    public int stamina(ServerPlayer player) {
        var runtime = players.get(player.getUUID());
        return runtime == null ? stamina.max() : runtime.stamina;
    }

    public boolean tryStart(ServerPlayer player) {
        var runtime = players.get(player.getUUID());
        boolean running = game.getRules().running();
        if (runtime == null || !player.isAlive() || player.isSpectator() || running && runtime.releaseRequired) return false;
        runtime.releaseRequired = true;
        if (running && !stamina.canStart(runtime.stamina)) return false;
        runtime.startedAt = clock.getAsLong();
        return true;
    }

    public void released(ServerPlayer player) {
        var runtime = players.get(player.getUUID());
        if (runtime != null) runtime.releaseRequired = false;
    }

    public void stopped(ServerPlayer player) {
        var runtime = players.get(player.getUUID());
        if (runtime != null && runtime.startedAt != PlayerCombatRuntime.NOT_USING) {
            runtime.startedAt = PlayerCombatRuntime.NOT_USING;
            runtime.stoppedAt = clock.getAsLong();
        }
    }

    public void resetRound(ServerPlayer player) {
        var runtime = players.get(player.getUUID());
        if (runtime != null) {
            runtime.stamina = stamina.max();
            runtime.releaseRequired = GuardItems.using(player);
        }
    }

    // A command or another mod can replace the active stack between vanilla entity ticks.
    public void validateUse(ServerPlayer player) {
        var runtime = players.get(player.getUUID());
        if (runtime == null) return;
        boolean using = GuardItems.using(player);
        if (using && (runtime.startedAt == PlayerCombatRuntime.NOT_USING || !player.isAlive() || player.isSpectator()
                || player.getUseItem() != player.getItemInHand(player.getUsedItemHand()))) {
            player.stopUsingItem();
        } else if (!using) {
            stopped(player);
        }
    }

    public void tick(ServerPlayer player) {
        var runtime = players.get(player.getUUID());
        if (runtime == null) return;
        validateUse(player);
        if (!game.getRules().running()) return;
        if (GuardItems.using(player)) {
            runtime.stamina = stamina.drain(runtime.stamina);
            if (runtime.stamina == 0) player.stopUsingItem();
        } else if (player.isAlive() && runtime.stamina < stamina.max()) {
            runtime.stamina = stamina.recover(runtime.stamina, clock.getAsLong(), runtime.stoppedAt);
        }
    }
}
