package com.angryguyy.duels.listener;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.gui.PartyDisbandConfirmGui;
import com.angryguyy.duels.gui.PartyDisbandConfirmGuiHolder;
import com.angryguyy.duels.gui.PartyFightGui;
import com.angryguyy.duels.gui.PartyFightGuiHolder;
import com.angryguyy.duels.gui.PartyFightKitGui;
import com.angryguyy.duels.gui.PartyFightKitGuiHolder;
import com.angryguyy.duels.gui.PartyInfoGui;
import com.angryguyy.duels.gui.PartyInfoGuiHolder;
import com.angryguyy.duels.gui.PartyInviteGui;
import com.angryguyy.duels.gui.PartyInviteGuiHolder;
import com.angryguyy.duels.gui.PartyMemberActionsGui;
import com.angryguyy.duels.gui.PartyMemberActionsGuiHolder;
import com.angryguyy.duels.party.Party;
import com.angryguyy.duels.party.PartyInvite;
import com.angryguyy.duels.party.match.PartyMatchType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles clicks and drags inside every party GUI.
 *
 * <p>Covers the mode selection screen, the kit selection screen, the
 * party info screen, the member actions submenu, the disband
 * confirmation screen and the invite screen. All clicks are cancelled;
 * the handlers only perform their own actions and never allow item
 * movement.</p>
 *
 * <p>Whenever a click would mutate the party, the current leader is
 * revalidated against the party state, so a GUI left open across a
 * leadership change does not allow stale actions. The viewer uuid
 * carried by the holder is also revalidated, so a click coming from a
 * viewer other than the one the GUI was opened for is silently
 * ignored.</p>
 */
public class PartyGuiListener implements Listener {

