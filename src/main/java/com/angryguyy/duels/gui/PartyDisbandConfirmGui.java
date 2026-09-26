package com.angryguyy.duels.gui;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.party.Party;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Confirmation GUI opened by the Disband hotbar item.
 *
 * <p>Disbanding a party is irreversible, so the action requires an
 * explicit confirmation. The GUI is a 27-slot inventory with a dark
 * border, a confirm button and a cancel button.</p>
 */
public final class PartyDisbandConfirmGui {

    /**
     * Total number of slots of the inventory.
     */
    private static final int SIZE = 27;

    /**
     * Slot of the confirm button.
     */
    private static final int CONFIRM_SLOT = 11;

    /**
     * Slot of the cancel button.
     */
    private static final int CANCEL_SLOT = 15;

    /**
     * Prevents instantiation.
     */
    private PartyDisbandConfirmGui() {
    }

    /**
     * Opens the confirmation GUI.
     *
     * @param plugin owning plugin
     * @param viewer the leader
     * @param party  the party
     */
    public static void open(DuelsPlugin plugin, Player viewer, Party party) {
        PartyDisbandConfirmGuiHolder holder = new PartyDisbandConfirmGuiHolder(
                viewer.getUniqueId(), party.getId());

        Component title = MiniMessage.miniMessage().deserialize(
                "<dark_gray>» <dark_red><bold>Disband party?</bold></dark_red> <dark_gray>«");

        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);

        fillBorder(inv);

        inv.setItem(CONFIRM_SLOT, buildButton(Material.RED_CONCRETE,
                "<red><bold>Confirm disband",
                "<gray>This will permanently delete the party,",
                "<gray>remove every member and revoke all item",
                "<gray>assignments.",
                "",
                "<dark_red>▶ This action cannot be undone"));
        inv.setItem(CANCEL_SLOT, buildButton(Material.LIME_CONCRETE,
                "<green><bold>Cancel",
                "<gray>Keep the party as it is."));

        viewer.openInventory(inv);
    }

    /**
     * Returns the slot of the confirm button.
     *
     * @return confirm slot
     */
    public static int confirmSlot() {
        return CONFIRM_SLOT;
    }

    /**
     * Returns the slot of the cancel button.
     *
     * @return cancel slot
     */
    public static int cancelSlot() {
        return CANCEL_SLOT;
    }

    /**
     * Builds a labelled button with the given lore lines.
     *
     * @param material  button material
     * @param title     MiniMessage title
     * @param loreLines MiniMessage lore lines
     * @return the built button
     */
    private static ItemStack buildButton(Material material, String title, String... loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        MiniMessage mm = MiniMessage.miniMessage();
        meta.displayName(mm.deserialize(title).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(mm.deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Fills the outer ring of the inventory with black glass panes.
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