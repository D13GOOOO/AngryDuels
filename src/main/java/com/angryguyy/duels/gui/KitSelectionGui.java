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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and opens the kit selection GUI for a pending duel request.
 *
 * <p>The GUI is a 27-slot inventory (3 rows) with a dark border on the
 * top and bottom rows and on the two side columns of the middle row.
 * Kits are placed in slots 10 through 16 of the middle row, up to a
 * maximum of seven kits.</p>
 *
 * <p>Each kit is rendered using its {@code icon} block from
 * {@code kits.yml}, decorated with the kit display name and a short
 * action hint. Kits the viewer cannot use are still shown but cannot
 * be clicked.</p>
 */
public final class KitSelectionGui {

    private static final int SIZE = 27;
    private static final int[] KIT_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private KitSelectionGui() {
    }

    /**
     * Opens the GUI for the given viewer.
     *
     * @param plugin owning plugin
     * @param viewer player viewing the GUI
     * @param target player the duel request is addressed to
     */
    public static void open(DuelsPlugin plugin, Player viewer, Player target) {
        List<Kit> kits = List.copyOf(plugin.kits().all());
        Map<Integer, Kit> slotToKit = new HashMap<>();

        KitGuiHolder holder = new KitGuiHolder(
                viewer.getUniqueId(),
                target.getUniqueId(),
                slotToKit
        );

        Inventory inv = Bukkit.createInventory(
                holder, SIZE,
                MiniMessage.miniMessage().deserialize("<dark_gray>» <gold><bold>Select a kit</bold></gold> <dark_gray>«")
        );
        holder.setInventory(inv);

        fillBorder(inv);

        int index = 0;
        for (Kit kit : kits) {
            if (index >= KIT_SLOTS.length) break;
            int slot = KIT_SLOTS[index++];
            boolean permitted = viewer.hasPermission(kit.getPermission());
            inv.setItem(slot, kit.buildGuiIcon(permitted));
            if (permitted) {
                slotToKit.put(slot, kit);
            }
        }

        viewer.openInventory(inv);
    }

    /**
     * Fills the outer ring of the inventory with black glass panes to
     * frame the kit row.
     *
     * @param inv inventory to decorate
     */
    private static void fillBorder(Inventory inv) {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        if (meta != null) {
            meta.displayName(MiniMessage.miniMessage().deserialize(" "));
            border.setItemMeta(meta);
        }
        for (int slot = 0; slot < SIZE; slot++) {
            if (slot < 9 || slot >= 18 || slot % 9 == 0 || slot % 9 == 8) {
                inv.setItem(slot, border);
            }
        }
    }
}