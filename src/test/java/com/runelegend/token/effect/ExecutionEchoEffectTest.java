package com.runelegend.token.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionEchoEffectTest {
    @Test void recordRoundsUpAndIgnoresFloatingPointNoise() {
        assertEquals(6, ExecutionEchoEffect.recordedDamage(6.0, 20));
        assertEquals(6, ExecutionEchoEffect.recordedDamage(6.00001, 20));
        assertEquals(7, ExecutionEchoEffect.recordedDamage(6.2, 20));
        assertEquals(1, ExecutionEchoEffect.recordedDamage(0.5, 20));
    }

    @Test void nothingIsRecordedForZeroNegativeOrNaNDamage() {
        assertEquals(0, ExecutionEchoEffect.recordedDamage(0.0, 20));
        assertEquals(0, ExecutionEchoEffect.recordedDamage(0.00001, 20));
        assertEquals(0, ExecutionEchoEffect.recordedDamage(-3.0, 20));
        assertEquals(0, ExecutionEchoEffect.recordedDamage(Double.NaN, 20));
    }

    @Test void recordIsCappedAfterRounding() {
        assertEquals(20, ExecutionEchoEffect.recordedDamage(20.0, 20));
        assertEquals(20, ExecutionEchoEffect.recordedDamage(20.3, 20));
        assertEquals(20, ExecutionEchoEffect.recordedDamage(1.0E9, 20));
        assertEquals(12, ExecutionEchoEffect.recordedDamage(11.5, 12));
    }

    @Test void durationRunsUntilEveryDecayStepHasRemovedTheRecord() {
        assertEquals(100, ExecutionEchoEffect.durationTicks(10, 2.0F, 20));
        assertEquals(60, ExecutionEchoEffect.durationTicks(5, 2.0F, 20));
        assertEquals(20, ExecutionEchoEffect.durationTicks(1, 2.0F, 20));
        assertEquals(400, ExecutionEchoEffect.durationTicks(20, 1.0F, 20));
        assertEquals(120, ExecutionEchoEffect.durationTicks(3, 0.5F, 20));
    }

    @Test void remainingDamageDropsOncePerIntervalAndNeverGoesNegative() {
        assertEquals(10, ExecutionEchoEffect.remainingDamage(10, 2.0F, 20, 0), 0.0001);
        assertEquals(10, ExecutionEchoEffect.remainingDamage(10, 2.0F, 20, 19), 0.0001);
        assertEquals(8, ExecutionEchoEffect.remainingDamage(10, 2.0F, 20, 20), 0.0001);
        assertEquals(2, ExecutionEchoEffect.remainingDamage(10, 2.0F, 20, 99), 0.0001);
        assertEquals(0, ExecutionEchoEffect.remainingDamage(10, 2.0F, 20, 100), 0.0001);
        assertEquals(0, ExecutionEchoEffect.remainingDamage(3, 2.0F, 20, 500), 0.0001);
    }

    @Test void partialFinalStepLeavesTheRemainderUntilTheDurationEnds() {
        int recorded = 5;
        assertEquals(60, ExecutionEchoEffect.durationTicks(recorded, 2.0F, 20));
        assertEquals(5, ExecutionEchoEffect.remainingDamage(recorded, 2.0F, 20, 0), 0.0001);
        assertEquals(3, ExecutionEchoEffect.remainingDamage(recorded, 2.0F, 20, 20), 0.0001);
        assertEquals(1, ExecutionEchoEffect.remainingDamage(recorded, 2.0F, 20, 40), 0.0001);
        assertEquals(1, ExecutionEchoEffect.remainingDamage(recorded, 2.0F, 20, 59), 0.0001);
    }
}
