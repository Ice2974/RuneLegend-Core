package com.runelegend.game;

import com.runelegend.api.*;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.function.BiConsumer;

/** Publishes the new rule gate before applying teardown on the same thread. */
public final class GameController implements RuneLegendApi {
    @FunctionalInterface
    public interface TransitionListener {
        void changed(RuleSnapshot before, RuleSnapshot after, RoundSummary.Reason reason, Identifier outcomeId);
    }

    private final Runnable checkThread;
    private final TransitionListener onChange;
    private RuleSnapshot rules = new RuleSnapshot(GameState.WAITING, 0, 0);
    private boolean transitioning;

    public GameController(Runnable checkThread, BiConsumer<RuleSnapshot, RuleSnapshot> onChange) {
        this(checkThread, (before, after, reason, outcomeId) -> onChange.accept(before, after));
        Objects.requireNonNull(onChange);
    }

    public GameController(Runnable checkThread, TransitionListener onChange) {
        this.checkThread = Objects.requireNonNull(checkThread);
        this.onChange = Objects.requireNonNull(onChange);
    }

    @Override public RuleSnapshot getRules() { checkThread.run(); return rules; }
    @Override public boolean startGame() { return update(GameState.RUNNING, null, null); }
    @Override public boolean stopGame() { return update(GameState.WAITING, RoundSummary.Reason.MANUAL_STOP, null); }

    public boolean finishGame(Identifier outcomeId) {
        return update(GameState.WAITING, RoundSummary.Reason.FINISH, Objects.requireNonNull(outcomeId));
    }

    public boolean endWithoutSurvivors() {
        return update(GameState.WAITING, RoundSummary.Reason.NO_ALIVE, null);
    }

    private boolean update(GameState state, RoundSummary.Reason reason, Identifier outcomeId) {
        checkThread.run();
        var previous = rules;
        if (transitioning || state == previous.state()) return false;
        long round = previous.round() + (state == GameState.RUNNING ? 1 : 0);
        rules = new RuleSnapshot(state, round, previous.revision() + 1);
        transitioning = true;
        try {
            onChange.changed(previous, rules, reason, outcomeId);
            return true;
        } finally {
            transitioning = false;
        }
    }
}
