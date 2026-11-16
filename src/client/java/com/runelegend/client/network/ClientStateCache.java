package com.runelegend.client.network;

import com.runelegend.api.RuleSnapshot;
import com.runelegend.client.hud.TokenHudAnimation;
import com.runelegend.network.RuneLegendPayloads;
import net.minecraft.util.Util;
import static com.runelegend.network.RuneLegendPayloads.*;

public final class ClientStateCache {
    public static final ClientStateCache INSTANCE = new ClientStateCache();
    private RuleSnapshot rules;
    private View view;
    private long revision = -1;
    private long serverTick;
    private long localSampleTick;
    private long tick;
    private boolean desynced;
    private final TokenHudAnimation tokenAnimation = new TokenHudAnimation();

    private ClientStateCache() {}

    public RuleSnapshot rules() { return rules; }
    public View view() { return desynced ? null : view; }
    public long tick() { return tick; }
    public long serverTick() { return serverTick + Math.max(0, tick - localSampleTick); }
    public boolean desynced() { return desynced; }
    public TokenHudAnimation tokenAnimation() { return tokenAnimation; }

    public void advanceTick() { tick++; }

    public void apply(Rules packet) {
        if (rules != null && packet.rules().revision() < rules.revision()) return;
        var next = packet.rules();
        if (!next.running() || rules == null || rules.round() != next.round()) tokenAnimation.clear();
        rules = next;
    }

    public void apply(PlayerSnapshot packet) {
        if (packet.revision() < revision) return;
        view = packet.state();
        desynced = false;
        updateClock(packet.revision(), packet.tick());
        updateTokenAnimation();
    }

    public void apply(PlayerDelta packet) {
        if (packet.revision() <= revision) return;
        if (view == null || packet.base() != revision) {
            desynced = true;
            return;
        }
        view = RuneLegendPayloads.merge(view, packet.state(), packet.mask());
        updateClock(packet.revision(), packet.tick());
        if ((packet.mask() & TOKENS) != 0 && !desynced) updateTokenAnimation();
    }

    public void apply(TokenFlash packet) {
        if (rules == null || !rules.running() || rules.round() != packet.round() || view == null
                || view.tokens().stream().noneMatch(token -> token.id().equals(packet.tokenId()))) return;
        tokenAnimation.enqueueFlash(packet.tokenId());
    }

    private void updateTokenAnimation() {
        if (rules != null && rules.running()) tokenAnimation.update(view.tokens(), Util.getMillis());
    }

    private void updateClock(long newRevision, long sample) {
        revision = newRevision;
        serverTick = sample;
        localSampleTick = tick;
    }

    public void clear() {
        tokenAnimation.clear();
        rules = null;
        view = null;
        revision = -1;
        serverTick = 0;
        localSampleTick = tick;
        desynced = false;
    }
}
