package com.angryguyy.duels.command;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.gui.PartyFightKitGui;
import com.angryguyy.duels.party.Party;
import com.angryguyy.duels.party.PartyInvite;
import com.angryguyy.duels.party.match.PartyMatchType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Handler for the {@code /party} command and its subcommands.
 *
 * <p>Subcommands cover party lifecycle, membership, social features and
 * fight setup. Fight commands open the kit selection GUI, which then
 * either starts an internal match or sends a challenge to another
 * party.</p>
 */
public class PartyCommand implements CommandExecutor, TabCompleter {

    /**
     * Owning plugin instance.
     */
    private final DuelsPlugin plugin;

    /**
     * Creates a new command handler.
     *
     * @param plugin owning plugin
     */
    public PartyCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            plugin.messages().send(sender, "party.help");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> handleCreate(sender);
            case "disband" -> handleDisband(sender);
            case "invite" -> handleInvite(sender, args);
            case "accept" -> handleAccept(sender);
            case "deny" -> handleDeny(sender);
            case "kick" -> handleKick(sender, args);
            case "leave" -> handleLeave(sender);
            case "transfer" -> handleTransfer(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender);
            case "announce" -> handleAnnounce(sender, args);
            case "public" -> handlePublic(sender, args);
            case "join" -> handleJoin(sender, args);
            case "ffa" -> handleFfa(sender);
            case "split" -> handleSplit(sender);
            case "challenge" -> handleChallenge(sender, args);
            case "help" -> plugin.messages().send(sender, "party.help");
            default -> plugin.messages().send(sender, "party.help");
        }
        return true;
    }

    // ------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------

    /**
     * Creates a new party with the sender as leader and gives them the
     * leader hotbar items.
     *
     * @param sender source of the command
     */
    private void handleCreate(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;

        if (plugin.parties().isInParty(player.getUniqueId())) {
            plugin.messages().send(player, "party.already-in");
            return;
        }

        Party party = plugin.parties().create(player);
        if (party == null) {
            plugin.messages().send(player, "party.create-failed");
            return;
        }

        plugin.partyItems().giveItems(player);
        plugin.messages().send(player, "party.created");
    }

    /**
     * Disbands the sender's party. Only the leader may do this.
     *
     * @param sender source of the command
     */
    private void handleDisband(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;

        Party party = plugin.parties().getParty(player.getUniqueId());
        if (party == null) {
            plugin.messages().send(player, "party.not-in");
            return;
        }
        if (!party.isLeader(player.getUniqueId())) {
            plugin.messages().send(player, "party.only-leader");
            return;
        }

        plugin.partyItems().removeItems(player);
        plugin.parties().disband(player);
        plugin.messages().send(player, "party.disbanded");
    }

    /**
     * Makes the sender leave their party.
     *
     * <p>If the sender was the leader, the leader hotbar items are
     * removed from them and given to the new leader, if any, so the
     * party keeps a functional leader item bar after the automatic
     * promotion performed by {@code PartyManager.leave}.</p>
     *
     * @param sender source of the command
     */
    private void handleLeave(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;

        Party party = plugin.parties().getParty(player.getUniqueId());
        if (party == null) {
            plugin.messages().send(player, "party.not-in");
            return;
        }

        boolean wasLeader = party.isLeader(player.getUniqueId());
        Party after = plugin.parties().leave(player);
        plugin.messages().send(player, "party.left");

        if (!wasLeader) return;
        plugin.partyItems().removeItems(player);
        if (after == null) return;

        Player newLeader = Bukkit.getPlayer(after.getLeader());
        if (newLeader != null && newLeader.isOnline()) {
            plugin.partyItems().giveItems(newLeader);
        }
    }

    /**
     * Transfers leadership to another online member.
     *
     * @param sender source of the command
     * @param args   {@code args[1]} is the target player name
     */
    private void handleTransfer(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length < 2) {
            plugin.messages().send(player, "party.transfer-usage");
            return;
        }

        Party party = plugin.parties().getParty(player.getUniqueId());
        if (party == null) {
            plugin.messages().send(player, "party.not-in");
            return;
        }
        if (!party.isLeader(player.getUniqueId())) {
            plugin.messages().send(player, "party.only-leader");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            plugin.messages().send(player, "party.target-offline");
            return;
        }
        if (!party.isMember(target.getUniqueId())) {
            plugin.messages().send(player, "party.target-not-member");
            return;
        }

        plugin.partyItems().removeItems(player);
        boolean ok = plugin.parties().promote(player, target.getUniqueId());
        if (!ok) {
            plugin.partyItems().giveItems(player);
            plugin.messages().send(player, "party.transfer-failed");
            return;
        }

        plugin.partyItems().giveItems(target);
        plugin.messages().send(player, "party.transfer-sent",
                Map.of("player", target.getName()));
        plugin.messages().send(target, "party.transfer-received",
                Map.of("player", player.getName()));
    }

    // ------------------------------------------------------------
    // Invitations
    // ------------------------------------------------------------

    /**
     * Sends an invitation to another player. Only the leader may do
     * this.
     *
     * @param sender source of the command
     * @param args   {@code args[1]} is the target player name
     */
    private void handleInvite(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length < 2) {
            plugin.messages().send(player, "party.invite-usage");
            return;
        }

        Party party = plugin.parties().getParty(player.getUniqueId());
        if (party == null) {
            plugin.messages().send(player, "party.not-in");
            return;
        }
        if (!party.isLeader(player.getUniqueId())) {
            plugin.messages().send(player, "party.only-leader");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            plugin.messages().send(player, "party.target-offline");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.messages().send(player, "party.invite-self");
            return;
        }
        if (plugin.parties().isInParty(target.getUniqueId())) {
            plugin.messages().send(player, "party.target-already-in");
            return;
        }
        if (party.size() >= Party.MAX_MEMBERS) {
            plugin.messages().send(player, "party.full");
            return;
        }

        PartyInvite invite = plugin.parties().invite(player, target);
        if (invite == null) {
            plugin.messages().send(player, "party.invite-failed");
            return;
        }

        plugin.messages().send(player, "party.invite-sent",
                Map.of("player", target.getName()));
        plugin.messages().send(target, "party.invite-received",
                Map.of("player", player.getName()));
    }

    /**
     * Accepts the sender's pending invitation and notifies the party.
     *
     * @param sender source of the command
     */
    private void handleAccept(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;

        Party party = plugin.parties().acceptInvite(player);
        if (party == null) {
            plugin.messages().send(player, "party.no-invite");
            return;
        }

        plugin.messages().send(player, "party.invite-accepted-self",
                Map.of("player", nameOf(party.getLeader())));
        broadcastToParty(party, "party.member-joined",
                Map.of("player", player.getName()));
    }

    /**
     * Denies the sender's pending invitation.
     *
     * @param sender source of the command
     */
    private void handleDeny(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;

        boolean ok = plugin.parties().denyInvite(player);
        if (!ok) {
            plugin.messages().send(player, "party.no-invite");
            return;
        }
        plugin.messages().send(player, "party.invite-denied");
    }

    // ------------------------------------------------------------
    // Membership
    // ------------------------------------------------------------

    /**
     * Kicks a member out of the sender's party. Only the leader may do
     * this.
     *
     * @param sender source of the command
     * @param args   {@code args[1]} is the target player name
     */
    private void handleKick(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length < 2) {
            plugin.messages().send(player, "party.kick-usage");
            return;
        }

        Party party = plugin.parties().getParty(player.getUniqueId());
        if (party == null) {
            plugin.messages().send(player, "party.not-in");
            return;
        }
        if (!party.isLeader(player.getUniqueId())) {
            plugin.messages().send(player, "party.only-leader");
            return;
        }

        UUID target = resolveUuid(args[1]);
        if (target == null || !party.isMember(target) || party.isLeader(target)) {
            plugin.messages().send(player, "party.target-not-member");
            return;
        }

        boolean ok = plugin.parties().kick(player, target);
        if (!ok) {
            plugin.messages().send(player, "party.kick-failed");
            return;
        }

        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            plugin.messages().send(targetPlayer, "party.kicked-self");
            plugin.partyItems().discardBackup(targetPlayer.getUniqueId());
        }
        plugin.messages().send(player, "party.kicked-other",
                Map.of("player", nameOf(target)));
    }

    /**
     * Lists the members of the sender's party with their online state.
     *
     * @param sender source of the command
     */
    private void handleList(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;

        Party party = plugin.parties().getParty(player.getUniqueId());
        if (party == null) {
            plugin.messages().send(player, "party.not-in");
            return;
        }

        plugin.messages().sendList(player, "party.list-header",
                Map.of("count", String.valueOf(party.size()),
                        "max", String.valueOf(Party.MAX_MEMBERS)));

        for (UUID uuid : party.getMembers().keySet()) {
            boolean online = Bukkit.getPlayer(uuid) != null;
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) name = uuid.toString().substring(0, 8);

            String role = party.isLeader(uuid) ? "leader" : "member";
            String status = online ? "online" : "offline";
            plugin.messages().send(player, "party.list-" + role + "-" + status,
                    Map.of("player", name));
        }
    }

    /**
     * Shows a compact info line about the sender's party.
     *
     * @param sender source of the command
     */
    private void handleInfo(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;

        Party party = plugin.parties().getParty(player.getUniqueId());
        if (party == null) {
            plugin.messages().send(player, "party.not-in");
            return;
        }

        plugin.messages().send(player, "party.info", Map.of(
                "id", String.valueOf(party.getId()),
                "leader", nameOf(party.getLeader()),
                "size", String.valueOf(party.size()),
                "max", String.valueOf(Party.MAX_MEMBERS)
        ));
    }

    // ------------------------------------------------------------
    // Social
    // ------------------------------------------------------------

    /**
     * Broadcasts a message to every online member of the sender's
     * party.
     *
     * @param sender source of the command
     * @param args   joined into a single message
     */
    private void handleAnnounce(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length < 2) {
            plugin.messages().send(player, "party.announce-usage");
            return;
        }

        Party party = plugin.parties().getParty(player.getUniqueId());
        if (party == null) {
            plugin.messages().send(player, "party.not-in");
            return;
        }

        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        broadcastToParty(party, "party.announce",
                Map.of("player", player.getName(), "message", message));
    }

    /**
     * Toggles or sets the public flag of the sender's party. Only the
     * leader may do this.
     *
     * @param sender source of the command
     * @param args   optional {@code on|off|true|false}
     */
    private void handlePublic(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;

        Party party = plugin.parties().getParty(player.getUniqueId());
        if (party == null) {
            plugin.messages().send(player, "party.not-in");
            return;
        }
        if (!party.isLeader(player.getUniqueId())) {
            plugin.messages().send(player, "party.only-leader");
            return;
        }

        boolean target = party.isPublic();
        if (args.length >= 2) {
            String arg = args[1].toLowerCase(Locale.ROOT);
            if (arg.equals("on") || arg.equals("true")) target = true;
            else if (arg.equals("off") || arg.equals("false")) target = false;
        } else {
            target = !party.isPublic();
        }

        party.setPublic(target);
        plugin.messages().send(player, target
                ? "party.public-on" : "party.public-off");
    }

    /**
     * Joins a public party led by the named player.
     *
     * @param sender source of the command
     * @param args   {@code args[1]} is the leader's name
     */
    private void handleJoin(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length < 2) {
            plugin.messages().send(player, "party.join-usage");
            return;
        }

        if (plugin.parties().isInParty(player.getUniqueId())) {
            plugin.messages().send(player, "party.already-in");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            plugin.messages().send(player, "party.target-offline");
            return;
        }

        Party party = plugin.parties().getParty(target.getUniqueId());
        if (party == null) {
            plugin.messages().send(player, "party.target-not-in");
            return;
        }
        if (!party.isPublic()) {
            plugin.messages().send(player, "party.not-public");
            return;
        }
        if (party.size() >= Party.MAX_MEMBERS) {
            plugin.messages().send(player, "party.full");
            return;
        }

        boolean ok = plugin.parties().joinPublic(player, party);
        if (!ok) {
            plugin.messages().send(player, "party.join-failed");
            return;
        }

        plugin.messages().send(player, "party.joined-self");
        broadcastToParty(party, "party.member-joined",
                Map.of("player", player.getName()));
    }

    // ------------------------------------------------------------
    // Fight
    // ------------------------------------------------------------

    /**
     * Opens the kit selection GUI to start a party FFA. Only the leader
     * may do this, and the party must have at least two members.
     *
     * @param sender source of the command
     */
    private void handleFfa(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;

        Party party = plugin.parties().getParty(player.getUniqueId());
        if (party == null) {
            plugin.messages().send(player, "party.not-in");
            return;
        }
        if (!party.isLeader(player.getUniqueId())) {
            plugin.messages().send(player, "party.only-leader");
            return;
        }
        if (party.size() < 2) {
            plugin.messages().send(player, "party.fight-need-members");
            return;
        }

        PartyFightKitGui.open(plugin, player, party.getId(), PartyMatchType.FFA);
    }

    /**
     * Opens the kit selection GUI to start a party split. Only the
     * leader may do this, and the party must have at least two members.
     *
     * @param sender source of the command
     */
    private void handleSplit(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;

        Party party = plugin.parties().getParty(player.getUniqueId());
        if (party == null) {
            plugin.messages().send(player, "party.not-in");
            return;
        }
        if (!party.isLeader(player.getUniqueId())) {
            plugin.messages().send(player, "party.only-leader");
            return;
        }
        if (party.size() < 2) {
            plugin.messages().send(player, "party.fight-need-members");
            return;
        }

        PartyFightKitGui.open(plugin, player, party.getId(), PartyMatchType.SPLIT);
    }

    /**
     * Opens the kit selection GUI to challenge another party.
     *
     * <p>The target argument must be the name of the leader of another
     * party. Both parties are validated and the final click on a kit
     * sends a duel request between the two teams.</p>
     *
     * @param sender source of the command
     * @param args   {@code args[1]} is the target leader name
     */
    private void handleChallenge(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length < 2) {
            plugin.messages().send(player, "party.challenge-usage");
            return;
        }

        Party ownParty = plugin.parties().getParty(player.getUniqueId());
        if (ownParty == null) {
            plugin.messages().send(player, "party.not-in");
            return;
        }
        if (!ownParty.isLeader(player.getUniqueId())) {
            plugin.messages().send(player, "party.only-leader");
            return;
        }
        if (ownParty.size() < 2) {
            plugin.messages().send(player, "party.challenge-need-members");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            plugin.messages().send(player, "party.challenge-target-offline");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.messages().send(player, "party.challenge-self");
            return;
        }

        Party targetParty = plugin.parties().getParty(target.getUniqueId());
        if (targetParty == null) {
            plugin.messages().send(player, "party.challenge-target-no-party");
            return;
        }
        if (!targetParty.isLeader(target.getUniqueId())) {
            plugin.messages().send(player, "party.challenge-target-not-leader");
            return;
        }
        if (targetParty.getId() == ownParty.getId()) {
            plugin.messages().send(player, "party.challenge-self");
            return;
        }
        if (targetParty.size() < 2) {
            plugin.messages().send(player, "party.challenge-target-need-members");
            return;
        }

        PartyFightKitGui.open(plugin, player, ownParty.getId(),
                PartyMatchType.FFA, target.getUniqueId());
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    /**
     * Resolves a command sender to a player, sending the standard
     * player-only message when the sender is not a player.
     *
     * @param sender source of the command
     * @return the player, or {@code null}
     */
    private @Nullable Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "general.player-only");
            return null;
        }
        return p;
    }

    /**
     * Sends a message to every online member of a party.
     *
     * @param party        the party
     * @param key          messages.yml key
     * @param placeholders placeholder values
     */
    private void broadcastToParty(Party party, String key, Map<String, String> placeholders) {
        for (UUID uuid : party.getMembers().keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                plugin.messages().send(p, key, placeholders);
            }
        }
    }

    /**
     * Resolves an offline player name to a uuid, using the cache when
     * the player is not online.
     *
     * @param name player name
     * @return uuid, or {@code null} if unknown
     */
    private @Nullable UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        var offline = Bukkit.getOfflinePlayerIfCached(name);
        return offline != null ? offline.getUniqueId() : null;
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

    // ------------------------------------------------------------
    // Tab completion
    // ------------------------------------------------------------

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return java.util.stream.Stream.of(
                            "create", "disband", "invite", "accept", "deny",
                            "kick", "leave", "transfer", "list", "info",
                            "announce", "public", "join", "ffa", "split",
                            "challenge", "help")
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("invite") || sub.equals("kick")
                    || sub.equals("transfer") || sub.equals("join")
                    || sub.equals("challenge")) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (sender instanceof Player sp && p.getUniqueId().equals(sp.getUniqueId())) continue;
                    names.add(p.getName());
                }
                return names.stream()
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
            if (sub.equals("public")) {
                return java.util.stream.Stream.of("on", "off")
                        .filter(s -> s.startsWith(prefix))
                        .toList();
            }
        }

        return List.of();
    }
}