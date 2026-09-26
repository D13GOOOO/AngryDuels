package com.angryguyy.duels.gui;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.kit.Kit;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

/**
 * Builds and opens the kit editor GUI.
 *
 * <p>The editor is a 54-slot inventory laid out to mirror the natural
 * inventory of a player. Slots 0-35 mirror the main inventory rows and
 * the hotbar, slots 36-40 mirror the armor pieces and the offhand. The
 * remaining slots hold filler panes and the Save / Cancel buttons.</p>
 *
 * <p>The starting layout is the player's personal override, or the
 * default kit layout if none exists. The player can freely move items
 * between slots 0-40; nothing can be added from outside and nothing can
 * be removed to the bottom inventory.</p>
 */
public final class KitEditorGui {

    /**
     * Total number of slots of the inventory.
     */
    private static final int SIZE = 54;

    /**
     * Slot of the Save button.
     */
    private static final int SAVE_SLOT = 49;

    /**
     * Slot of the Cancel button.
     */
    private static final int CANCEL_SLOT = 50;

    /**
     * Highest slot that can host a kit item.
     */
    private static final int MAX_EDITABLE_SLOT = 40;

    /**
     * Prevents instantiation.
     */
    private KitEditorGui() {
    }

    /**
     * Opens the editor for the given player and kit.
     *
     * @param plugin owning plugin
     * @param viewer player editing the kit
     * @param kit    kit being edited
     */
    public static void open(DuelsPlugin plugin, Player viewer, Kit kit) {
        KitEditorHolder holder = new KitEditorHolder(viewer.getUniqueId(), kit.getId());

        Inventory inv = Bukkit.createInventory(
                holder, SIZE,
                MiniMessage.miniMessage().deserialize(
                        "<dark_gray>» <gold><bold>Edit kit</bold></gold> <gray>· <white>"
                                + kit.getDisplayName() + "</white> <dark_gray>«")
        );
        holder.setInventory(inv);

        Map<Integer, ItemStack> starting = plugin.playerKits()
                .getOverride(viewer.getUniqueId(), kit.getId());
        if (starting == null) {
            starting = kit.getItems();
        }

        for (int slot = 0; slot <= MAX_EDITABLE_SLOT; slot++) {
            ItemStack item = starting.get(slot);
            if (item != null) {
                inv.setItem(slot, item.clone());
            }
        }

        ItemStack filler = makeFiller();
        for (int slot = MAX_EDITABLE_SLOT + 1; slot < SIZE; slot++) {
            inv.setItem(slot, filler);
        }

        inv.setItem(SAVE_SLOT, makeButton(Material.LIME_CONCRETE,
                "<green><bold>Save"));
        inv.setItem(CANCEL_SLOT, makeButton(Material.RED_CONCRETE,
                "<red><bold>Cancel"));

        viewer.openInventory(inv);
    }

    /**
     * Returns the slot index of the Save button.
     *
     * @return save button slot
     */
    public static int saveSlot() {
        return SAVE_SLOT;
    }

    /**
     * Returns the slot index of the Cancel button.
     *
     * @return cancel button slot
     */
    public static int cancelSlot() {
        return CANCEL_SLOT;
    }

    /**
     * Returns the highest slot index that can host a kit item.
     *
     * @return highest editable slot
     */
    public static int maxEditableSlot() {
        return MAX_EDITABLE_SLOT;
    }

    /**
     * Builds the filler glass pane used for non-editable slots.
     *
     * @return a black glass pane with a blank name
     */
    private static ItemStack makeFiller() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(MiniMessage.miniMessage().deserialize(" "));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    /**
     * Builds a labelled button.
     *
     * @param material button material
     * @param label    MiniMessage label
     * @return the built button
     */
    private static ItemStack makeButton(Material material, String label) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(MiniMessage.miniMessage().deserialize(label));
            stack.setItemMeta(meta);
        }
        return stack;
    }
}