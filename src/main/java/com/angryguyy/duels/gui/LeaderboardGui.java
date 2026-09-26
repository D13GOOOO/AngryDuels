package com.angryguyy.duels.gui;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.stats.LeaderboardCategory;
import com.angryguyy.duels.stats.LeaderboardEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and opens the leaderboard GUI.
 *
 * <p>Layout: 6 rows of 9 slots. Row 5 (slots 45-53) hosts navigation
 * controls for switching category and page. Rows 0-4 hold up to 45
 * entries as player heads, each showing rank, name and the value for
 * the current category.</p>
 *
 * <p>The page number is clamped to the valid range before the GUI is
 * built, so an out-of-range page always results in a valid, non-empty
 * view. Navigation arrows always reopen the inventory with the
 * requested page, delegating the clamping logic to this class.</p>
 */
public final class LeaderboardGui {

    /**
     * Total number of slots of the inventory.
     */
    private static final int SIZE = 54;

    /**
     * Maximum number of entries displayed before the navigation row.
     */
    private static final int MAX_ENTRIES = 45;

    /**
     * Prevents instantiation.
     */
    private LeaderboardGui() {
    }

    /**
     * Opens the GUI for the given viewer.
     *
     * @param plugin   owning plugin
     * @param viewer   player viewing the GUI
     * @param category category to display
     * @param page     one-based page number, clamped to the valid range
     */
    public static void open(DuelsPlugin plugin, Player viewer,
                            LeaderboardCategory category, int page) {
        int totalPages = plugin.leaderboards().totalPages(category);
        int safePage = Math.max(1, Math.min(page, totalPages));

        LeaderboardGuiHolder holder = new LeaderboardGuiHolder(
                viewer.getUniqueId(), category, safePage);

        Component title = MiniMessage.miniMessage().deserialize(
                "<dark_gray>» <gold><bold>" + category.getLabel()
                        + "</bold></gold> <gray>· page " + safePage + " <dark_gray>«");

        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);

        List<LeaderboardEntry> entries = plugin.leaderboards().getPage(category, safePage);
        for (int i = 0; i < entries.size() && i < MAX_ENTRIES; i++) {
            inv.setItem(i, buildEntryItem(entries.get(i)));
        }

        for (int slot = MAX_ENTRIES; slot < SIZE; slot++) {
            inv.setItem(slot, filler());
        }

        addNavigation(inv, category, safePage, totalPages);
        viewer.openInventory(inv);
    }

    /**
     * Builds the player head representing a single leaderboard entry.
     *
     * @param entry entry to represent
     * @return decorated item
     */
    private static ItemStack buildEntryItem(LeaderboardEntry entry) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(entry.uuid()));
        }
        if (meta != null) {
            MiniMessage mm = MiniMessage.miniMessage();
            meta.displayName(mm.deserialize("<yellow><bold>#" + entry.rank()
                    + "</bold></yellow> <white>" + entry.username() + "</white>"));
            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<gray>Value: <gold>" + entry.value() + "</gold>"));
            meta.lore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * Adds the navigation row: previous page, category switch, next page.
     *
     * @param inv        inventory to modify
     * @param category   current category, highlighted in the compass lore
     * @param page       current page, one-based
     * @param totalPages total number of available pages
     */
    private static void addNavigation(Inventory inv,
                                      LeaderboardCategory category,
                                      int page, int totalPages) {
        MiniMessage mm = MiniMessage.miniMessage();

        ItemStack prev = new ItemStack(Material.ARROW);
        ItemMeta prevMeta = prev.getItemMeta();
        if (prevMeta != null) {
            prevMeta.displayName(mm.deserialize("<yellow>◀ Previous page"));
            prev.setItemMeta(prevMeta);
        }
        inv.setItem(48, prev);

        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = next.getItemMeta();
        if (nextMeta != null) {
            nextMeta.displayName(mm.deserialize("<yellow>Next page ▶"));
            next.setItemMeta(nextMeta);
        }
        inv.setItem(50, next);

        ItemStack switcher = new ItemStack(Material.COMPASS);
        ItemMeta swMeta = switcher.getItemMeta();
        if (swMeta != null) {
            swMeta.displayName(mm.deserialize("<gold><bold>Change category"));
            List<Component> lore = new ArrayList<>();
            for (LeaderboardCategory c : LeaderboardCategory.values()) {
                String prefix = c == category ? "<green>▶ " : "<gray>  ";
                lore.add(mm.deserialize(prefix + c.getLabel()));
            }
            lore.add(Component.empty());
            lore.add(mm.deserialize("<gray>Page <white>" + page + "</white>/<white>" + totalPages + "</white>"));
            swMeta.lore(lore);
            switcher.setItemMeta(swMeta);
        }
        inv.setItem(49, switcher);
    }

    /**
     * Returns a filler glass pane used for empty slots.
     *
     * @return filler item
     */
    private static ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(MiniMessage.miniMessage().deserialize(" "));
            pane.setItemMeta(meta);
        }
        return pane;
    }
}