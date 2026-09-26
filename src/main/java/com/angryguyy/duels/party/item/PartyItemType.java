package com.angryguyy.duels.party.item;

/**
 * Enumerates the four hotbar items given to the leader of a party.
 *
 * <p>The type is stored inside the item's persistent data container
 * under the {@code angryduels:party_item} key, so the plugin can
 * recognize its own items even after they are moved between slots.</p>
 */
public enum PartyItemType {

    /** Opens the fight setup GUI (split / ffa). */
    FIGHT("fight", 0),

    /** Opens the party info GUI. */
    INFO("info", 2),

    /** Opens the invite info GUI. */
    INVITE("invite", 4),

    /** Disbands the party when right-clicked. */
    DISBAND("disband", 6);

    private final String key;
    private final int defaultSlot;

    PartyItemType(String key, int defaultSlot) {
        this.key = key;
        this.defaultSlot = defaultSlot;
    }

    /**
     * Returns the persistent data container key.
     *
     * @return key
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns the hotbar slot the item is placed in by default.
     *
     * @return default hotbar slot
     */
    public int getDefaultSlot() {
        return defaultSlot;
    }
}