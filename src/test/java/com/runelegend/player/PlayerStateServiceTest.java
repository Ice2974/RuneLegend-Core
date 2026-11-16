package com.runelegend.player;

import com.runelegend.game.GameController;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlayerStateServiceTest {
    private final GameController game = new GameController(() -> {}, (_, _) -> {});
    private final PlayerStateService states = new PlayerStateService(game);
    private final UUID player = UUID.randomUUID(), spectator = UUID.randomUUID();

    @Test void selectionIsConsumedOnceAndWaitingHasNoRoundIdentity() {
        assertTrue(states.selectSpectator(spectator, true));
        states.leave(spectator);
        states.join(spectator);
        assertTrue(states.spectateNextRound(spectator));
        assertNull(states.state(player));
        game.startGame();
        states.beginRound(List.of(player, spectator));
        assertTrue(states.alive(player));
        assertEquals(PlayerState.SPECTATOR, states.state(spectator));
        assertFalse(states.selectSpectator(player, true));
        assertFalse(states.spectateNextRound(spectator));
        game.stopGame(); states.clearRound(); game.startGame(); states.beginRound(List.of(player, spectator));
        assertTrue(states.alive(spectator));
    }

    @Test void disconnectAndDuplicateLoginNeverRestoreCombatIdentity() {
        game.startGame(); states.beginRound(List.of(player, spectator));
        assertTrue(states.enterDowned(player));
        assertFalse(states.enterDowned(player));
        states.leave(player); states.join(player);
        assertEquals(PlayerState.ELIMINATED, states.state(player));
        assertFalse(states.completeRevive(player));
        states.join(spectator);
        assertEquals(PlayerState.ELIMINATED, states.state(spectator));
        var newcomer = UUID.randomUUID();
        states.join(newcomer); states.leave(newcomer); states.join(newcomer);
        assertEquals(PlayerState.SPECTATOR, states.state(newcomer));
        assertFalse(states.hasAlive());
    }

    @Test void sessionsAndServersAreIsolated() {
        game.startGame(); states.beginRound(List.of(player)); states.enterDowned(player);
        assertTrue(states.completeRevive(player));
        assertFalse(states.completeRevive(player));
        var other = new PlayerStateService(new GameController(() -> {}, (_, _) -> {}));
        assertNull(other.state(player));
        game.stopGame(); states.clearRound(); states.clearRound();
        assertNull(states.state(player));
        assertFalse(states.hasAlive());
    }
}
