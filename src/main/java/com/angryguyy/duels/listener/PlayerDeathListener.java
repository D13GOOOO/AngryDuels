package com.angryguyy.duels.listener;

import com.angryguyy.duels.DuelsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Forwards player deaths to the duel manager.
 *
 * <p>The manager decides whether the death ends the match or simply
 * removes the player from their team, and performs the corresponding
 * cleanup. Drops and dropped experience are cleared before delegating,
 * so a duelist never loses items to a vanilla death drop during a
 * match.</p>
 */
public class PlayerDeathListener implements Listener {

    /**
     * Owning plugin instance.
     */
    private final DuelsPlugin plugin;

    /**
     * Creates a new death listener.
     *
     * @param plugin owning plugin
     */
    public PlayerDeathListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles a player death.
     *
     * @param event the death event
     */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player deceased = event.getEntity();
        if (!plugin.duels().isInDuel(deceased.getUniqueId())) return;

        event.getDrops().clear();
        event.setDroppedExp(0);

        plugin.duels().handleDeath(deceased);
    }
}