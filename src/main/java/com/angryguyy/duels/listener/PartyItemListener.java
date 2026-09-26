package com.angryguyy.duels.listener;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.gui.PartyFightGui;
import com.angryguyy.duels.party.Party;
import com.angryguyy.duels.party.item.PartyItemFactory;
import com.angryguyy.duels.party.item.PartyItemType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Handles interactions with the party hotbar items.
 *
 * <p>Because right-clicks in air are reported by the server as already
 * cancelled, this listener deliberately does not use
 * {@code ignoreCancelled = true}. The separate use-item and use-block
 * result states exposed by {@link PlayerInteractEvent} are inspected
 * instead, so only genuine use attempts are processed and the event is
 * re-cancelled before the vanilla action runs.</p>
 *
 * <p>The items are locked to their assigned slots: dropping, dragging,
 * shift-clicking, moving into a container or swapping with the offhand
 * are all cancelled when a party item is involved.</p>
 */
public class PartyItemListener implements Listener {

    private final DuelsPlugin plugin;

    /**
     * Creates a new listener.
     *
     * @param plugin owning plugin
     */
    public PartyItemListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles right-clicks with a party item, on air or on a block.
     *
     * @param event the interact event
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        PartyItemType type = PartyItemFactory.typeOf(event.getItem());
        if (type == null) return;

        if (action == Action.RIGHT_CLICK_AIR
                && event.useItemInHand() == Event.Result.DENY) return;

        event.setCancelled(true);
        handleUse(event.getPlayer(), type);
    }

    /**
     * Cancels any attempt to drop a party item.
     *
     * @param event the drop event
     */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (PartyItemFactory.typeOf(event.getItemDrop().getItemStack()) != null) {
            event.setCancelled(true);
        }
    }

    /**
     * Blocks moving a party item inside any inventory.
     *
     * <p>Catches clicks in the player's own inventory as well as in
     * containers, so a party item cannot be stashed away. Actions that
     * do not involve a party item on either side of the transaction are
     * left untouched.</p>
     *
     * @param event the click event
     */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean currentIsParty = PartyItemFactory.typeOf(current) != null;
        boolean cursorIsParty = PartyItemFactory.typeOf(cursor) != null;

        if (!currentIsParty && !cursorIsParty) return;

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
            return;
        }

        if (currentIsParty || cursorIsParty) {
            event.setCancelled(true);
        }
    }

    /**
     * Blocks dragging a party item across slots.
     *
     * @param event the drag event
     */
    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (PartyItemFactory.typeOf(event.getOldCursor()) != null) {
            event.setCancelled(true);
            return;
        }
        for (ItemStack item : event.getNewItems().values()) {
            if (PartyItemFactory.typeOf(item) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Blocks swapping a party item with the offhand using the F key.
     *
     * @param event the swap event
     */
    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (PartyItemFactory.typeOf(event.getMainHandItem()) != null
                || PartyItemFactory.typeOf(event.getOffHandItem()) != null) {
            event.setCancelled(true);
        }
    }

    private void handleUse(Player player, PartyItemType type) {
        Party party = plugin.parties().getParty(player.getUniqueId());
        if (party == null) return;
        if (!party.isLeader(player.getUniqueId())) {
            plugin.messages().send(player, "party.only-leader");
            return;
        }

        switch (type) {
            case FIGHT -> {
                if (party.size() < 2) {
                    plugin.messages().send(player, "party.fight-need-members");
                    return;
                }
                PartyFightGui.open(plugin, player, party);
            }
            case INFO -> plugin.messages().send(player, "party.info-gui-soon");
            case INVITE -> plugin.messages().send(player, "party.invite-gui-soon");
            case DISBAND -> plugin.messages().send(player, "party.disband-gui-soon");
        }
    }
}