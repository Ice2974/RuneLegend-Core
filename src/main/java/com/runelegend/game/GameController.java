package com.runelegend.game;

import com.runelegend.api.*;
import net.minecraft.resources.Identifier;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Publishes the new rule gate before applying teardown on the same thread. */
public final class GameController implements RuneLegendApi {
    @FunctionalInterface
    public interface TransitionListener {
        void changed(RuleSnapshot before, RuleSnapshot after, RoundSummary.Reason reason, Identifier outcomeId,
                     Predicate<UUID> participates);
    }

    private final Runnable checkThread;
    private final TransitionListener onChange;
    private final ArrayDeque<Runnable> pendingNotifications = new ArrayDeque<>();
    private RuleSnapshot rules = new RuleSnapshot(GameState.WAITING, 0, 0);
    private boolean transitioning;
    private boolean deferringNotifications;

    public GameController(Runnable checkThread, BiConsumer<RuleSnapshot, RuleSnapshot> onChange) {
        this(checkThread, (before, after, reason, outcomeId, participates) -> onChange.accept(before, after));
        Objects.requireNonNull(onChange);
    }

    public GameController(Runnable checkThread, TransitionListener onChange) {
        this.checkThread = Objects.requireNonNull(checkThread);
        this.onChange = Objects.requireNonNull(onChange);
    }

    @Override public RuleSnapshot getRules() { checkThread.run(); return rules; }
    @Override public boolean startGame() { return update(GameState.RUNNING, null, null, _ -> true); }
    @Override public boolean stopGame() { return update(GameState.WAITING, RoundSummary.Reason.MANUAL_STOP, null, null); }

    public boolean startGame(Collection<UUID> participants) {
        return update(GameState.RUNNING, null, null, Set.copyOf(participants)::contains);
    }

    public boolean finishGame(Identifier outcomeId) {
        return update(GameState.WAITING, RoundSummary.Reason.FINISH, Objects.requireNonNull(outcomeId), null);
    }

    public void publishNotification(Runnable notification) {
        withDeferredNotifications(() -> pendingNotifications.addLast(notification));
    }

    /** Finishes a group of commits before callbacks can change the state they operated on. */
    public void withDeferredNotifications(Runnable action) {
        checkThread.run();
        if (deferringNotifications) {
            action.run();
            return;
        }
        deferringNotifications = true;
        try {
            action.run();
            while (!pendingNotifications.isEmpty()) pendingNotifications.removeFirst().run();
        } finally {
            pendingNotifications.clear();
            deferringNotifications = false;
        }
    }

    private boolean update(GameState state, RoundSummary.Reason reason, Identifier outcomeId, Predicate<UUID> participates) {
        checkThread.run();
        var previous = rules;
        if (transitioning || deferringNotifications || state == previous.state()) return false;
        long round = previous.round() + (state == GameState.RUNNING ? 1 : 0);
        rules = new RuleSnapshot(state, round, previous.revision() + 1);
        transitioning = true;
        try {
            onChange.changed(previous, rules, reason, outcomeId, participates);
            return true;
        } finally {
            transitioning = false;
        }
    }
}
