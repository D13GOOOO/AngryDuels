package com.angryguyy.duels.duel;

/**
 * Represents the current phase of an active duel.
 *
 * <p>The phase determines which kind of damage is allowed by the
 * protection listener: during {@link #COUNTDOWN} all incoming damage
 * is cancelled, whereas during {@link #ACTIVE} normal PvP rules apply
 * between the two duelists.</p>
 */
public enum DuelPhase {

    /**
     * The duel has been created but the countdown is still running.
     * Players are at their spawns and cannot damage each other.
     */
    COUNTDOWN,

    /**
     * The countdown has finished and the duel is live. PvP between the
     * two duelists is enabled.
     */
    ACTIVE
}