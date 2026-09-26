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

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Party info GUI opened by the Info hotbar item.
 *
 * <p>Layout: 6 rows. The first and last row are a decorative border,
 * the middle three rows hold up to 21 member heads, and the bottom row
 * hosts the public toggle, a summary item and a close button. The
 * public toggle is only rendered as clickable for the party leader;
 * members see a read-only version.</p>
 *
 * <p>The slot-to-member map is created here and passed to the holder by
 * reference, because it is populated after the holder is created. See
 * {@link PartyFightKitGui} for the same pattern.</p>
 */
public final class PartyInfoGui {

    /**
     * Total number of slots of the inventory.
     */
    private static final int SIZE = 54;

    /**
     * Slots that can host a member head, in row-major order.
     */
    private static final int[] MEMBER_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    /**
     * Slot of the public toggle button.
     */
    private static final int PUBLIC_TOGGLE_SLOT = 45;

    /**
     * Slot of the info summary item.
     */
    private static final int INFO_SLOT = 49;

    /**
     * Slot of the close button.
     */
    private static final int CLOSE_SLOT = 53;

    /**
     * Formatter for the party creation date shown in the info item.
     */
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    /**
     * Prevents instantiation.
     */
    private PartyInfoGui() {
    }

    /**
     * Opens the info GUI for the given viewer.
     *
     * @param plugin owning plugin
     * @param viewer the player opening the GUI
     * @param party  the party
     */
    public static void open(DuelsPlugin plugin, Player viewer, Party party) {
        Map<Integer, UUID> slotToMember = new HashMap<>();
        boolean isLeader = party.isLeader(viewer.getUniqueId());

        PartyInfoGuiHolder holder = new PartyInfoGuiHolder(
                viewer.getUniqueId(), party.getId(), slotToMember);

        Component title = MiniMessage.miniMessage().deserialize(
                "<dark_gray>» <gold><bold>Party Info</bold></gold> "
                        + "<gray>· <white>#" + party.getId() + "</white> <dark_gray>«");

        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);

        fillBorder(inv);

        int index = 0;
        for (UUID member : party.getMembers().keySet()) {
            if (index >= MEMBER_SLOTS.length) break;
            int slot = MEMBER_SLOTS[index++];
            inv.setItem(slot, buildMemberHead(party, member));

            if (isLeader && !member.equals(viewer.getUniqueId())) {
                slotToMember.put(slot, member);
            }
        }

        inv.setItem(PUBLIC_TOGGLE_SLOT, isLeader
                ? buildPublicToggle(party.isPublic())
                : buildPublicDisplay(party.isPublic()));

        inv.setItem(INFO_SLOT, buildInfoItem(party));
        inv.setItem(CLOSE_SLOT, buildButton(Material.BARRIER,
                "<red><bold>Close", "<gray>Close this window."));

        viewer.openInventory(inv);
    }

    /**
     * Returns the slot of the public toggle.
     *
     * @return public toggle slot
     */
    public static int publicToggleSlot() {
        return PUBLIC_TOGGLE_SLOT;
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
     * Builds the head item of a party member.
     *
     * @param party  the party, used to determine the role
     * @param member uuid of the member
     * @return the built head
     */
    private static ItemStack buildMemberHead(Party party, UUID member) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(member));
        }
        if (meta != null) {
            MiniMessage mm = MiniMessage.miniMessage();
            String name = Bukkit.getOfflinePlayer(member).getName();
            if (name == null) name = member.toString().substring(0, 8);

            boolean leader = party.isLeader(member);
            boolean online = Bukkit.getPlayer(member) != null;

            Component displayName = mm.deserialize(
                            (leader ? "<gold>★ </gold>" : "") + "<white>" + name + "</white>")
                    .decoration(TextDecoration.ITALIC, false);
            meta.displayName(displayName);

            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize(leader
                            ? "<gray>Role: <gold>Leader"
                            : "<gray>Role: <white>Member")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(mm.deserialize(online
                            ? "<gray>Status: <green>Online"
                            : "<gray>Status: <red>Offline")
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * Builds the clickable public toggle button.
     *
     * @param isPublic current public state
     * @return the built toggle
     */
    private static ItemStack buildPublicToggle(boolean isPublic) {
        Material material = isPublic ? Material.ENDER_EYE : Material.ENDER_PEARL;
        String label = isPublic
                ? "<green><bold>Party: Public"
                : "<red><bold>Party: Private";
        String hint = isPublic
                ? "<gray>Anyone can join with <white>/party join"
                : "<gray>Only invited players can join";
        return buildButton(material, label, hint, "", "<yellow>▶ Click to toggle");
    }

    /**
     * Builds the read-only public state display.
     *
     * @param isPublic current public state
     * @return the built display
     */
    private static ItemStack buildPublicDisplay(boolean isPublic) {
        Material material = isPublic ? Material.ENDER_EYE : Material.ENDER_PEARL;
        String label = isPublic
                ? "<green><bold>Party: Public"
                : "<red><bold>Party: Private";
        String hint = isPublic
                ? "<gray>Anyone can join with <white>/party join"
                : "<gray>Only invited players can join";
        return buildButton(material, label, hint);
    }

    /**
     * Builds the summary info item.
     *
     * @param party the party
     * @return the built info item
     */
    private static ItemStack buildInfoItem(Party party) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            MiniMessage mm = MiniMessage.miniMessage();
            meta.displayName(mm.deserialize("<gold><bold>Party summary")
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<gray>Id: <white>#" + party.getId() + "</white>")
                    .decoration(TextDecoration.ITALIC, false));

            String leaderName = Bukkit.getOfflinePlayer(party.getLeader()).getName();
            if (leaderName == null) leaderName = party.getLeader().toString().substring(0, 8);
            lore.add(mm.deserialize("<gray>Leader: <white>" + leaderName + "</white>")
                    .decoration(TextDecoration.ITALIC, false));

            lore.add(mm.deserialize("<gray>Members: <white>" + party.size()
                            + "</white>/<white>" + Party.MAX_MEMBERS + "</white>")
                    .decoration(TextDecoration.ITALIC, false));

            lore.add(mm.deserialize("<gray>Created: <white>"
                            + DATE_FORMAT.format(party.getCreatedAt()) + "</white>")
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
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
     * Fills the border slots of the inventory with black glass panes.
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
            if (slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8) {
                inv.setItem(slot, border);
            }
        }
        for (int slot = 36; slot < 45; slot++) {
            inv.setItem(slot, border);
        }
    }
}