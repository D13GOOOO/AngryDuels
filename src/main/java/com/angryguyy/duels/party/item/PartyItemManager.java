package com.angryguyy.duels.party.item;

import com.angryguyy.duels.party.Party;
import com.angryguyy.duels.party.PartyManager;
import com.angryguyy.duels.util.Log;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gives and removes the leader party items in the player hotbar.
 *
 * <p>When a party is created, the leader receives four tagged items in
 * the hotbar. Any pre-existing item in those slots is temporarily
 * displaced to a free slot, and its original position is remembered so
 * it can be restored exactly when the party is disbanded or the leader
 * leaves.</p>
 *
 * <p>The displaced items are kept in memory only. If the server crashes
 * while a player has the party items, the displaced items are lost; this
 * is considered acceptable because the operation is short-lived and
 * players are expected to run {@code /party disband} when they are done.
 * A future iteration may persist the backup alongside the player's
 * layout.</p>
 */
public class PartyItemManager {

    private final PartyManager partyManager;

    /** Displaced items per leader, keyed by slot. */
    private final Map<UUID, Map<Integer, ItemStack>> displaced = new HashMap<>();

    /**
     * Creates a new party item manager.
     *
     * @param partyManager the party manager, used for validation
     */
    public PartyItemManager(PartyManager partyManager) {
        this.partyManager = partyManager;
    }

    /**
     * Gives the four party items to a party leader.
     *
     * <p>Any item already present in the destination slots is moved to
     * the first free slot of the inventory. If no free slot exists, the
     * conflicting item is dropped at the player's feet. The displaced
     * items are remembered for later restoration.</p>
     *
     * @param leader the party leader
     */
    public void giveItems(Player leader) {
        UUID uuid = leader.getUniqueId();
        if (displaced.containsKey(uuid)) return;

        PlayerInventory inv = leader.getInventory();
        Map<Integer, ItemStack> backup = new HashMap<>();

        for (PartyItemType type : PartyItemType.values()) {
            int slot = type.getDefaultSlot();
            ItemStack existing = inv.getItem(slot);
            if (existing != null && existing.getType() != org.bukkit.Material.AIR) {
                backup.put(slot, existing.clone());
                inv.setItem(slot, null);
                if (!inv.addItem(existing).isEmpty()) {
                    leader.getWorld().dropItemNaturally(leader.getLocation(), existing);
                }
            }
            inv.setItem(slot, PartyItemFactory.create(type));
        }

        displaced.put(uuid, backup);
        Log.debug("Gave party items to %s", leader.getName());
    }

    /**
     * Removes the party items from a leader and restores any displaced
     * item to its original slot.
     *
     * <p>If an original slot is now occupied by another item, the
     * restored item is dropped at the player's feet instead of
     * overwriting.</p>
     *
     * @param leader the party leader
     */
    public void removeItems(Player leader) {
        UUID uuid = leader.getUniqueId();
        PlayerInventory inv = leader.getInventory();

        for (PartyItemType type : PartyItemType.values()) {
            int slot = type.getDefaultSlot();
            ItemStack current = inv.getItem(slot);
            if (current != null && PartyItemFactory.typeOf(current) == type) {
                inv.setItem(slot, null);
            }
        }

        Map<Integer, ItemStack> backup = displaced.remove(uuid);
        if (backup == null) return;

        for (Map.Entry<Integer, ItemStack> entry : backup.entrySet()) {
            int slot = entry.getKey();
            ItemStack item = entry.getValue();
            ItemStack existing = inv.getItem(slot);
            if (existing == null || existing.getType() == org.bukkit.Material.AIR) {
                inv.setItem(slot, item);
            } else if (!inv.addItem(item).isEmpty()) {
                leader.getWorld().dropItemNaturally(leader.getLocation(), item);
            }
        }
        Log.debug("Removed party items from %s", leader.getName());
    }

    /**
     * Removes the party items without restoring the backup.
     *
     * <p>Used during shutdown or when the backup is already known to be
     * stale. The backup entry is discarded.</p>
     *
     * @param uuid the player uuid
     */
    public void discardBackup(UUID uuid) {
        displaced.remove(uuid);
    }

    /**
     * Clears every backup. Called on plugin shutdown.
     */
    public void shutdown() {
        displaced.clear();
    }
}