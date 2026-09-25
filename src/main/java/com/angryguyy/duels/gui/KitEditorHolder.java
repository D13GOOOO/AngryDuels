package com.angryguyy.duels.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * {@link InventoryHolder} marker for the kit editor GUI.
 *
 * <p>Carries the viewer uuid and the kit id being edited. The actual
 * layout being edited lives inside the inventory itself; on save, the
 * listener reads slots 0-40 back into a map and stores it through the
 * {@code PlayerKitManager}.</p>
 */
public class KitEditorHolder implements InventoryHolder {

    private final UUID viewer;
    private final String kitId;

    private Inventory inventory;

    /**
     * Creates a new holder.
     *
     * @param viewer uuid of the editing player
     * @param kitId  kit id being edited
     */
    public KitEditorHolder(UUID viewer, String kitId) {
        this.viewer = viewer;
        this.kitId = kitId;
    }

    /**
     * Returns the uuid of the editing player.
     *
     * @return viewer uuid
     */
    public UUID getViewer() {
        return viewer;
    }

    /**
     * Returns the id of the kit being edited.
     *
     * @return kit id
     */
    public String getKitId() {
        return kitId;
    }

    /**
     * Attaches the created inventory to this holder.
     *
     * @param inventory the created inventory
     */
    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    /**
     * Returns the attached inventory.
     *
     * @return inventory
     */
    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}