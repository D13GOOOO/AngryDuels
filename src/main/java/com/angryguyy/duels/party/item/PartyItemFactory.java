package com.angryguyy.duels.party.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Builds the four hotbar items given to the leader of a party.
 *
 * <p>Every item is marked with a persistent data container entry, so
 * the plugin can identify its own items without relying on display
 * names or lore. The materials are cosmetic: the interact listener
 * does not rely on the client sending a use packet, so any material
 * works, including passive ones.</p>
 */
public final class PartyItemFactory {

    /** Namespace key used to tag party items. */
    public static final NamespacedKey ITEM_KEY =
            new NamespacedKey("angryduels", "party_item");

    private PartyItemFactory() {
    }

    /**
     * Builds the item associated with a given type.
     *
     * @param type the item type
     * @return a freshly built item stack
     */
    public static ItemStack create(PartyItemType type) {
        return switch (type) {
            case FIGHT -> build(Material.NETHERITE_SWORD, type, "<red><bold>Party Fight",
                    "<gray>Right-click to start a party fight.",
                    "<gray>Choose between <white>FFA</white> and <white>Split</white>.");
            case INFO -> build(Material.BOOK, type, "<gold><bold>Party Info",
                    "<gray>Right-click to view your party.",
                    "<gray>Members, status and settings.");
            case INVITE -> build(Material.PAPER, type, "<aqua><bold>Invite",
                    "<gray>Right-click to invite a player.",
                    "<gray>Only the leader can send invitations.");
            case DISBAND -> build(Material.BARRIER, type, "<dark_red><bold>Disband",
                    "<gray>Right-click to disband the party.",
                    "<red>This action cannot be undone.");
        };
    }

    /**
     * Checks whether an item is one of the plugin's party items and
     * returns its type, or {@code null} if it is not a party item.
     *
     * @param item item to test
     * @return the type, or {@code null}
     */
    public static PartyItemType typeOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String raw = meta.getPersistentDataContainer()
                .get(ITEM_KEY, PersistentDataType.STRING);
        if (raw == null) return null;
        for (PartyItemType type : PartyItemType.values()) {
            if (type.getKey().equals(raw)) return type;
        }
        return null;
    }

    private static ItemStack build(Material material, PartyItemType type,
                                   String title, String... loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        MiniMessage mm = MiniMessage.miniMessage();

        meta.displayName(mm.deserialize(title)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new java.util.ArrayList<>();
        for (String line : loreLines) {
            lore.add(mm.deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);

        meta.getPersistentDataContainer().set(ITEM_KEY,
                PersistentDataType.STRING, type.getKey());

        stack.setItemMeta(meta);
        return stack;
    }
}