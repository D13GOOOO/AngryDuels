package com.angryguyy.duels.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * {@link InventoryHolder} marker for the disband confirmation GUI.
 */
public class PartyDisbandConfirmGuiHolder implements InventoryHolder {

    private final UUID viewer;
    private final long partyId;

    private Inventory inventory;

    /**
     * Creates a new holder.
     *
     * @param viewer  uuid of the leader
     * @param partyId id of the party
     */
    public PartyDisbandConfirmGuiHolder(UUID viewer, long partyId) {
        this.viewer = viewer;
        this.partyId = partyId;
    }

    public UUID getViewer() { return viewer; }
    public long getPartyId() { return partyId; }

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