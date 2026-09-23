package com.angryguyy.duels.listener;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.event.DuelEndEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Forwards duel end events to the reward manager.
 *
 * <p>Keeping the forwarding logic in a dedicated listener means the
 * {@code DuelManager} does not need to know about rewards, and rewards
 * can be enabled, disabled or reloaded independently of the duel flow.
 * The listener itself is a no-op if rewards are disabled by config.</p>
 */
public class DuelRewardListener implements Listener {

    private final DuelsPlugin plugin;

    /**
     * Creates a new listener.
     *
     * @param plugin owning plugin
     */
    public DuelRewardListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Grants rewards when a duel ends.
     *
     * @param event the duel end event
     */
    @EventHandler
    public void onDuelEnd(DuelEndEvent event) {
        plugin.rewards().grant(event);
    }
}