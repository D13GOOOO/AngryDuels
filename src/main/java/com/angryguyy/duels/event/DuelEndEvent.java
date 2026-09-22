package com.angryguyy.duels.event;

import com.angryguyy.duels.duel.DuelEndReason;
import com.angryguyy.duels.duel.DuelSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired after a duel session has been terminated.
 *
 * <p>This event is fired by the {@code DuelManager} for every possible
 * end scenario, including kills, forfeits, disconnects, cancellations
 * and server shutdown. It is <b>not</b> cancellable: the duel has
 * already concluded by the time listeners receive it.</p>
 *
 * <p>Listeners can use this event to update statistics, run reward
 * scripts, broadcast results, or trigger any downstream effect that
 * should follow a duel. The {@code winner} and {@code loser} may be
 * {@code null} for scenarios where no opponent was determined, such as
 * a cancelled duel or a shut down server.</p>
 */
public class DuelEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final DuelSession session;
    private final @Nullable Player winner;
    private final @Nullable Player loser;
    private final DuelEndReason reason;

    /**
     * Creates a new duel end event.
     *
     * @param session the session that just ended
     * @param winner  the winning player, or {@code null} if there is
     *                no winner
     * @param loser   the losing player, or {@code null} if there is
     *                no loser
     * @param reason  the reason the duel ended
     */
    public DuelEndEvent(DuelSession session, @Nullable Player winner, @Nullable Player loser, DuelEndReason reason) {
        this.session = session;
        this.winner = winner;
        this.loser = loser;
        this.reason = reason;
    }

    /**
     * Returns the session that just ended.
     *
     * @return the ended session
     */
    public DuelSession getSession() {
        return session;
    }

    /**
     * Returns the winning player, if any.
     *
     * @return winner, or {@code null}
     */
    public @Nullable Player getWinner() {
        return winner;
    }

    /**
     * Returns the losing player, if any.
     *
     * @return loser, or {@code null}
     */
    public @Nullable Player getLoser() {
        return loser;
    }

    /**
     * Returns the reason the duel ended.
     *
     * @return end reason
     */
    public DuelEndReason getReason() {
        return reason;
    }

    /**
     * Returns the handler list for this event.
     *
     * @return handler list
     */
    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Static accessor required by Bukkit's event dispatch system.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}