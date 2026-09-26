package com.angryguyy.duels.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * {@link InventoryHolder} marker for the Party Info GUI.
 *
 * <p>Carries the viewer uuid, the party id and a mapping from each
 * member head slot to the member uuid. The mapping is populated only
 * for the party leader: for regular members the slot map is empty, so
 * clicks on heads are ignored.</p>
 */
public class PartyInfoGuiHolder implements InventoryHolder {

    private final UUID viewer;
    private final long partyId;
    private final Map<Integer, UUID> slotToMember;

    private Inventory inventory;

    /**
     * Creates a new holder.
     *
     * @param viewer       uuid of the viewer
     * @param partyId      id of the party
     * @param slotToMember mapping from slot to member uuid, or an empty map
     */
    public PartyInfoGuiHolder(UUID viewer, long partyId, Map<Integer, UUID> slotToMember) {
        this.viewer = viewer;
        this.partyId = partyId;
        this.slotToMember = slotToMember;
    }

    public UUID getViewer() { return viewer; }
    public long getPartyId() { return partyId; }
    public Map<Integer, UUID> getSlotToMember() { return slotToMember; }

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