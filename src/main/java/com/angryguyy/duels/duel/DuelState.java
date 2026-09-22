package com.angryguyy.duels.duel;

/**
 * Coarse-grained state of a player with respect to duels.
 *
 * <p>This enum is used by UI code to quickly determine whether a player
 * can send a request, must resolve an existing one, or is already busy
 * fighting. It is derived from the maps held by the
 * {@link DuelManager} and is not stored anywhere.</p>
 */
public enum DuelState {

    /**
     * The player has no pending request and is not in a duel.
     */
    IDLE,

    /**
     * The player has an outgoing or incoming request that has not been
     * accepted or denied yet.
     */
    IN_REQUEST,

    /**
     * The player is currently participating in an active duel.
     */
    IN_DUEL
}