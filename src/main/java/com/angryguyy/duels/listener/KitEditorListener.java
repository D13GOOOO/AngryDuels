package com.angryguyy.duels.listener;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.gui.KitEditorGui;
import com.angryguyy.duels.gui.KitEditorHolder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles interactions with the kit editor GUI.
 *
 * <p>The listener is intentionally strict: only movement between slots
 * 0-40 of the editor is allowed. Every other interaction — clicks in the
 * player's own inventory, shift-clicks, number-key swaps, drags — is
 * cancelled, so the kit cannot gain or lose items.</p>
 *
 * <p>Save and Cancel buttons are handled here. Save reads the current
 * contents of slots 0-40, stores them in the {@code PlayerKitManager}
 * and closes the inventory. Cancel closes the inventory without writing
 * anything.</p>
 *
 * <p>If the player is holding an item on the cursor when clicking Save
 * or Cancel, the action is refused with a message, so the pending item
 * is never silently lost.</p>
 *
 * <p>The Save button refuses to persist when the database is not
 * available, so the player is not led to believe the layout was saved
 * when it would only survive until the next disconnect.</p>
 */
public class KitEditorListener implements Listener {

    private final DuelsPlugin plugin;

    /**
     * Creates a new listener.
     *
     * @param plugin owning plugin
     */
    public KitEditorListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles clicks inside the editor.
     *
     * @param event the click event
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof KitEditorHolder gui)) return;
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        if (!viewer.getUniqueId().equals(gui.getViewer())) return;

        int raw = event.getRawSlot();

        if (raw == KitEditorGui.saveSlot()) {
            event.setCancelled(true);
            if (cursorNotEmpty(viewer)) {
                plugin.messages().send(viewer, "kit.editor-cursor");
                return;
            }
            saveAndClose(gui, viewer);
            return;
        }
        if (raw == KitEditorGui.cancelSlot()) {
            event.setCancelled(true);
            if (cursorNotEmpty(viewer)) {
                plugin.messages().send(viewer, "kit.editor-cursor");
                return;
            }
            viewer.closeInventory();
            return;
        }

        if (event.isShiftClick() || event.getClick() == ClickType.NUMBER_KEY
                || event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }

        boolean inEditor = event.getClickedInventory().getHolder() instanceof KitEditorHolder;
        if (!inEditor) {
            event.setCancelled(true);
            return;
        }

        if (raw < 0 || raw > KitEditorGui.maxEditableSlot()) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevents item dragging inside the editor.
     *
     * @param event the drag event
     */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof KitEditorHolder)) return;
        event.setCancelled(true);
    }

    /**
     * Reads the current layout, stores it and closes the inventory.
     *
     * <p>If the database is not available, the save is refused and an
     * error message is shown instead, so the player knows the change
     * would not persist beyond the current session.</p>
     *
     * @param gui    the editor holder
     * @param viewer the editing player
     */
    private void saveAndClose(KitEditorHolder gui, Player viewer) {
        if (!plugin.database().isReady()) {
            plugin.messages().send(viewer, "kit.editor-save-error");
            return;
        }

        Map<Integer, ItemStack> layout = new HashMap<>();
        for (int slot = 0; slot <= KitEditorGui.maxEditableSlot(); slot++) {
            ItemStack item = gui.getInventory().getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                layout.put(slot, item.clone());
            }
        }

        plugin.playerKits().setOverride(viewer.getUniqueId(), gui.getKitId(), layout);
        viewer.closeInventory();
        plugin.messages().send(viewer, "kit.editor-saved",
                Map.of("kit", gui.getKitId()));
    }

    /**
     * Checks whether the player is holding an item on the cursor.
     *
     * @param viewer the player
     * @return {@code true} if the cursor holds a non-air item
     */
    private boolean cursorNotEmpty(Player viewer) {
        ItemStack cursor = viewer.getItemOnCursor();
        return cursor != null && cursor.getType() != Material.AIR;
    }
}