package com.angryguyy.duels.listener;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.gui.LeaderboardGuiHolder;
import com.angryguyy.duels.stats.LeaderboardCategory;
import com.angryguyy.duels.stats.LeaderboardManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Handles interactions with the leaderboard GUI.
 *
 * <p>All clicks and drags are cancelled. Navigation clicks on the
 * previous/next arrows change page and reopen the inventory; a click on
 * the compass cycles to the next category and resets the page to one.
 */
public class LeaderboardGuiListener implements Listener {

    private final DuelsPlugin plugin;

    /**
     * Creates a new listener.
     *
     * @param plugin owning plugin
     */
    public LeaderboardGuiListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles clicks inside the leaderboard GUI.
     *
     * @param event click event
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof LeaderboardGuiHolder gui)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        if (!viewer.getUniqueId().equals(gui.getViewer())) return;

        LeaderboardManager manager = plugin.leaderboards();
        int slot = event.getRawSlot();

        if (slot == 48) {
            int newPage = Math.max(1, gui.getPage() - 1);
            com.angryguyy.duels.gui.LeaderboardGui.open(plugin, viewer, gui.getCategory(), newPage);
        } else if (slot == 50) {
            int max = manager.totalPages(gui.getCategory());
            int newPage = Math.min(max, gui.getPage() + 1);
            com.angryguyy.duels.gui.LeaderboardGui.open(plugin, viewer, gui.getCategory(), newPage);
        } else if (slot == 49) {
            LeaderboardCategory[] all = LeaderboardCategory.values();
            int next = (gui.getCategory().ordinal() + 1) % all.length;
            com.angryguyy.duels.gui.LeaderboardGui.open(plugin, viewer, all[next], 1);
        }
    }

    /**
     * Prevents item dragging inside the leaderboard GUI.
     *
     * @param event drag event
     */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof LeaderboardGuiHolder) {
            event.setCancelled(true);
        }
    }
}