package com.angryguyy.duels.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * {@link InventoryHolder} marker for the party invite GUI.
 *
 * <p>Carries the viewer, the party id, the current page number and a
 * mapping from each head slot to the corresponding online player.</p>
 */
public class PartyInviteGuiHolder implements InventoryHolder {

    private final UUID viewer;
    private final long partyId;
    private final int page;
    private final Map<Integer, UUID> slotToPlayer;

    private Inventory inventory;

    /**
     * Creates a new holder.
     *
     * @param viewer       uuid of the leader
     * @param partyId      id of the party
     * @param page         current page, zero-based
     * @param slotToPlayer mapping from slot to player uuid
     */
    public PartyInviteGuiHolder(UUID viewer, long partyId, int page,
                                Map<Integer, UUID> slotToPlayer) {
        this.viewer = viewer;
        this.partyId = partyId;
        this.page = page;
        this.slotToPlayer = slotToPlayer;
    }

    public UUID getViewer() { return viewer; }
    public long getPartyId() { return partyId; }
    public int getPage() { return page; }
    public Map<Integer, UUID> getSlotToPlayer() { return slotToPlayer; }

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