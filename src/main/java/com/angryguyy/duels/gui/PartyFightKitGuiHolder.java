package com.angryguyy.duels.gui;

import com.angryguyy.duels.party.match.PartyMatchType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * {@link InventoryHolder} marker for the kit selection GUI opened from
 * the party fight setup.
 *
 * <p>When {@code targetLeaderUuid} is set, the GUI was opened through
 * the {@code /party challenge} command and the final kit click must
 * send a duel request between the two parties rather than start an
 * internal match. When it is {@code null}, the GUI belongs to an
 * internal FFA or Split match.</p>
 */
public class PartyFightKitGuiHolder implements InventoryHolder {

    private final UUID viewer;
    private final long partyId;
    private final PartyMatchType type;
    private final @Nullable UUID targetLeaderUuid;
    private final Map<Integer, String> slotToKit;

    private Inventory inventory;

    /**
     * Creates a new holder.
     *
     * @param viewer           uuid of the viewer
     * @param partyId          id of the party
     * @param type             chosen fight mode
     * @param targetLeaderUuid uuid of the opposing party leader for a
     *                         challenge, or {@code null} for an
     *                         internal match
     * @param slotToKit        mapping from inventory slot to kit id
     */
    public PartyFightKitGuiHolder(UUID viewer, long partyId,
                                  PartyMatchType type,
                                  @Nullable UUID targetLeaderUuid,
                                  Map<Integer, String> slotToKit) {
        this.viewer = viewer;
        this.partyId = partyId;
        this.type = type;
        this.targetLeaderUuid = targetLeaderUuid;
        this.slotToKit = slotToKit;
    }

    public UUID getViewer() { return viewer; }
    public long getPartyId() { return partyId; }
    public PartyMatchType getType() { return type; }
    public @Nullable UUID getTargetLeaderUuid() { return targetLeaderUuid; }
    public Map<Integer, String> getSlotToKit() { return slotToKit; }

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