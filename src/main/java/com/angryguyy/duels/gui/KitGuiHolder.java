package com.angryguyy.duels.gui;

import com.angryguyy.duels.kit.Kit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
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
 * <p>The mapping returned by {@link #getSlotToKit()} is an unmodifiable
 * view, so the click listener cannot accidentally corrupt the holder
 * by mutating the returned map.</p>
 */
public class KitGuiHolder implements InventoryHolder {

    private final UUID viewer;
    private final UUID target;
    private final Map<Integer, Kit> slotToKit;

    private Inventory inventory;

    /**
     * Creates a new holder.
     *
     * @param viewer    uuid of the player viewing the GUI
     * @param target    uuid of the duel target
     * @param slotToKit mapping from slot index to kit
     */
    public KitGuiHolder(UUID viewer, UUID target, Map<Integer, Kit> slotToKit) {
        this.viewer = viewer;
        this.target = target;
        this.slotToKit = slotToKit;
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