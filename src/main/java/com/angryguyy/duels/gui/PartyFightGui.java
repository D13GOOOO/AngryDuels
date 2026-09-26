package com.angryguyy.duels.gui;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.party.Party;
import com.angryguyy.duels.party.match.PartyMatchType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Mode selection GUI opened by the Party Fight item.
 *
 * <p>Presents two buttons, {@code FFA} and {@code Split}, in a 27-slot
 * inventory with a dark border. Clicking one of them opens the kit
 * selection GUI for that mode.</p>
 */
public final class PartyFightGui {

    private static final int SIZE = 27;
    private static final int FFA_SLOT = 11;
    private static final int SPLIT_SLOT = 15;

    private PartyFightGui() {
    }

    /**
     * Opens the mode selection GUI.
     *
     * @param plugin owning plugin
     * @param viewer the party leader
     * @param party  the party
     */
    public static void open(DuelsPlugin plugin, Player viewer, Party party) {
        PartyFightGuiHolder holder = new PartyFightGuiHolder(viewer.getUniqueId(), party.getId());

        Component title = MiniMessage.miniMessage().deserialize(
                "<dark_gray>» <red><bold>Party Fight</bold></red> <dark_gray>«");

        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);

        fillBorder(inv);
        inv.setItem(FFA_SLOT, buildButton(Material.DIAMOND_SWORD,
                "<red><bold>Free For All",
                "<gray>Every member fights for themselves.",
                "<gray>Last one standing wins.",
                "",
                "<yellow>▶ Click to select"));
        inv.setItem(SPLIT_SLOT, buildButton(Material.SHIELD,
                "<gold><bold>Split",
                "<gray>The party is split into two balanced teams.",
                "<gray>Team versus team.",
                "",
                "<yellow>▶ Click to select"));

        viewer.openInventory(inv);
    }

    /**
     * Returns the slot of the FFA button.
     *
     * @return FFA slot
     */
    public static int ffaSlot() {
        return FFA_SLOT;
    }

    /**
     * Returns the slot of the Split button.
     *
     * @return Split slot
     */
    public static int splitSlot() {
        return SPLIT_SLOT;
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

    private static ItemStack buildButton(Material material, String title, String... loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        MiniMessage mm = MiniMessage.miniMessage();
        meta.displayName(mm.deserialize(title).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new java.util.ArrayList<>();
        for (String line : loreLines) {
            lore.add(mm.deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}