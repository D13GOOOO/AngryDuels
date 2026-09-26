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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Invite GUI opened by the Invite hotbar item.
 *
 * <p>Shows a paginated list of online players that can still be invited
 * to the party. Clicking a head sends an invitation using the standard
 * party invite flow, closing the GUI and reporting the result to the
 * leader.</p>
 *
 * <p>Layout: 6 rows. Rows 1-4 host up to 28 player heads in a grid
 * surrounded by a dark border. The bottom row hosts previous page,
 * close and next page controls.</p>
 */
public final class PartyInviteGui {

    private static final int SIZE = 54;

    /** Slots that can host a player head, in row-major order. */
    private static final int[] HEAD_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int PREV_SLOT = 45;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private PartyInviteGui() {
    }

    /**
     * Opens the invite GUI on the given page.
     *
     * @param plugin owning plugin
     * @param viewer the party leader
     * @param party  the party
     * @param page   zero-based page number
     */
    public static void open(DuelsPlugin plugin, Player viewer, Party party, int page) {
        List<Player> candidates = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getUniqueId().equals(viewer.getUniqueId())) continue;
            if (party.isMember(p.getUniqueId())) continue;
            if (plugin.parties().isInParty(p.getUniqueId())) continue;
            candidates.add(p);
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) candidates.size() / HEAD_SLOTS.length));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        Map<Integer, UUID> slotToPlayer = new HashMap<>();
        Map<Integer, Player> visible = new HashMap<>();
        int startIndex = safePage * HEAD_SLOTS.length;
        for (int i = 0; i < HEAD_SLOTS.length; i++) {
            int candidateIndex = startIndex + i;
            if (candidateIndex >= candidates.size()) break;
            Player candidate = candidates.get(candidateIndex);
            int slot = HEAD_SLOTS[i];
            slotToPlayer.put(slot, candidate.getUniqueId());
            visible.put(slot, candidate);
        }

        PartyInviteGuiHolder holder = new PartyInviteGuiHolder(
                viewer.getUniqueId(), party.getId(), safePage, slotToPlayer);

        Component title = MiniMessage.miniMessage().deserialize(
                "<dark_gray>» <aqua><bold>Invite a player</bold></aqua> "
                        + "<gray>· <white>" + (safePage + 1) + "/" + totalPages + "</white> <dark_gray>«");

        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);

        fillBorder(inv);

        for (Map.Entry<Integer, Player> entry : visible.entrySet()) {
            inv.setItem(entry.getKey(), buildPlayerHead(entry.getValue()));
        }

        if (candidates.isEmpty()) {
            inv.setItem(22, buildInfoItem(Material.BARRIER,
                    "<red><bold>No players available",
                    "<gray>Every online player is already",
                    "<gray>in a party or is already a member."));
        }

        if (safePage > 0) {
            inv.setItem(PREV_SLOT, buildButton(Material.ARROW,
                    "<yellow>◀ Previous page"));
        }
        inv.setItem(CLOSE_SLOT, buildButton(Material.BARRIER,
                "<red><bold>Close", "<gray>Return to the game."));
        if (safePage < totalPages - 1) {
            inv.setItem(NEXT_SLOT, buildButton(Material.ARROW,
                    "<yellow>Next page ▶"));
        }

        viewer.openInventory(inv);
    }

    /**
     * Returns the slot of the previous page button.
     *
     * @return prev slot
     */
    public static int prevSlot() {
        return PREV_SLOT;
    }

    /**
     * Returns the slot of the close button.
     *
     * @return close slot
     */
    public static int closeSlot() {
        return CLOSE_SLOT;
    }

    /**
     * Returns the slot of the next page button.
     *
     * @return next slot
     */
    public static int nextSlot() {
        return NEXT_SLOT;
    }

    private static ItemStack buildPlayerHead(Player target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(target);
        }
        if (meta != null) {
            MiniMessage mm = MiniMessage.miniMessage();
            meta.displayName(mm.deserialize("<white><bold>" + target.getName() + "</bold></white>")
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<gray>Click to send an invitation.")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private static ItemStack buildInfoItem(Material material, String title, String... loreLines) {
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

    private static ItemStack buildButton(Material material, String title, String... loreLines) {
        return buildInfoItem(material, title, loreLines);
    }

    private static void fillBorder(Inventory inv) {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        if (meta != null) {
            meta.displayName(MiniMessage.miniMessage().deserialize(" "));
            border.setItemMeta(meta);
        }
        for (int slot = 0; slot < SIZE; slot++) {
            if (slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8) {
                inv.setItem(slot, border);
            }
        }
        for (int slot = 17; slot < 19; slot++) inv.setItem(slot, border);
        for (int slot = 26; slot < 28; slot++) inv.setItem(slot, border);
        for (int slot = 35; slot < 37; slot++) inv.setItem(slot, border);
    }
}