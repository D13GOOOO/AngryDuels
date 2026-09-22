package com.angryguyy.duels.duel;

/**
 * Enumerates the possible reasons a duel can end.
 *
 * <p>This value is attached to the {@code DuelEndEvent} and is used by
 * statistics, rewards, and message layers to differentiate between a
 * legitimate kill, a forfeit, a disconnect, or a plugin-driven
 * cancellation.</p>
 */
public enum DuelEndReason {

    /**
     * A duelist was killed by their opponent during an active duel.
     */
    PLAYER_DIED,

    /**
     * A duelist disconnected while the duel was active.
     */
    PLAYER_QUIT,

    /**
     * A duelist explicitly gave up using the forfeit command.
     */
    FORFEIT,

    /**
     * The duel ended without a winner, for example due to a mutual
     * simultaneous death.
     */
    DRAW,

    /**
     * The duel was cancelled before it could reach a conclusion, for
     * example because a spawn or world became unavailable.
     */
    CANCELLED,

    /**
     * The server is shutting down while the duel was still active.
     */
    PLUGIN_DISABLE
}