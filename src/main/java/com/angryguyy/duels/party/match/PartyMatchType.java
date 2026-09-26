package com.angryguyy.duels.party.match;

/**
 * Enumerates the fight modes available inside a party.
 */
public enum PartyMatchType {

    /**
     * Every member fights for themselves. The last player alive wins.
     */
    FFA("Free For All"),

    /**
     * The party is split into two balanced teams that fight each other.
     */
    SPLIT("Split");

    private final String label;

    PartyMatchType(String label) {
        this.label = label;
    }

    /**
     * Returns the human-readable label.
     *
     * @return label
     */
    public String getLabel() {
        return label;
    }
}