    /**
     * Owning plugin instance.
     */
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
     * Routes clicks to the correct handler based on the inventory
     * holder type.
     *
     * @param event the click event
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(event.getWhoClicked() instanceof Player viewer)) return;

        if (holder instanceof PartyFightGuiHolder gui) {
            event.setCancelled(true);
            if (!isOwner(viewer, gui.getViewer())) return;
            handleModeClick(viewer, gui, event.getRawSlot());
        } else if (holder instanceof PartyFightKitGuiHolder gui) {
            event.setCancelled(true);
            if (!isOwner(viewer, gui.getViewer())) return;
            handleKitClick(viewer, gui, event.getRawSlot());
        } else if (holder instanceof PartyInfoGuiHolder gui) {
            event.setCancelled(true);
            if (!isOwner(viewer, gui.getViewer())) return;
            handleInfoClick(viewer, gui, event.getRawSlot());
        } else if (holder instanceof PartyMemberActionsGuiHolder gui) {
            event.setCancelled(true);
            if (!isOwner(viewer, gui.getViewer())) return;
            handleActionsClick(viewer, gui, event.getRawSlot());
        } else if (holder instanceof PartyDisbandConfirmGuiHolder gui) {
            event.setCancelled(true);
            if (!isOwner(viewer, gui.getViewer())) return;
            handleDisbandClick(viewer, gui, event.getRawSlot());
        } else if (holder instanceof PartyInviteGuiHolder gui) {
            event.setCancelled(true);
            if (!isOwner(viewer, gui.getViewer())) return;
            handleInviteClick(viewer, gui, event.getRawSlot());
        }
    }

    /**
     * Prevents item dragging inside every party GUI.
     *
     * @param event the drag event
     */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof PartyFightGuiHolder
                || holder instanceof PartyFightKitGuiHolder
                || holder instanceof PartyInfoGuiHolder
                || holder instanceof PartyMemberActionsGuiHolder
                || holder instanceof PartyDisbandConfirmGuiHolder
                || holder instanceof PartyInviteGuiHolder) {
            event.setCancelled(true);
        }
    }

    /**
     * Checks that a viewer is the one a GUI was opened for.
     *
     * @param viewer   the clicking player
     * @param expected expected viewer uuid stored in the holder
     * @return {@code true} if the viewer is online and matches
     */
    private boolean isOwner(Player viewer, UUID expected) {
        return viewer.isOnline() && viewer.getUniqueId().equals(expected);
    }

    /**
     * Handles the mode selection screen.
     *
     * @param viewer the leader
     * @param gui    the mode GUI holder
     * @param slot   clicked slot
     */
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

    /**
     * Handles the final click in the kit selection screen.
     *
     * <p>Two cases are distinguished. When no target leader is set, the
     * click starts an internal FFA or Split match through the duel
     * manager. When a target leader is set, the click sends a duel
     * request from the viewer's party to the target's party.</p>
     *
     * @param viewer the leader
     * @param gui    the kit GUI holder
     * @param slot   clicked slot
     */
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

        viewer.closeInventory();

        UUID targetLeader = gui.getTargetLeaderUuid();
        if (targetLeader != null) {
            handleChallenge(viewer, party, targetLeader, kitId);
            return;
        }

        switch (gui.getType()) {
            case FFA -> plugin.duels().startPartyFfa(party, kitId);
            case SPLIT -> plugin.duels().startPartySplit(party, kitId);
        }
    }

    /**
     * Sends a duel request from one party to another.
     *
     * <p>Both parties are revalidated at click time: the target leader
     * must still be online and still leading their party, and both
     * parties must still have at least one online member beyond the
     * leader. The actual duel request is built by the duel manager.</p>
     *
     * @param viewer       the challenger leader
     * @param ownParty     the challenger's party
     * @param targetLeader uuid of the opposing party leader
     * @param kitId        chosen kit id, or {@code null}
     */
    private void handleChallenge(Player viewer, Party ownParty,
                                 UUID targetLeader, String kitId) {
        Player targetPlayer = Bukkit.getPlayer(targetLeader);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            plugin.messages().send(viewer, "party.challenge-target-offline");
            return;
        }

        Party targetParty = plugin.parties().getParty(targetLeader);
        if (targetParty == null) {
            plugin.messages().send(viewer, "party.challenge-target-no-party");
            return;
        }
        if (!targetParty.isLeader(targetLeader)) {
            plugin.messages().send(viewer, "party.challenge-target-not-leader");
            return;
        }
        if (targetParty.getId() == ownParty.getId()) {
            plugin.messages().send(viewer, "party.challenge-self");
            return;
        }

        List<Player> teamA = collectOnlineMembers(ownParty);
        List<Player> teamB = collectOnlineMembers(targetParty);
        if (teamA.size() < 2) {
            plugin.messages().send(viewer, "party.challenge-need-members");
            return;
        }
        if (teamB.size() < 2) {
            plugin.messages().send(viewer, "party.challenge-target-need-members");
            return;
        }

        plugin.duels().sendRequest(teamA, teamB, kitId);
    }

    /**
     * Returns the currently online members of a party.
     *
     * @param party the party
     * @return list of online players
     */
    private List<Player> collectOnlineMembers(Party party) {
        List<Player> out = new ArrayList<>();
        for (UUID uuid : party.getMembers().keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) out.add(p);
        }
        return out;
    }

    /**
     * Handles the party info screen: close, public toggle and member
     * actions.
     *
     * @param viewer the leader
     * @param gui    the info GUI holder
     * @param slot   clicked slot
     */
    private void handleInfoClick(Player viewer, PartyInfoGuiHolder gui, int slot) {
        Party party = plugin.parties().getPartyById(gui.getPartyId());
        if (party == null) {
            viewer.closeInventory();
            return;
        }

        if (slot == PartyInfoGui.closeSlot()) {
            viewer.closeInventory();
            return;
        }

        if (slot == PartyInfoGui.publicToggleSlot()) {
            if (!party.isLeader(viewer.getUniqueId())) return;
            boolean next = !party.isPublic();
            party.setPublic(next);
            plugin.messages().send(viewer, next
                    ? "party.public-on" : "party.public-off");
            PartyInfoGui.open(plugin, viewer, party);
            return;
        }

        UUID target = gui.getSlotToMember().get(slot);
        if (target == null) return;

        if (!party.isLeader(viewer.getUniqueId())) {
            return;
        }
        if (!party.isMember(target) || party.isLeader(target)) {
            PartyInfoGui.open(plugin, viewer, party);
            return;
        }

        PartyMemberActionsGui.open(plugin, viewer, party, target);
    }

    /**
     * Handles the member actions submenu.
     *
     * <p>The target must still be a member and not the leader, and must
     * be online for the promote and kick actions, so the party items
     * can be transferred without leaving the new leader without them.</p>
     *
     * @param viewer the leader
     * @param gui    the actions GUI holder
     * @param slot   clicked slot
     */
    private void handleActionsClick(Player viewer, PartyMemberActionsGuiHolder gui, int slot) {
        Party party = plugin.parties().getPartyById(gui.getPartyId());
        if (party == null || !party.isLeader(viewer.getUniqueId())) {
            viewer.closeInventory();
            return;
        }

        if (slot == PartyMemberActionsGui.cancelSlot()) {
            PartyInfoGui.open(plugin, viewer, party);
            return;
        }

        UUID target = gui.getTarget();
        if (!party.isMember(target) || party.isLeader(target)) {
            PartyInfoGui.open(plugin, viewer, party);
            return;
        }

        if (slot == PartyMemberActionsGui.promoteSlot()) {
            Player targetPlayer = Bukkit.getPlayer(target);
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                plugin.messages().send(viewer, "party.target-offline");
                PartyInfoGui.open(plugin, viewer, party);
                return;
            }

            plugin.partyItems().removeItems(viewer);
            boolean ok = plugin.parties().promote(viewer, target);
            if (!ok) {
                plugin.partyItems().giveItems(viewer);
                plugin.messages().send(viewer, "party.transfer-failed");
                PartyInfoGui.open(plugin, viewer, party);
                return;
            }

            plugin.partyItems().giveItems(targetPlayer);
            plugin.messages().send(targetPlayer, "party.transfer-received",
                    Map.of("player", viewer.getName()));
            plugin.messages().send(viewer, "party.transfer-sent",
                    Map.of("player", targetPlayer.getName()));
            viewer.closeInventory();
            return;
        }

        if (slot == PartyMemberActionsGui.kickSlot()) {
            boolean ok = plugin.parties().kick(viewer, target);
            if (!ok) {
                plugin.messages().send(viewer, "party.kick-failed");
                PartyInfoGui.open(plugin, viewer, party);
                return;
            }
            Player targetPlayer = Bukkit.getPlayer(target);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                plugin.messages().send(targetPlayer, "party.kicked-self");
                plugin.partyItems().discardBackup(targetPlayer.getUniqueId());
            }
            plugin.messages().send(viewer, "party.kicked-other",
                    Map.of("player", nameOf(target)));
            PartyInfoGui.open(plugin, viewer, party);
        }
    }

    /**
     * Handles the disband confirmation screen.
     *
     * @param viewer the leader
     * @param gui    the disband GUI holder
     * @param slot   clicked slot
     */
    private void handleDisbandClick(Player viewer, PartyDisbandConfirmGuiHolder gui, int slot) {
        Party party = plugin.parties().getPartyById(gui.getPartyId());
        if (party == null || !party.isLeader(viewer.getUniqueId())) {
            viewer.closeInventory();
            return;
        }

        if (slot == PartyDisbandConfirmGui.cancelSlot()) {
            viewer.closeInventory();
            return;
        }

        if (slot == PartyDisbandConfirmGui.confirmSlot()) {
            plugin.partyItems().removeItems(viewer);
            plugin.parties().disband(viewer);
            viewer.closeInventory();
            plugin.messages().send(viewer, "party.disbanded");
        }
    }

    /**
     * Handles the invite screen: pagination and member invitation.
     *
     * @param viewer the leader
     * @param gui    the invite GUI holder
     * @param slot   clicked slot
     */
    private void handleInviteClick(Player viewer, PartyInviteGuiHolder gui, int slot) {
        Party party = plugin.parties().getPartyById(gui.getPartyId());
        if (party == null || !party.isLeader(viewer.getUniqueId())) {
            viewer.closeInventory();
            return;
        }

        if (slot == PartyInviteGui.closeSlot()) {
            viewer.closeInventory();
            return;
        }

        if (slot == PartyInviteGui.prevSlot()) {
            PartyInviteGui.open(plugin, viewer, party, gui.getPage() - 1);
            return;
        }
        if (slot == PartyInviteGui.nextSlot()) {
            PartyInviteGui.open(plugin, viewer, party, gui.getPage() + 1);
            return;
        }

        UUID target = gui.getSlotToPlayer().get(slot);
        if (target == null) return;

        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            PartyInviteGui.open(plugin, viewer, party, gui.getPage());
            return;
        }
        if (plugin.parties().isInParty(target)) {
            PartyInviteGui.open(plugin, viewer, party, gui.getPage());
            return;
        }
        if (party.size() >= Party.MAX_MEMBERS) {
            plugin.messages().send(viewer, "party.full");
            viewer.closeInventory();
            return;
        }

        PartyInvite invite = plugin.parties().invite(viewer, targetPlayer);
        if (invite == null) {
            plugin.messages().send(viewer, "party.invite-failed");
            return;
        }

        plugin.messages().send(viewer, "party.invite-sent",
                Map.of("player", targetPlayer.getName()));
        plugin.messages().send(targetPlayer, "party.invite-received",
                Map.of("player", viewer.getName()));
        viewer.closeInventory();
    }

    /**
     * Returns the last known name of a player, falling back to a
     * shortened uuid when the name is unavailable.
     *
     * @param uuid player uuid
     * @return display name
     */
    private String nameOf(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }
}