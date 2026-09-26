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
import java.util.UUID;

/**
 * Actions submenu opened when the leader clicks on a member head in the
 * Party Info GUI.
 *
 * <p>Presents three buttons: Promote to leader, Kick from party, and
 * Cancel. Each of the two actions closes the submenu and returns to the
 * info GUI after performing the operation.</p>
 */
public final class PartyMemberActionsGui {

    /**
     * Total number of slots of the inventory.
     */
    private static final int SIZE = 27;

    /**
     * Slot of the promote button.
     */
    private static final int PROMOTE_SLOT = 11;

    /**
     * Slot of the kick button.
     */
    private static final int KICK_SLOT = 15;

    /**
     * Slot of the cancel button.
     */
    private static final int CANCEL_SLOT = 22;

    /**
     * Prevents instantiation.
     */
    private PartyMemberActionsGui() {
    }

    /**
     * Opens the actions submenu.
     *
     * @param plugin owning plugin
     * @param viewer the leader
     * @param party  the party
     * @param target uuid of the member the actions apply to
     */
    public static void open(DuelsPlugin plugin, Player viewer, Party party, UUID target) {
        PartyMemberActionsGuiHolder holder = new PartyMemberActionsGuiHolder(
                viewer.getUniqueId(), party.getId(), target);

        String targetName = Bukkit.getOfflinePlayer(target).getName();
        if (targetName == null) targetName = target.toString().substring(0, 8);

        Component title = MiniMessage.miniMessage().deserialize(
                "<dark_gray>» <gold><bold>Actions</bold></gold> "
                        + "<gray>· <white>" + targetName + "</white> <dark_gray>«");

        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);

        fillBorder(inv);

        inv.setItem(PROMOTE_SLOT, buildButton(Material.LIME_CONCRETE,
                "<green><bold>Promote to leader",
                "<gray>Transfer leadership to <white>" + targetName + "</white>.",
                "",
                "<yellow>▶ Click to confirm"));
        inv.setItem(KICK_SLOT, buildButton(Material.RED_CONCRETE,
                "<red><bold>Kick from party",
                "<gray>Remove <white>" + targetName + "</white> from the party.",
                "",
                "<yellow>▶ Click to confirm"));
        inv.setItem(CANCEL_SLOT, buildButton(Material.BARRIER,
                "<gray><bold>Cancel",
                "<gray>Return to the party info."));

        viewer.openInventory(inv);
    }

    /**
     * Returns the slot of the promote button.
     *
     * @return promote slot
     */
    public static int promoteSlot() {
        return PROMOTE_SLOT;
    }

    /**
     * Returns the slot of the kick button.
     *
     * @return kick slot
     */
    public static int kickSlot() {
        return KICK_SLOT;
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