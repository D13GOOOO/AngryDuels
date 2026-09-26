package com.angryguyy.duels.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * {@link InventoryHolder} marker for the member actions submenu.
 *
 * <p>Carries the viewer, the party and the member the actions apply to.
 * The holder is used only by the leader.</p>
 */
public class PartyMemberActionsGuiHolder implements InventoryHolder {

    private final UUID viewer;
    private final long partyId;
    private final UUID target;

    private Inventory inventory;

    /**
     * Creates a new holder.
     *
     * @param viewer  uuid of the leader
     * @param partyId id of the party
     * @param target  uuid of the member the actions apply to
     */
    public PartyMemberActionsGuiHolder(UUID viewer, long partyId, UUID target) {
        this.viewer = viewer;
        this.partyId = partyId;
        this.target = target;
    }

    public UUID getViewer() { return viewer; }
    public long getPartyId() { return partyId; }
    public UUID getTarget() { return target; }

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