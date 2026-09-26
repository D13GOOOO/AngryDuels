package com.angryguyy.duels.listener;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.event.DuelEndEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Forwards duel end events to the reward manager.
 *
 * <p>The reward manager distributes rewards to every winning player
 * individually, so team matches grant the same rewards to each member
 * of the winning team.</p>
 */
public class DuelRewardListener implements Listener {

    /**
     * Owning plugin instance.
     */
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
        for (Player winner : event.getWinners()) {
            plugin.rewards().grant(event, winner);
        }
    }
}