package com.angryguyy.duels.gui;

import com.angryguyy.duels.party.match.PartyMatchType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * {@link InventoryHolder} marker for the kit selection GUI opened from
 * the party fight setup.
 */
public class PartyFightKitGuiHolder implements InventoryHolder {

    private final UUID viewer;
    private final long partyId;
    private final PartyMatchType type;
    private final Map<Integer, String> slotToKit;

    private Inventory inventory;

    /**
     * Creates a new holder.
     *
     * @param viewer    uuid of the viewer
     * @param partyId   id of the party
     * @param type      chosen fight mode
     * @param slotToKit mapping from inventory slot to kit id
     */
    public PartyFightKitGuiHolder(UUID viewer, long partyId,
                                  PartyMatchType type, Map<Integer, String> slotToKit) {
        this.viewer = viewer;
        this.partyId = partyId;
        this.type = type;
        this.slotToKit = slotToKit;
    }

    public UUID getViewer() { return viewer; }
    public long getPartyId() { return partyId; }
    public PartyMatchType getType() { return type; }
    public Map<Integer, String> getSlotToKit() { return slotToKit; }

    /**
     * Attaches the inventory to this holder.
     *
     * @param inventory created inventory
     */
    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}