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
 * <p>Subcommands cover party lifecycle (create, disband, leave),
 * membership (invite, accept, deny, kick, transfer, list, info) and
 * social features (announce, public, join). Fight commands ({@code ffa}
 * and {@code split}) open the kit selection GUI directly, skipping the
 * mode selection step since the mode was already chosen by the
 * command.</p>
 */
public class PartyCommand implements CommandExecutor, TabCompleter {

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
            case "help" -> plugin.messages().send(sender, "party.help");
            default -> plugin.messages().send(sender, "party.help");
        }
        return true;
    }

    // ------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------

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

    private void handleLeave(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;

        Party party = plugin.parties().getParty(player.getUniqueId());
        if (party == null) {
            plugin.messages().send(player, "party.not-in");
            return;
        }

        boolean wasLeader = party.isLeader(player.getUniqueId());
        plugin.parties().leave(player);
        plugin.messages().send(player, "party.left");

        if (wasLeader) {
            plugin.partyItems().removeItems(player);
        }
    }

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

    private void handleAccept(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;

        Party party = plugin.parties().acceptInvite(player);
        if (party == null) {
            plugin.messages().send(player, "party.no-invite");
            return;
        }

        plugin.messages().send(player, "party.invite-accepted-self",
                Map.of("player", party.getLeader().toString()));
        broadcastToParty(party, "party.member-joined",
                Map.of("player", player.getName()));
    }

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
                Map.of("player", args[1]));
    }

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

            String roleKey = party.isLeader(uuid) ? "party.list-leader" : "party.list-member";
            plugin.messages().send(player, roleKey, Map.of(
                    "player", name,
                    "status", online ? "<green>online" : "<red>offline"
            ));
        }
    }

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
     * Opens the kit selection for a party FFA match.
     *
     * <p>Only the leader can start a party fight, and at least two
     * members are required so that there is someone to fight.</p>
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
     * Opens the kit selection for a party Split match.
     *
     * <p>Only the leader can start a party fight, and at least two
     * members are required so that the party can be split into two
     * teams. With exactly two members the split is 1v1.</p>
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

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private @Nullable Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "general.player-only");
            return null;
        }
        return p;
    }

    private void broadcastToParty(Party party, String key, Map<String, String> placeholders) {
        for (UUID uuid : party.getMembers().keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                plugin.messages().send(p, key, placeholders);
            }
        }
    }

    private @Nullable UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        var offline = Bukkit.getOfflinePlayerIfCached(name);
        return offline != null ? offline.getUniqueId() : null;
    }

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
                            "announce", "public", "join", "ffa", "split", "help")
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("invite") || sub.equals("kick")
                    || sub.equals("transfer") || sub.equals("join")) {
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