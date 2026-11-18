package com.runelegend.player;

import com.runelegend.api.PlayerSnapshot;

/** Round identity, independent of vanilla health and game mode. WAITING has no round identity. */
public enum PlayerState {
    ALIVE(PlayerSnapshot.Identity.ALIVE),
    DOWNED(PlayerSnapshot.Identity.DOWNED),
    ELIMINATED(PlayerSnapshot.Identity.ELIMINATED),
    SPECTATOR(PlayerSnapshot.Identity.SPECTATOR);

    private final PlayerSnapshot.Identity identity;

    PlayerState(PlayerSnapshot.Identity identity) { this.identity = identity; }

    public PlayerSnapshot.Identity identity() { return identity; }
}
