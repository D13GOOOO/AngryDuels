package com.angryguyy.duels.listener;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.gui.KitGuiHolder;
import com.angryguyy.duels.kit.Kit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Handles interactions with the kit selection GUI.
 *
 * <p>All clicks and drags inside the GUI are cancelled so that items
 * cannot be moved. A click on a slot mapped to a kit resolves the
 * target player, closes the inventory, and delegates to the duel
 * manager to send the request with the chosen kit.</p>
 *
 * <p>If the target went offline between opening the GUI and the click,
 * the request is silently dropped and the appropriate error message is
 * displayed via the normal duel flow.</p>
 */
public class KitGuiListener implements Listener {

    /**
     * Owning plugin instance.
     */
    private final DuelsPlugin plugin;

    /**
     * Creates a new listener.
     *
     * @param plugin owning plugin
     */
    public KitGuiListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles clicks inside the kit selection GUI.
     *
     * @param event the click event
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof KitGuiHolder gui)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        if (!viewer.isOnline()) return;
        if (!viewer.getUniqueId().equals(gui.getViewer())) return;

        Kit kit = gui.getSlotToKit().get(event.getRawSlot());
        if (kit == null) return;

        Player target = Bukkit.getPlayer(gui.getTarget());
        viewer.closeInventory();
        if (target == null || !target.isOnline()) {
            plugin.messages().send(viewer, "duel.target-offline");
            return;
        }

        plugin.duels().sendRequest(viewer, target, kit.getId());
    }

    /**
     * Prevents item dragging inside the kit selection GUI.
     *
     * @param event the drag event
     */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof KitGuiHolder) {
            event.setCancelled(true);
        }
    }
}