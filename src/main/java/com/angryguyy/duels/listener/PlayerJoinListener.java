package com.angryguyy.duels.listener;

import com.angryguyy.duels.DuelsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Recovers pending snapshots when a player reconnects.
 *
 * <p>If the server was shut down or crashed while the player was inside
 * a duel, a snapshot for that player is still persisted on disk. This
 * listener detects the situation on join and applies the snapshot a few
 * ticks later, once the player has fully spawned.</p>
 */
public class PlayerJoinListener implements Listener {

    private final DuelsPlugin plugin;

    /**
     * Creates a new join listener.
     *
     * @param plugin owning plugin
     */
    public PlayerJoinListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Applies a pending snapshot to the joining player, if any.
     *
     * @param event the join event
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.snapshots().hasPending(event.getPlayer().getUniqueId())) return;
        plugin.snapshots().scheduleRestore(event.getPlayer(), 10L);
    }
}