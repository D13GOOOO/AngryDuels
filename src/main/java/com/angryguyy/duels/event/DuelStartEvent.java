package com.angryguyy.duels.event;

import com.angryguyy.duels.duel.DuelSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a duel is about to start.
 *
 * <p>This event is triggered after a request has been accepted, an
 * arena has been assigned, and the return locations have been
 * captured, but <b>before</b> the players are teleported and the
 * countdown begins. It is cancellable: if any listener cancels the
 * event, the arena is released, the session is not registered, and
 * both players receive a cancellation message.</p>
 *
 * <p>The event exposes the fully prepared {@link DuelSession} so that
 * listeners can inspect the assigned arena, the intended kit, or the
 * captured return locations before the fight is committed.</p>
 */
public class DuelStartEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final DuelSession session;
    private final Player playerA;
    private final Player playerB;
    private boolean cancelled;

    /**
     * Creates a new duel start event.
     *
     * @param session the prepared session
     * @param playerA the first duelist
     * @param playerB the second duelist
     */
    public DuelStartEvent(DuelSession session, Player playerA, Player playerB) {
        this.session = session;
        this.playerA = playerA;
        this.playerB = playerB;
    }

    /**
     * Returns the session about to start.
     *
     * @return the prepared session
     */
    public DuelSession getSession() {
        return session;
    }

    /**
     * Returns the first duelist.
     *
     * @return player A
     */
    public Player getPlayerA() {
        return playerA;
    }

    /**
     * Returns the second duelist.
     *
     * @return player B
     */
    public Player getPlayerB() {
        return playerB;
    }

    /**
     * Checks whether the event has been cancelled.
     *
     * @return {@code true} if a listener cancelled the event
     */
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Sets the cancellation state of the event.
     *
     * @param cancel {@code true} to abort the duel
     */
    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
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