package com.runelegend.player;

/** Round identity, independent of vanilla health and game mode. WAITING has no round identity. */
public enum PlayerState {
    ALIVE, DOWNED, ELIMINATED, SPECTATOR
}
