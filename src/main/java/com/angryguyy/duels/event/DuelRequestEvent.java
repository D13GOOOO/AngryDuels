package com.angryguyy.duels.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired before a duel request is registered.
 *
 * <p>This event is cancellable: a listener that cancels it prevents
 * the request from being stored and no cooldown is applied to the
 * sender. It is typically used to enforce custom restrictions on who
 * may challenge whom, such as rank checks, region checks or
 * anti-abuse policies.</p>
 *
 * <p>The event carries both players and the optional kit identifier,
 * so a listener may also inspect the intended kit before allowing the
 * request through.</p>
 */
public class DuelRequestEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player sender;
    private final Player target;
    private final @Nullable String kitId;
    private boolean cancelled;

    /**
     * Creates a new duel request event.
     *
     * @param sender the player sending the request
     * @param target the player receiving the request
     * @param kitId  optional kit identifier, or {@code null}
     */
    public DuelRequestEvent(Player sender, Player target, @Nullable String kitId) {
        this.sender = sender;
        this.target = target;
        this.kitId = kitId;
    }

    /**
     * Returns the player sending the request.
     *
     * @return sender
     */
    public Player getSender() {
        return sender;
    }

    /**
     * Returns the player receiving the request.
     *
     * @return target
     */
    public Player getTarget() {
        return target;
    }

    /**
     * Returns the optional kit identifier requested for the duel.
     *
     * @return kit id, or {@code null}
     */
    public @Nullable String getKitId() {
        return kitId;
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
     * @param cancel {@code true} to cancel the request
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