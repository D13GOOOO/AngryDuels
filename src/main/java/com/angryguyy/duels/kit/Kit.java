package com.angryguyy.duels.kit;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable definition of a kit.
 *
 * <p>A kit is a set of items keyed by their exact slot in the player
 * inventory. Slot numbers follow the standard Bukkit layout:</p>
 * <ul>
 *     <li>{@code 0–8} — hotbar</li>
 *     <li>{@code 9–35} — main inventory</li>
 *     <li>{@code 36} — boots, {@code 37} leggings, {@code 38}
 *     chestplate, {@code 39} helmet</li>
 *     <li>{@code 40} — offhand</li>
 * </ul>
 *
 * <p>Preserving the slot of every item is a core design requirement:
 * the future in-game kit editor will let players move items between
 * slots, and the order in which items are shown and applied must match
 * the order stored in the config file exactly. For this reason the
 * backing map is a {@link java.util.TreeMap} so iteration is always
 * ordered by ascending slot index.</p>
 *
 * <p>Each kit also carries a display icon used by the selection GUI.
 * The icon is a standalone {@link ItemStack} built from the
 * {@code icon} block in {@code kits.yml} and is unrelated to the actual
 * kit contents; it is only used for visual representation.</p>
 *
 * <p>Instances are effectively immutable after construction; the
 * underlying item stacks are cloned on both retrieval and application
 * so that a kit can be applied to any number of players without
 * sharing mutable state.</p>
 */
public class Kit {

    private final String id;
    private final String displayName;
    private final String permission;
    private final List<String> aliases;
    private final Map<Integer, ItemStack> items;
    private final ItemStack icon;

    /**
     * Creates a new kit definition.
     *
     * @param id          unique identifier of the kit
     * @param displayName human-readable name used in messages
     * @param permission  permission node required to use the kit
     * @param aliases     alternative names accepted by the command
     * @param items       map of slot to item stack
     * @param icon        display icon used by the selection GUI
     */
    public Kit(String id, String displayName, String permission,
               List<String> aliases, Map<Integer, ItemStack> items, ItemStack icon) {
        this.id = id;
        this.displayName = displayName;
        this.permission = permission;
        this.aliases = List.copyOf(aliases);
        this.items = Collections.unmodifiableMap(items);
        this.icon = icon;
    }

    /**
     * Returns the unique id of the kit.
     *
     * @return kit id
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the human-readable display name.
     *
     * @return display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the permission node required to use the kit.
     *
     * @return permission node
     */
    public String getPermission() {
        return permission;
    }

    /**
     * Returns the aliases accepted by the command.
     *
     * @return list of aliases, possibly empty
     */
    public List<String> getAliases() {
        return aliases;
    }

    /**
     * Returns the item map, keyed by exact inventory slot.
     *
     * @return an unmodifiable, slot-ordered map
     */
    public Map<Integer, ItemStack> getItems() {
        return items;
    }

    /**
     * Returns the item assigned to a specific slot.
     *
     * @param slot slot index
     * @return a clone of the item, or {@code null} if the slot is empty
     */
    public ItemStack getItem(int slot) {
        ItemStack item = items.get(slot);
        return item == null ? null : item.clone();
    }

    /**
     * Returns the display icon used by the selection GUI.
     *
     * @return a clone of the icon item
     */
    public ItemStack getIcon() {
        return icon.clone();
    }

    /**
     * Builds a GUI-ready icon: the base icon with the kit display name
     * as title and a short action hint in the lore.
     *
     * <p>Item attribute tooltips (armor, attack damage, and similar
     * statistics added automatically by the client) are hidden so that
     * the icon only shows the kit name and the hint.</p>
     *
     * @param permitted whether the viewer has permission to use the kit
     * @return the decorated icon
     */
    public ItemStack buildGuiIcon(boolean permitted) {
        ItemStack stack = icon.clone();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        MiniMessage mm = MiniMessage.miniMessage();
        meta.displayName(mm.deserialize("<gold><bold>" + displayName + "</bold></gold>"));
        meta.lore(List.of(
                Component.empty(),
                mm.deserialize(permitted
                        ? "<green>▶ Click to select"
                        : "<red>✖ You don't have permission")
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Applies the kit to a player.
     *
     * <p>The player's inventory is cleared first, including armor and
     * offhand, so that no leftover items from a previous state leak
     * into the duel. Each item is then placed at its exact slot using
     * the Bukkit slot convention, which lets a single call handle the
     * hotbar, main inventory, armor, and offhand uniformly.</p>
     *
     * @param player player receiving the kit
     */
    public void apply(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            inv.setItem(entry.getKey(), entry.getValue().clone());
        }
        player.updateInventory();
    }
}