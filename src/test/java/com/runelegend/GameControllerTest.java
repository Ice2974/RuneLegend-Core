package com.runelegend;

import com.runelegend.api.GameState;
import com.runelegend.api.RuleSnapshot;
import com.runelegend.game.GameController;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameControllerTest {
    @Test void onlyRealTransitionsReviseTheRulesAndOnlyStartsAdvanceTheRound() {
        var transitions = new ArrayList<RuleSnapshot>();
        var game = new GameController(() -> {}, (old, current) -> transitions.add(current));
        assertEquals(new RuleSnapshot(GameState.WAITING, 0, 0), game.getRules());
        assertFalse(game.stopGame());
        assertTrue(game.startGame());
        assertTrue(game.getRules().running());
        assertFalse(game.startGame());
        assertEquals(1, game.getRules().round());
        assertTrue(game.stopGame());
        assertFalse(game.stopGame());
        assertEquals(2, transitions.size());
        assertEquals(2, game.getRules().revision());
        assertTrue(game.startGame());
        assertEquals(2, game.getRules().round());
        assertEquals(3, game.getRules().revision());
    }
}
