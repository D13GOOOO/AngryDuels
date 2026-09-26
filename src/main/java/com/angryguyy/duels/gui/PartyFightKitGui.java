package com.angryguyy.duels.gui;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.kit.Kit;
import com.angryguyy.duels.party.match.PartyMatchType;
import net.kyori.adventure.text.Component;
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
 * Kit selection GUI opened from the party fight setup.
 *
 * <p>Lists every kit the leader has permission to use. Clicking a kit
 * completes the setup and delegates to the {@code PartyMatchSetupManager}.
 * A dedicated "no kit" button is provided so the party can fight with
 * the players' own inventories.</p>
 */
public final class PartyFightKitGui {

    private static final int SIZE = 27;
    private static final int[] KIT_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int NO_KIT_SLOT = 22;

    private PartyFightKitGui() {
    }

    /**
     * Opens the kit selection GUI.
     *
     * @param plugin owning plugin
     * @param viewer the party leader
     * @param partyId id of the party
     * @param type   chosen fight mode
     */
    public static void open(DuelsPlugin plugin, Player viewer, long partyId, PartyMatchType type) {
        Map<Integer, String> slotToKit = new HashMap<>();
        List<Kit> kits = List.copyOf(plugin.kits().all());

        PartyFightKitGuiHolder holder = new PartyFightKitGuiHolder(
                viewer.getUniqueId(), partyId, type, slotToKit);

        Component title = MiniMessage.miniMessage().deserialize(
                "<dark_gray>» <gold><bold>Choose a kit</bold></gold> <gray>· "
                        + type.getLabel() + " <dark_gray>«");

        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);

        fillBorder(inv);

        int index = 0;
        for (Kit kit : kits) {
            if (index >= KIT_SLOTS.length) break;
            if (!viewer.hasPermission(kit.getPermission())) continue;
            int slot = KIT_SLOTS[index++];
            inv.setItem(slot, kit.buildGuiIcon(true));
            slotToKit.put(slot, kit.getId());
        }

        inv.setItem(NO_KIT_SLOT, buildNoKitButton());
        viewer.openInventory(inv);
    }

    /**
     * Returns the slot of the "no kit" button.
     *
     * @return no-kit slot
     */
    public static int noKitSlot() {
        return NO_KIT_SLOT;
    }

    private static ItemStack buildNoKitButton() {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            MiniMessage mm = MiniMessage.miniMessage();
            meta.displayName(mm.deserialize("<red><bold>No kit").decoration(
                    net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    mm.deserialize("<gray>Use players' own inventories.")
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                    Component.empty(),
                    mm.deserialize("<yellow>▶ Click to select")
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
            ));
            stack.setItemMeta(meta);
        }
        return stack;
    }

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