package com.angryguyy.duels.gui;

import com.angryguyy.duels.kit.Kit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@link InventoryHolder} marker for the kit selection GUI.
 *
 * <p>The holder carries the target of the pending duel and a mapping
 * from inventory slot to kit, so the click listener can identify the
 * GUI reliably and resolve the clicked slot without relying on the
 * inventory title or item comparison.</p>
 *
 * <p>Using a dedicated holder is the recommended way to distinguish
 * plugin GUIs from third-party inventories, since titles can be edited
 * by players and item names are not guaranteed to be unique.</p>
 *
 * <p>The slot-to-kit mapping is copied at construction time and exposed
 * through an unmodifiable view, so neither the caller nor the click
 * listener can mutate the holder's internal state.</p>
 */
public class KitGuiHolder implements InventoryHolder {

    /**
     * Uuid of the player viewing the GUI.
     */
    private final UUID viewer;

    /**
     * Uuid of the duel target.
     */
    private final UUID target;

    /**
     * Mapping from inventory slot to kit, owned by this holder.
     */
    private final Map<Integer, Kit> slotToKit;

    /**
     * Inventory instance attached to this holder.
     */
    private Inventory inventory;

    /**
     * Creates a new holder.
     *
     * <p>The mapping is copied defensively so that the caller cannot
     * mutate the holder's state after construction.</p>
     *
     * @param viewer    uuid of the player viewing the GUI
     * @param target    uuid of the duel target
     * @param slotToKit mapping from slot index to kit
     */
    public KitGuiHolder(UUID viewer, UUID target, Map<Integer, Kit> slotToKit) {
        this.viewer = viewer;
        this.target = target;
        this.slotToKit = new HashMap<>(slotToKit);
    }

    /**
     * Returns the uuid of the viewer.
     *
     * @return viewer uuid
     */
    public UUID getViewer() {
        return viewer;
    }

    /**
     * Returns the uuid of the duel target.
     *
     * @return target uuid
     */
    public UUID getTarget() {
        return target;
    }

    /**
     * Returns an unmodifiable view of the slot-to-kit mapping.
     *
     * @return mapping used by the click listener
     */
    public Map<Integer, Kit> getSlotToKit() {
        return Collections.unmodifiableMap(slotToKit);
    }

    /**
     * Attaches the inventory instance to this holder.
     *
     * @param inventory the created inventory
     */
    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    /**
     * Returns the inventory, as required by {@link InventoryHolder}.
     *
     * @return the attached inventory
     */
    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}