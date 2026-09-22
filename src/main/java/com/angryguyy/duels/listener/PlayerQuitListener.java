package com.angryguyy.duels.listener;

import com.angryguyy.duels.DuelsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Forwards player disconnections to the duel manager.
 *
 * <p>Disconnects can happen in three distinct situations that the
 * manager must resolve: while the player has an outgoing request,
 * while they have an incoming request, or while they are inside an
 * active duel. Delegating the whole handling to
 * {@code DuelManager.handleQuit(Player)} keeps the listener trivial
 * and concentrates all disconnect-related logic in one place.</p>
 */
public class PlayerQuitListener implements Listener {

    private final DuelsPlugin plugin;

    /**
     * Creates a new quit listener.
     *
     * @param plugin owning plugin
     */
    public PlayerQuitListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles a player quitting the server.
     *
     * @param event the quit event
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.duels().handleQuit(event.getPlayer());
    }
}