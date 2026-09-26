package com.angryguyy.duels.gui;

import com.angryguyy.duels.stats.LeaderboardCategory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * {@link InventoryHolder} marker for the leaderboard GUI.
 *
 * <p>Carries the category currently displayed and the page number so
 * that the click listener can resolve navigation buttons without
 * inspecting the inventory contents.</p>
 */
public class LeaderboardGuiHolder implements InventoryHolder {

    /**
     * Uuid of the viewer.
     */
    private final UUID viewer;

    /**
     * Category currently displayed.
     */
    private final LeaderboardCategory category;

    /**
     * Current page number, one-based.
     */
    private final int page;

    /**
     * Inventory instance attached to this holder.
     */
    private Inventory inventory;

    /**
     * Creates a new holder.
     *
     * @param viewer   uuid of the viewer
     * @param category category displayed
     * @param page     current page, one-based
     */
    public LeaderboardGuiHolder(UUID viewer, LeaderboardCategory category, int page) {
        this.viewer = viewer;
        this.category = category;
        this.page = page;
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
     * Returns the displayed category.
     *
     * @return category
     */
    public LeaderboardCategory getCategory() {
        return category;
    }

    /**
     * Returns the current page.
     *
     * @return page, one-based
     */
    public int getPage() {
        return page;
    }

    /**
     * Attaches the inventory to this holder.
     *
     * @param inventory created inventory
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