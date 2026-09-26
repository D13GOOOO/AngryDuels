package com.angryguyy.duels.listener;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.gui.PartyFightGui;
import com.angryguyy.duels.gui.PartyFightGuiHolder;
import com.angryguyy.duels.gui.PartyFightKitGui;
import com.angryguyy.duels.gui.PartyFightKitGuiHolder;
import com.angryguyy.duels.party.Party;
import com.angryguyy.duels.party.match.PartyMatchType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

/**
 * Handles clicks and drags inside the party fight setup GUIs.
 *
 * <p>The listener covers two inventories: the mode selection screen and
 * the kit selection screen. All clicks are cancelled so no item can be
 * moved, and the only accepted actions are the two mode buttons, the
 * kit buttons and the "no kit" button.</p>
 */
public class PartyGuiListener implements Listener {

    private final DuelsPlugin plugin;

    /**
     * Creates a new listener.
     *
     * @param plugin owning plugin
     */
    public PartyGuiListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles clicks inside the fight setup GUIs.
     *
     * @param event the click event
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        if (viewer.getUniqueId() == null) return;

        if (holder instanceof PartyFightGuiHolder gui) {
            event.setCancelled(true);
            handleModeClick(viewer, gui, event.getRawSlot());
            return;
        }
        if (holder instanceof PartyFightKitGuiHolder gui) {
            event.setCancelled(true);
            handleKitClick(viewer, gui, event.getRawSlot());
        }
    }

    /**
     * Prevents item dragging inside the fight setup GUIs.
     *
     * @param event the drag event
     */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof PartyFightGuiHolder
                || holder instanceof PartyFightKitGuiHolder) {
            event.setCancelled(true);
        }
    }

    private void handleModeClick(Player viewer, PartyFightGuiHolder gui, int slot) {
        Party party = plugin.parties().getPartyById(gui.getPartyId());
        if (party == null || !party.isLeader(viewer.getUniqueId())) {
            viewer.closeInventory();
            return;
        }

        PartyMatchType type = null;
        if (slot == PartyFightGui.ffaSlot()) {
            type = PartyMatchType.FFA;
        } else if (slot == PartyFightGui.splitSlot()) {
            type = PartyMatchType.SPLIT;
        }
        if (type == null) return;

        viewer.closeInventory();
        PartyFightKitGui.open(plugin, viewer, party.getId(), type);
    }

    private void handleKitClick(Player viewer, PartyFightKitGuiHolder gui, int slot) {
        Party party = plugin.parties().getPartyById(gui.getPartyId());
        if (party == null || !party.isLeader(viewer.getUniqueId())) {
            viewer.closeInventory();
            return;
        }

        String kitId;
        if (slot == PartyFightKitGui.noKitSlot()) {
            kitId = null;
        } else {
            kitId = gui.getSlotToKit().get(slot);
            if (kitId == null) return;
        }

        plugin.partyMatchSetups().create(
                party.getId(), viewer.getUniqueId(), gui.getType(), kitId);

        viewer.closeInventory();
        plugin.messages().send(viewer, "party.match-setup-saved", Map.of(
                "type", gui.getType().getLabel(),
                "kit", kitId != null ? kitId : "none"
        ));
    }
}