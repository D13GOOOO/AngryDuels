package com.angryguyy.duels.gui;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.kit.Kit;
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
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kit selection GUI opened from the party fight setup.
 *
 * <p>Lists every kit the leader has permission to use. Clicking a kit
 * either starts an internal FFA or Split match, or sends a challenge to
 * another party, depending on whether a target leader was provided.</p>
 *
 * <p>The slot-to-kit map is created here and passed to the holder by
 * reference, because it is populated after the holder is created. This
 * is the only place in the codebase where a holder is intentionally
 * given a live view of the map instead of a defensive copy.</p>
 */
public final class PartyFightKitGui {

    /**
     * Total number of slots of the inventory.
     */
    private static final int SIZE = 27;

    /**
     * Slots that can host a kit icon, in visual order.
     */
    private static final int[] KIT_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    /**
     * Slot of the "no kit" button.
     */
    private static final int NO_KIT_SLOT = 22;

    /**
     * Prevents instantiation.
     */
    private PartyFightKitGui() {
    }

    /**
     * Opens the kit selection GUI for an internal match.
     *
     * @param plugin  owning plugin
     * @param viewer  the party leader
     * @param partyId id of the party
     * @param type    chosen fight mode
     */
    public static void open(DuelsPlugin plugin, Player viewer, long partyId, PartyMatchType type) {
        open(plugin, viewer, partyId, type, null);
    }

    /**
     * Opens the kit selection GUI, optionally targeting another party.
     *
     * @param plugin           owning plugin
     * @param viewer           the party leader
     * @param partyId          id of the party
     * @param type             chosen fight mode
     * @param targetLeaderUuid uuid of the opposing party leader for a
     *                         challenge, or {@code null}
     */
    public static void open(DuelsPlugin plugin, Player viewer, long partyId,
                            PartyMatchType type, @Nullable UUID targetLeaderUuid) {
        Map<Integer, String> slotToKit = new HashMap<>();
        List<Kit> kits = List.copyOf(plugin.kits().all());

        PartyFightKitGuiHolder holder = new PartyFightKitGuiHolder(
                viewer.getUniqueId(), partyId, type, targetLeaderUuid, slotToKit);

        String subtitle = targetLeaderUuid != null
                ? "Challenge"
                : type.getLabel();
        Component title = MiniMessage.miniMessage().deserialize(
                "<dark_gray>» <gold><bold>Choose a kit</bold></gold> <gray>· "
                        + subtitle + " <dark_gray>«");

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

    /**
     * Builds the "no kit" button.
     *
     * @return the built button
     */
    private static ItemStack buildNoKitButton() {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            MiniMessage mm = MiniMessage.miniMessage();
            meta.displayName(mm.deserialize("<red><bold>No kit")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    mm.deserialize("<gray>Use players' own inventories.")
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    mm.deserialize("<yellow>▶ Click to select")
                            .decoration(TextDecoration.ITALIC, false)
            ));
            stack.setItemMeta(meta);
        }
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