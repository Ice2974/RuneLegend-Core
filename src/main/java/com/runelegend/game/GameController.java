package com.runelegend.game;

import com.runelegend.api.*;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Publishes the new rule gate before applying teardown on the same thread. */
public final class GameController implements RuneLegendApi {
    private final Runnable checkThread;
    private final BiConsumer<RuleSnapshot, RuleSnapshot> onChange;
    private RuleSnapshot rules = new RuleSnapshot(GameState.WAITING, 0, 0);

    public GameController(Runnable checkThread, BiConsumer<RuleSnapshot, RuleSnapshot> onChange) {
        this.checkThread = Objects.requireNonNull(checkThread);
        this.onChange = Objects.requireNonNull(onChange);
    }

    @Override public RuleSnapshot getRules() { checkThread.run(); return rules; }
    @Override public boolean startGame() { checkThread.run(); return update(GameState.RUNNING); }
    @Override public boolean stopGame() { checkThread.run(); return update(GameState.WAITING); }

    private boolean update(GameState state) {
        var previous = rules;
        if (state == previous.state()) return false;
        long round = previous.round() + (state == GameState.RUNNING && !previous.running() ? 1 : 0);
        rules = new RuleSnapshot(state, round, previous.revision() + 1);
        onChange.accept(previous, rules);
        return true;
    }
}
