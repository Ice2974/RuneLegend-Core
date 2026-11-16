package com.runelegend.client.hud;

import com.runelegend.token.TokenEngine;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Presentation only. Times are monotonic client milliseconds, not server ticks. */
public final class TokenHudAnimation {
    private static final long PHASE_MS = 1_000;
    private static final long FRAME_MS = 50;
    static final int FRAME_COUNT = 8;
    private static final long GLOW_MS = FRAME_COUNT * FRAME_MS;

    public record Display(float opacity, int glowFrame) {}
    private static final Display DIM = new Display(0.5F, -1);
    private static final Display BRIGHT = new Display(1F, -1);

    private static final class Slot {
        long lastTrigger;
        long triggeredAt;
        long preparingAt;
        boolean active;
        boolean preparing;
        long queuedFlashes;
        long flashStarted;
        boolean playingFlash;

        void update(TokenEngine.View view, long now) {
            int flags = view.flags();
            boolean triggered = (flags & TokenEngine.TRIGGERED) != 0 && view.triggeredUntil() > lastTrigger;
            if (triggered) {
                lastTrigger = view.triggeredUntil();
                triggeredAt = now;
            }
            boolean nextPreparing = (flags & TokenEngine.PREPARING) != 0;
            boolean nextActive = (flags & TokenEngine.ACTIVE) != 0;
            if (nextPreparing && (!preparing || triggered || (active && !nextActive))) {
                preparingAt = lastTrigger == 0 ? now : Math.max(now, triggeredAt + PHASE_MS);
            }
            active = nextActive;
            preparing = nextPreparing;
        }

        Display display(long now) {
            if (playingFlash && now - flashStarted >= GLOW_MS) playingFlash = false;
            if (!playingFlash && queuedFlashes > 0) {
                queuedFlashes--;
                playingFlash = true;
                flashStarted = now;
            }
            if (playingFlash) return bright(now - flashStarted);
            if (lastTrigger != 0 && now - triggeredAt < PHASE_MS) return bright(now - triggeredAt);
            if (active) return BRIGHT;
            if (!preparing || now < preparingAt) return DIM;
            long elapsed = now - preparingAt;
            return (elapsed / PHASE_MS) % 2 == 0 ? DIM : bright(elapsed % PHASE_MS);
        }
    }

    private final Map<Identifier, Slot> slots = new HashMap<>();

    public void update(List<TokenEngine.View> views, long now) {
        slots.keySet().removeIf(id -> views.stream().noneMatch(view -> view.id().equals(id)));
        for (var view : views) slots.computeIfAbsent(view.id(), id -> new Slot()).update(view, now);
    }

    public Display display(Identifier id, long now) {
        var slot = slots.get(id);
        return slot == null ? DIM : slot.display(now);
    }

    public void enqueueFlash(Identifier id) {
        slots.computeIfAbsent(id, ignored -> new Slot()).queuedFlashes++;
    }

    public void clear() { slots.clear(); }

    private static Display bright(long elapsed) {
        return elapsed >= 0 && elapsed < GLOW_MS ? new Display(1F, (int) (elapsed / FRAME_MS)) : BRIGHT;
    }
}
