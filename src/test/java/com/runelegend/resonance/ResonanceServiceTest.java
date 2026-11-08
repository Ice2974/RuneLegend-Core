package com.runelegend.resonance;

import com.runelegend.api.GameState;
import com.runelegend.api.RuleSnapshot;
import com.runelegend.game.GameController;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResonanceServiceTest {
    private static final Identifier PROTECTION = Identifier.parse("runelegend:protection");
    private static final Identifier LOST = Identifier.parse("runelegend:lost");
    private static final Identifier DESTRUCTION = Identifier.parse("runelegend:destruction");

    private static GameController waitingGame() {
        return new GameController(() -> {}, (previous, current) -> {});
    }

    @Test
    void defaultSelectionAndWaitingEquipFlow() {
        var player = UUID.randomUUID();
        var service = new ResonanceService(waitingGame(), new ResonanceSavedData(Map.of(), Map.of(), true));

        assertTrue(service.guardSelection(player).isEmpty());
        assertEquals(ResonanceService.Result.CHANGED, service.equip(player, PROTECTION));
        assertEquals(PROTECTION, service.guardSelection(player).orElseThrow());
        assertEquals(ResonanceService.Result.UNCHANGED, service.equip(player, PROTECTION));
        assertEquals(ResonanceService.Result.CHANGED, service.equip(player, LOST));
        assertEquals(LOST, service.guardSelection(player).orElseThrow());
        assertEquals(ResonanceService.Result.NOT_SELECTABLE, service.equip(player, DESTRUCTION));
        assertEquals(ResonanceService.Result.UNKNOWN, service.equip(player, Identifier.parse("runelegend:unknown")));
        assertEquals(LOST, service.guardSelection(player).orElseThrow());
        assertEquals(ResonanceService.Result.CHANGED, service.unequip(player));
        assertTrue(service.guardSelection(player).isEmpty());
        assertEquals(ResonanceService.Result.UNCHANGED, service.unequip(player));
    }

    @Test
    void runningStateRejectsEquipAndUnequipAtTheServiceLayer() {
        var player = UUID.randomUUID();
        var data = new ResonanceSavedData(Map.of(), Map.of(), true);
        var game = waitingGame();
        var service = new ResonanceService(game, data);
        assertEquals(ResonanceService.Result.CHANGED, service.equip(player, PROTECTION));

        game.startGame();
        assertEquals(GameState.RUNNING, game.getRules().state());
        assertEquals(ResonanceService.Result.RUNNING, service.equip(player, LOST));
        assertEquals(ResonanceService.Result.RUNNING, service.unequip(player));
        assertEquals(PROTECTION, service.guardSelection(player).orElseThrow());

        game.stopGame();
        assertEquals(ResonanceService.Result.CHANGED, service.equip(player, LOST));
    }

    @Test
    void readOnlyDataRejectsSelectionChanges() {
        var player = UUID.randomUUID();
        var service = new ResonanceService(waitingGame(), new ResonanceSavedData(Map.of(), Map.of(), false));
        assertEquals(ResonanceService.Result.READ_ONLY, service.equip(player, PROTECTION));
        assertEquals(ResonanceService.Result.READ_ONLY, service.unequip(player));
        assertTrue(service.guardSelection(player).isEmpty());
    }

    @Test
    void guardAttributeResolvesKnownIdsAndLayersDefaultToNone() {
        var player = UUID.randomUUID();
        var holder = UUID.randomUUID();
        var data = new ResonanceSavedData(Map.of(holder, new ResonanceLayers(1, 2, 3)),
                Map.of(player, LOST), true);
        var service = new ResonanceService(waitingGame(), data);

        assertTrue(service.guardAttribute(player).isPresent());
        assertEquals(ResonanceAttribute.LOST, service.guardAttribute(player).orElseThrow());
        assertEquals(new ResonanceLayers(1, 2, 3), service.layers(holder));
        assertEquals(ResonanceLayers.NONE, service.layers(UUID.randomUUID()));
    }

    @Test
    void restoredRoundStillControlsTheSelectionGate() {
        var player = UUID.randomUUID();
        var game = new GameController(() -> {}, (previous, current) -> {},
                new RuleSnapshot(GameState.RUNNING, 7, 9), ignored -> {});
        var service = new ResonanceService(game, new ResonanceSavedData(Map.of(), Map.of(), true));
        assertEquals(ResonanceService.Result.RUNNING, service.equip(player, PROTECTION));
        game.stopGame();
        assertEquals(ResonanceService.Result.CHANGED, service.equip(player, PROTECTION));
    }
}
