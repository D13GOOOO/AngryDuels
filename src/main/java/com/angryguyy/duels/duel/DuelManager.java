package com.angryguyy.duels.duel;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.arena.Arena;
import com.angryguyy.duels.event.DuelEndEvent;
import com.angryguyy.duels.event.DuelRequestEvent;
import com.angryguyy.duels.event.DuelStartEvent;
import com.angryguyy.duels.party.Party;
import com.angryguyy.duels.util.Log;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Central coordinator for every duel-related activity.
 *
 * <p>The manager supports classic 1v1 duels, team fights between two
 * parties, and free-for-all matches inside a single party. Every
 * request and session carries either two team lists or a single FFA
 * list. The manager is the only component allowed to mutate that
 * state; listeners, commands and GUIs interact with duels exclusively
 * through the public methods declared here.</p>
 *
 * <p>Snapshot restoration at the end of a match follows a two-track
 * rule: for non-death endings, every participant is restored directly
 * by this manager; for death endings, only the winning players are
 * restored here, because the losing players will die and be restored
 * by the respawn listener once their vanilla respawn completes.</p>
 *
 * <p>All state is main-thread only. The manager never touches the
 * database; persistence is delegated to the snapshot and stats
 * subsystems, which schedule their own asynchronous work.</p>
 */
public class DuelManager {

    /**
     * Owning plugin instance, used to reach every subsystem.
     */
    private final DuelsPlugin plugin;

    /**
     * Outgoing requests, keyed by sender uuid. For team requests every
     * member of the sender team maps to the same {@link DuelRequest}
     * instance.
     */
    private final Map<UUID, DuelRequest> outgoing = new HashMap<>();

    /**
     * Incoming requests, keyed by recipient uuid. For team requests
     * every member of the recipient team maps to the same
     * {@link DuelRequest} instance.
     */
    private final Map<UUID, DuelRequest> incoming = new HashMap<>();

    /**
     * Active sessions, keyed by participant uuid.
     */
    private final Map<UUID, DuelSession> sessions = new HashMap<>();

    /**
     * Set of alive players per session, keyed by the session's
     * representative (the first element of team A).
     */
    private final Map<UUID, Set<UUID>> alivePlayers = new HashMap<>();

    /**
     * Cooldown deadlines, keyed by sender uuid.
     */
    private final Map<UUID, Instant> cooldowns = new HashMap<>();

    /**
     * Repeating task that expires old requests, or {@code null} when
     * the manager has not been started or has been shut down.
     */
    private BukkitTask timeoutTask;

    /**
     * Creates a new duel manager.
     *
     * @param plugin owning plugin
     */
    public DuelManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the repeating task that expires pending requests.
     */
    public void start() {
        timeoutTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /**
     * Ends every active session with {@link DuelEndReason#PLUGIN_DISABLE},
     * stops the timeout task and clears all state.
     */
    public void shutdown() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
        for (DuelSession session : new ArrayList<>(new HashSet<>(sessions.values()))) {
            endSession(session, List.of(), List.of(), DuelEndReason.PLUGIN_DISABLE, false);
        }
        sessions.clear();
        alivePlayers.clear();
        outgoing.clear();
        incoming.clear();
        cooldowns.clear();
    }

    // ------------------------------------------------------------
    // State queries
    // ------------------------------------------------------------

    /**
     * Returns the coarse-grained state of a player with respect to
     * duels.
     *
     * @param uuid player uuid
     * @return current state
     */
    public DuelState getState(UUID uuid) {
        if (sessions.containsKey(uuid)) return DuelState.IN_DUEL;
        if (outgoing.containsKey(uuid) || incoming.containsKey(uuid)) return DuelState.IN_REQUEST;
        return DuelState.IDLE;
    }

    /**
     * Checks whether a player is currently part of an active session.
     *
     * @param uuid player uuid
     * @return {@code true} if the player is in a duel
     */
    public boolean isInDuel(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    /**
     * Checks whether a player has an outgoing or incoming request.
     *
     * @param uuid player uuid
     * @return {@code true} if a request is pending
     */
    public boolean hasPending(UUID uuid) {
        return outgoing.containsKey(uuid) || incoming.containsKey(uuid);
    }

    /**
     * Returns the active session of a player, if any.
     *
     * @param uuid player uuid
     * @return session, or {@code null}
     */
    public DuelSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    /**
     * Returns the outgoing request of a player, if any.
     *
     * @param uuid player uuid
     * @return request, or {@code null}
     */
    public DuelRequest getOutgoing(UUID uuid) {
        return outgoing.get(uuid);
    }

    /**
     * Returns the incoming request of a player, if any.
     *
     * @param uuid player uuid
     * @return request, or {@code null}
     */
    public DuelRequest getIncoming(UUID uuid) {
        return incoming.get(uuid);
    }

    /**
     * Returns the remaining cooldown of a player, in seconds.
     *
     * @param uuid player uuid
     * @return remaining seconds, or {@code 0} if no cooldown is active
     */
    public long getRemainingCooldown(UUID uuid) {
        Instant until = cooldowns.get(uuid);
        if (until == null) return 0;
        return Math.max(0, Duration.between(Instant.now(), until).getSeconds());
    }

    /**
     * Checks whether a player is currently on cooldown.
     *
     * @param uuid player uuid
     * @return {@code true} if the cooldown is still running
     */
    public boolean hasCooldown(UUID uuid) {
        return getRemainingCooldown(uuid) > 0;
    }

    /**
     * Applies the configured request cooldown to a player.
     *
     * @param uuid player uuid
     */
    private void applyCooldown(UUID uuid) {
        int cd = plugin.config().cooldownSeconds();
        if (cd > 0) {
            cooldowns.put(uuid, Instant.now().plusSeconds(cd));
        }
    }

    // ------------------------------------------------------------
    // Request flow
    // ------------------------------------------------------------

    /**
     * Sends a 1v1 request.
     *
     * @param sender sender player
     * @param target target player
     * @param kitId  optional kit id, or {@code null}
     */
    public void sendRequest(Player sender, Player target, String kitId) {
        sendRequest(List.of(sender), List.of(target), kitId);
    }

    /**
     * Sends a team request.
     *
     * <p>The first element of each team acts as the representative: the
     * sender side receives the "sent" message and the target side is
     * the only one allowed to accept or deny. Every member of both
     * teams is registered in the pending state.</p>
     *
     * <p>The request is rejected when a member of either team is
     * already fighting, has another pending request, or is on cooldown.
     * The self-duel case is also rejected. A cancellable
     * {@link DuelRequestEvent} is fired before the request is stored,
     * so external plugins can veto it.</p>
     *
     * @param teamA  first team; must not be empty
     * @param teamB  second team; must not be empty
     * @param kitId  optional kit id, or {@code null}
     */
    public void sendRequest(List<Player> teamA, List<Player> teamB, String kitId) {
        if (teamA.isEmpty() || teamB.isEmpty()) return;

        Set<UUID> teamAIds = new HashSet<>();
        for (Player p : teamA) teamAIds.add(p.getUniqueId());
        for (Player p : teamB) {
            if (teamAIds.contains(p.getUniqueId())) return;
        }

        Player sender = teamA.get(0);
        Player target = teamB.get(0);

        for (Player p : teamA) {
            if (isInDuel(p.getUniqueId())) {
                plugin.messages().send(sender, "duel.sender-busy");
                return;
            }
        }
        for (Player p : teamB) {
            if (isInDuel(p.getUniqueId())) {
                plugin.messages().send(sender, "duel.target-busy");
                return;
            }
        }
        for (Player p : teamA) {
            if (hasPending(p.getUniqueId())) {
                plugin.messages().send(sender, "duel.sender-busy");
                return;
            }
        }
        for (Player p : teamB) {
            if (hasPending(p.getUniqueId())) {
                plugin.messages().send(sender, "duel.target-busy");
                return;
            }
        }
        if (hasCooldown(sender.getUniqueId())) {
            plugin.messages().send(sender, "duel.cooldown",
                    Map.of("seconds", String.valueOf(getRemainingCooldown(sender.getUniqueId()))));
            return;
        }
        if (!plugin.worlds().isReady()) {
            plugin.messages().send(sender, "arena.no-world");
            return;
        }

        String resolvedKitId = kitId;
        if (kitId != null) {
            var kit = plugin.kits().resolve(kitId);
            if (kit == null) {
                plugin.messages().send(sender, "kit.not-found", Map.of("kit", kitId));
                return;
            }
            if (!sender.hasPermission(kit.getPermission())) {
                plugin.messages().send(sender, "kit.no-permission");
                return;
            }
            resolvedKitId = kit.getId();
        }

        DuelRequestEvent event = new DuelRequestEvent(sender, target, resolvedKitId);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        Duration timeout = Duration.ofSeconds(plugin.config().requestTimeoutSeconds());
        DuelRequest request = new DuelRequest(
                teamA.stream().map(Player::getUniqueId).toList(),
                teamB.stream().map(Player::getUniqueId).toList(),
                resolvedKitId, timeout
        );

        for (Player p : teamA) outgoing.put(p.getUniqueId(), request);
        for (Player p : teamB) incoming.put(p.getUniqueId(), request);
        applyCooldown(sender.getUniqueId());

        if (resolvedKitId != null) {
            plugin.messages().send(sender, "duel.request-sent", Map.of(
                    "target", target.getName(),
                    "kit", resolvedKitId
            ));
            for (Player p : teamB) {
                plugin.messages().send(p, "duel.request-received", Map.of(
                        "sender", sender.getName(),
                        "kit", resolvedKitId
                ));
            }
        } else {
            plugin.messages().send(sender, "duel.request-sent-no-kit", Map.of(
                    "target", target.getName()
            ));
            for (Player p : teamB) {
                plugin.messages().send(p, "duel.request-received-no-kit", Map.of(
                        "sender", sender.getName()
                ));
            }
        }

        Log.debug("Duel request: %s -> %s (size=%d/%d, kit=%s)",
                sender.getName(), target.getName(), teamA.size(), teamB.size(), resolvedKitId);
    }

    /**
     * Accepts the incoming request of a player and starts the match.
     *
     * <p>Only the representative of the target team may accept. If any
     * participant went offline, or no free arena is available, the
     * request is cleared and the acceptor is notified. A cancellable
     * {@link DuelStartEvent} is fired after the arena has been assigned
     * but before players are teleported, so external plugins can still
     * abort the match.</p>
     *
     * @param target the player accepting the request
     */
    public void accept(Player target) {
        DuelRequest request = incoming.get(target.getUniqueId());
        if (request == null) {
            plugin.messages().send(target, "duel.no-incoming");
            return;
        }
        if (!target.getUniqueId().equals(request.getTarget())) {
            plugin.messages().send(target, "duel.no-incoming");
            return;
        }

        List<Player> teamA = resolvePlayers(request.getTeamA());
        List<Player> teamB = resolvePlayers(request.getTeamB());
        if (teamA == null || teamB == null) {
            clearRequest(request);
            plugin.messages().send(target, "duel.no-incoming");
            return;
        }

        Arena arena = plugin.arenas().assign();
        if (arena == null) {
            clearRequest(request);
            plugin.messages().send(teamA.get(0), "arena.no-arena-available");
            plugin.messages().send(target, "arena.no-arena-available");
            return;
        }

        clearRequest(request);

        Map<UUID, Location> returnLocations = new HashMap<>();
        for (Player p : teamA) {
            plugin.snapshots().capture(p);
            returnLocations.put(p.getUniqueId(), p.getLocation().clone());
        }
        for (Player p : teamB) {
            plugin.snapshots().capture(p);
            returnLocations.put(p.getUniqueId(), p.getLocation().clone());
        }

        DuelSession session = new DuelSession(
                request.getTeamA(), request.getTeamB(),
                request.getKitId(), arena, returnLocations
        );

        DuelStartEvent event = new DuelStartEvent(session, teamA.get(0), teamB.get(0));
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            plugin.arenas().release(arena);
            for (Player p : teamA) plugin.snapshots().restore(p);
            for (Player p : teamB) plugin.snapshots().restore(p);
            for (Player p : teamA) plugin.messages().send(p, "duel.ended-cancelled");
            for (Player p : teamB) plugin.messages().send(p, "duel.ended-cancelled");
            return;
        }

        teleportTeam(teamA, arena.getEffectiveTeamSpawns(1));
        teleportTeam(teamB, arena.getEffectiveTeamSpawns(2));

        Set<UUID> alive = new HashSet<>();
        for (Player p : teamA) {
            preparePlayer(p);
            sessions.put(p.getUniqueId(), session);
            alive.add(p.getUniqueId());
        }
        for (Player p : teamB) {
            preparePlayer(p);
            sessions.put(p.getUniqueId(), session);
            alive.add(p.getUniqueId());
        }
        alivePlayers.put(session.getLeaderA(), alive);

        applyKit(session, teamA, teamB);

        plugin.messages().send(teamA.get(0), "duel.accepted-sender",
                Map.of("target", teamB.get(0).getName()));
        plugin.messages().send(teamB.get(0), "duel.accepted-target");

        Log.debug("Duel started: %d vs %d on arena '%s'",
                teamA.size(), teamB.size(), arena.getId());

        startCountdown(session);
    }

    // ------------------------------------------------------------
    // Party matches
    // ------------------------------------------------------------

    /**
     * Starts a party free-for-all.
     *
     * @param party the party
     * @param kitId optional kit id, or {@code null}
     */
    public void startPartyFfa(Party party, String kitId) {
        List<Player> players = collectOnlineParty(party);
        if (players.size() < 2) return;
        startFfa(players, kitId);
    }

    /**
     * Starts a party split match, dividing online members into two
     * balanced teams in random order.
     *
     * @param party the party
     * @param kitId optional kit id, or {@code null}
     */
    public void startPartySplit(Party party, String kitId) {
        List<Player> players = collectOnlineParty(party);
        if (players.size() < 2) return;

        Collections.shuffle(players);

        List<Player> teamA = new ArrayList<>();
        List<Player> teamB = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            if (i % 2 == 0) teamA.add(players.get(i));
            else teamB.add(players.get(i));
        }

        startTeam(teamA, teamB, kitId);
    }

    /**
     * Starts a free-for-all match with the given players.
     *
     * @param players participants; must contain at least two entries
     * @param kitId   optional kit id, or {@code null}
     */
    public void startFfa(List<Player> players, String kitId) {
        if (players.size() < 2) return;

        String resolvedKitId = resolveKit(players.get(0), kitId);
        if (kitId != null && resolvedKitId == null) return;

        Arena arena = plugin.arenas().assign();
        if (arena == null) {
            for (Player p : players) plugin.messages().send(p, "arena.no-arena-available");
            return;
        }

        Map<UUID, Location> returnLocations = new HashMap<>();
        for (Player p : players) {
            plugin.snapshots().capture(p);
            returnLocations.put(p.getUniqueId(), p.getLocation().clone());
        }

        List<UUID> uuids = players.stream().map(Player::getUniqueId).toList();
        DuelSession session = new DuelSession(uuids, resolvedKitId, arena, returnLocations);

        DuelStartEvent event = new DuelStartEvent(session, players.get(0), players.get(1));
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            plugin.arenas().release(arena);
            for (Player p : players) plugin.snapshots().restore(p);
            for (Player p : players) plugin.messages().send(p, "duel.ended-cancelled");
            return;
        }

        List<Location> spawns = combinedTeamSpawns(arena);
        teleportTeam(players, spawns);

        Set<UUID> alive = new HashSet<>();
        for (Player p : players) {
            preparePlayer(p);
            sessions.put(p.getUniqueId(), session);
            alive.add(p.getUniqueId());
        }
        alivePlayers.put(session.getLeaderA(), alive);

        applyKitToPlayers(session, players);

        for (Player p : players) plugin.messages().send(p, "duel.ffa-started");

        Log.debug("FFA started: %d players on arena '%s'", players.size(), arena.getId());

        startCountdown(session);
    }

    /**
     * Starts a team-vs-team match.
     *
     * @param teamA first team; must not be empty
     * @param teamB second team; must not be empty
     * @param kitId optional kit id, or {@code null}
     */
    public void startTeam(List<Player> teamA, List<Player> teamB, String kitId) {
        if (teamA.isEmpty() || teamB.isEmpty()) return;

        String resolvedKitId = resolveKit(teamA.get(0), kitId);
        if (kitId != null && resolvedKitId == null) return;

        Arena arena = plugin.arenas().assign();
        if (arena == null) {
            for (Player p : teamA) plugin.messages().send(p, "arena.no-arena-available");
            for (Player p : teamB) plugin.messages().send(p, "arena.no-arena-available");
            return;
        }

        Map<UUID, Location> returnLocations = new HashMap<>();
        List<Player> all = new ArrayList<>(teamA);
        all.addAll(teamB);
        for (Player p : all) {
            plugin.snapshots().capture(p);
            returnLocations.put(p.getUniqueId(), p.getLocation().clone());
        }

        DuelSession session = new DuelSession(
                teamA.stream().map(Player::getUniqueId).toList(),
                teamB.stream().map(Player::getUniqueId).toList(),
                resolvedKitId, arena, returnLocations);

        DuelStartEvent event = new DuelStartEvent(session, teamA.get(0), teamB.get(0));
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            plugin.arenas().release(arena);
            for (Player p : all) plugin.snapshots().restore(p);
            for (Player p : all) plugin.messages().send(p, "duel.ended-cancelled");
            return;
        }

        teleportTeam(teamA, arena.getEffectiveTeamSpawns(1));
        teleportTeam(teamB, arena.getEffectiveTeamSpawns(2));

        Set<UUID> alive = new HashSet<>();
        for (Player p : all) {
            preparePlayer(p);
            sessions.put(p.getUniqueId(), session);
            alive.add(p.getUniqueId());
        }
        alivePlayers.put(session.getLeaderA(), alive);

        applyKitToPlayers(session, all);

        Log.debug("Team match started: %d vs %d on arena '%s'",
                teamA.size(), teamB.size(), arena.getId());

        startCountdown(session);
    }

    /**
     * Returns the online members of a party.
     *
     * @param party the party
     * @return list of online players
     */
    private List<Player> collectOnlineParty(Party party) {
        List<Player> out = new ArrayList<>();
        for (UUID uuid : party.getMembers().keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) out.add(p);
        }
        return out;
    }

    /**
     * Resolves a kit id and checks the sender's permission.
     *
     * @param sender the player requesting the kit
     * @param kitId  requested kit id, or {@code null}
     * @return canonical kit id, or {@code null} if the kit does not
     *         exist or the sender lacks permission
     */
    private @Nullable String resolveKit(Player sender, String kitId) {
        if (kitId == null) return null;
        var kit = plugin.kits().resolve(kitId);
        if (kit == null) {
            plugin.messages().send(sender, "kit.not-found", Map.of("kit", kitId));
            return null;
        }
        if (!sender.hasPermission(kit.getPermission())) {
            plugin.messages().send(sender, "kit.no-permission");
            return null;
        }
        return kit.getId();
    }

    /**
     * Concatenates the effective spawns of both teams of an arena.
     *
     * @param arena the arena
     * @return list of spawns, team 1 first
     */
    private List<Location> combinedTeamSpawns(Arena arena) {
        List<Location> out = new ArrayList<>();
        out.addAll(arena.getEffectiveTeamSpawns(1));
        out.addAll(arena.getEffectiveTeamSpawns(2));
        return out;
    }

    /**
     * Applies the session kit to a list of players.
     *
     * @param session the session providing the kit id
     * @param players players receiving the kit
     */
    private void applyKitToPlayers(DuelSession session, List<Player> players) {
        if (session.getKitId() == null) return;
        var baseKit = plugin.kits().get(session.getKitId());
        if (baseKit == null) return;
        for (Player p : players) {
            plugin.playerKits().resolveForPlayer(p.getUniqueId(), baseKit).apply(p);
        }
    }

    /**
     * Resolves a list of uuids to online players, returning
     * {@code null} if any of them is offline.
     *
     * @param uuids uuids to resolve
     * @return list of players, or {@code null}
     */
    private @Nullable List<Player> resolvePlayers(List<UUID> uuids) {
        List<Player> out = new ArrayList<>(uuids.size());
        for (UUID uuid : uuids) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) return null;
            out.add(p);
        }
        return out;
    }

    /**
     * Teleports every player of a team to a spawn, cycling through the
     * spawn list when there are fewer spawns than players.
     *
     * @param team   players to teleport
     * @param spawns spawn locations, possibly empty
     */
    private void teleportTeam(List<Player> team, List<Location> spawns) {
        if (spawns.isEmpty()) return;
        for (int i = 0; i < team.size(); i++) {
            Location loc = spawns.get(i % spawns.size());
            Player p = team.get(i);
            p.teleport(loc);
            p.setFallDistance(0f);
        }
    }

    /**
     * Resets the transient state of a player entering a duel.
     *
     * @param p the player
     */
    private void preparePlayer(Player p) {
        p.setGameMode(GameMode.SURVIVAL);
        p.setFireTicks(0);
        p.clearActivePotionEffects();
    }

    /**
     * Applies the session kit to two teams.
     *
     * @param session the session providing the kit id
     * @param teamA   first team
     * @param teamB   second team
     */
    private void applyKit(DuelSession session, List<Player> teamA, List<Player> teamB) {
        if (session.getKitId() == null) return;
        var baseKit = plugin.kits().get(session.getKitId());
        if (baseKit == null) return;
        for (Player p : teamA) {
            plugin.playerKits().resolveForPlayer(p.getUniqueId(), baseKit).apply(p);
        }
        for (Player p : teamB) {
            plugin.playerKits().resolveForPlayer(p.getUniqueId(), baseKit).apply(p);
        }
    }

    // ------------------------------------------------------------
    // Countdown
    // ------------------------------------------------------------

    /**
     * Starts the pre-fight countdown for a session.
     *
     * @param session the session
     */
    private void startCountdown(DuelSession session) {
        int seconds = plugin.config().countdownSeconds();

        if (seconds <= 0) {
            activateSession(session);
            return;
        }

        sendCountdown(session, seconds);

        new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (sessions.get(session.getLeaderA()) != session) {
                    cancel();
                    return;
                }
                remaining--;
                if (remaining <= 0) {
                    activateSession(session);
                    cancel();
                    return;
                }
                sendCountdown(session, remaining);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Sends the countdown title and sound to every session participant.
     *
     * @param session   the session
     * @param remaining remaining seconds to display
     */
    private void sendCountdown(DuelSession session, int remaining) {
        Component titleComp = MiniMessage.miniMessage().deserialize(
                "<gold><bold>" + remaining + "</bold></gold>");
        Title.Times times = Title.Times.times(Duration.ZERO,
                Duration.ofMillis(1200), Duration.ofMillis(200));
        Title title = Title.title(titleComp, Component.empty(), times);
        for (UUID uuid : session.getAllPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.6f);
        }
    }

    /**
     * Transitions a session to the active phase and sends the "FIGHT"
     * title to every participant.
     *
     * @param session the session
     */
    private void activateSession(DuelSession session) {
        if (sessions.get(session.getLeaderA()) != session) return;
        session.setPhase(DuelPhase.ACTIVE);

        Component fightComp = MiniMessage.miniMessage().deserialize("<red><bold>FIGHT!");
        Title.Times times = Title.Times.times(Duration.ZERO,
                Duration.ofMillis(800), Duration.ofMillis(200));
        Title fight = Title.title(fightComp, Component.empty(), times);

        for (UUID uuid : session.getAllPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.showTitle(fight);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
            plugin.messages().send(p, "duel.started");
        }

        Log.debug("Duel active: %d vs %d",
                session.getTeamA().size(), session.getTeamB().size());
    }

    // ------------------------------------------------------------
    // Deny / forfeit
    // ------------------------------------------------------------

    /**
     * Denies an incoming request.
     *
     * @param target the denying player
     */
    public void deny(Player target) {
        DuelRequest request = incoming.get(target.getUniqueId());
        if (request == null) {
            plugin.messages().send(target, "duel.no-incoming");
            return;
        }
        clearRequest(request);

        Player sender = Bukkit.getPlayer(request.getSender());
        if (sender != null && sender.isOnline()) {
            plugin.messages().send(sender, "duel.denied-sender",
                    Map.of("target", target.getName()));
        }
        plugin.messages().send(target, "duel.denied-target");
    }

    /**
     * Makes a player forfeit the current match.
     *
     * <p>The player is removed from the alive set. If this ends the
     * match, the session is closed with {@link DuelEndReason#FORFEIT}.
     * In FFA mode a message is sent when the match continues; in team
     * mode the match simply goes on.</p>
     *
     * @param player the forfeiting player
     */
    public void forfeit(Player player) {
        DuelSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            plugin.messages().send(player, "duel.not-in-duel");
            return;
        }

        Set<UUID> alive = alivePlayers.get(session.getLeaderA());
        if (alive == null) return;
        alive.remove(player.getUniqueId());

        List<UUID> winningUuids = session.winnersIfEnded(alive);
        if (winningUuids.isEmpty()) {
            if (session.isFfaMode()) {
                plugin.messages().send(player, "duel.ffa-eliminated");
            }
            return;
        }

        List<Player> winners = new ArrayList<>();
        List<Player> losers = new ArrayList<>();
        Set<UUID> winningSet = new HashSet<>(winningUuids);
        for (UUID uuid : session.getAllPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            if (winningSet.contains(uuid)) winners.add(p);
            else losers.add(p);
        }
        endSession(session, winners, losers, DuelEndReason.FORFEIT, true);
    }

    // ------------------------------------------------------------
    // Death handling
    // ------------------------------------------------------------

    /**
     * Handles a duelist's death.
     *
     * <p>The deceased is removed from the alive set; if the match
     * continues the call is a no-op, otherwise the session is closed
     * with {@link DuelEndReason#PLAYER_DIED}.</p>
     *
     * @param deceased the player who died
     */
    public void handleDeath(Player deceased) {
        DuelSession session = sessions.get(deceased.getUniqueId());
        if (session == null) return;

        Set<UUID> alive = alivePlayers.get(session.getLeaderA());
        if (alive == null) return;
        alive.remove(deceased.getUniqueId());

        List<UUID> winningUuids = session.winnersIfEnded(alive);
        if (winningUuids.isEmpty()) {
            Log.debug("Player %s died but match continues", deceased.getName());
            return;
        }

        List<Player> winners = new ArrayList<>();
        List<Player> losers = new ArrayList<>();
        Set<UUID> winningSet = new HashSet<>(winningUuids);
        for (UUID uuid : session.getAllPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            if (winningSet.contains(uuid)) winners.add(p);
            else losers.add(p);
        }

        endSession(session, winners, losers, DuelEndReason.PLAYER_DIED, true);
    }

    // ------------------------------------------------------------
    // Session end
    // ------------------------------------------------------------

    /**
     * Ends a session and, if requested, performs the post-match
     * cleanup: reward and defeat messages, snapshot restoration and
     * return teleports.
     *
     * <p>The guard {@code sessions.get(session.getLeaderA()) != session}
     * makes the method idempotent, so calling it twice on the same
     * session has no effect. The {@link DuelEndEvent} is fired before
     * cleanup, and the arena is released before the event, so listeners
     * cannot observe an arena still marked as occupied.</p>
     *
     * @param session the session to end
     * @param winners winning players, possibly empty
     * @param losers  losing players, possibly empty
     * @param reason  end reason
     * @param cleanup whether to run the post-match cleanup
     */
    public void endSession(DuelSession session, List<Player> winners, List<Player> losers,
                           DuelEndReason reason, boolean cleanup) {
        if (sessions.get(session.getLeaderA()) != session) return;

        for (UUID uuid : session.getAllPlayers()) {
            sessions.remove(uuid);
        }
        alivePlayers.remove(session.getLeaderA());

        if (session.getArena() != null) {
            plugin.arenas().release(session.getArena());
        }

        plugin.spectators().cleanup(session);

        Bukkit.getPluginManager().callEvent(
                new DuelEndEvent(session, winners, losers, reason));

        if (cleanup) {
            for (Player w : winners) {
                plugin.messages().send(w, "duel.ended-win");
                w.playSound(w.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
            for (Player l : losers) {
                plugin.messages().send(l, "duel.ended-loss");
            }

            if (winners.isEmpty() && (reason == DuelEndReason.CANCELLED
                    || reason == DuelEndReason.DRAW)) {
                for (UUID uuid : session.getAllPlayers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) plugin.messages().send(p, "duel.ended-cancelled");
                }
            }

            if (reason == DuelEndReason.PLAYER_DIED) {
                for (Player w : winners) {
                    if (plugin.snapshots().hasPending(w.getUniqueId())) {
                        plugin.snapshots().scheduleRestore(w, 4L);
                    }
                }
            } else {
                for (UUID uuid : session.getAllPlayers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && plugin.snapshots().hasPending(uuid)) {
                        plugin.snapshots().scheduleRestore(p, 4L);
                    }
                }
            }

            for (UUID uuid : session.getAllPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                Location returnLoc = session.getReturnLocation(uuid);
                if (returnLoc == null) continue;
                if (!plugin.snapshots().hasPending(uuid)) {
                    Bukkit.getScheduler().runTaskLater(plugin,
                            () -> p.teleport(returnLoc), 2L);
                }
            }
        }

        Log.debug("Duel ended (%s): winners=%d losers=%d",
                reason, winners.size(), losers.size());
    }

    /**
     * Convenience overload that ends a session with a single winner.
     *
     * @param session the session to end
     * @param winner  the winning player, or {@code null}
     * @param reason  end reason
     */
    public void endSession(DuelSession session, @Nullable Player winner, DuelEndReason reason) {
        List<Player> winners = winner == null ? List.of() : List.of(winner);
        List<Player> losers = new ArrayList<>();
        if (winner != null) {
            UUID loserId = session.opponentsOf(winner.getUniqueId()).stream()
                    .findFirst().orElse(null);
            if (loserId != null) {
                Player l = Bukkit.getPlayer(loserId);
                if (l != null) losers.add(l);
            }
        }
        endSession(session, winners, losers, reason, true);
    }

    // ------------------------------------------------------------
    // Request helpers
    // ------------------------------------------------------------

    /**
     * Expires every request whose timeout has elapsed.
     *
     * <p>Duplicate entries produced by team requests are collapsed
     * using object identity, so each request is processed once and
     * the participants receive at most one expiry notification per
     * side.</p>
     */
    private void tick() {
        if (outgoing.isEmpty()) return;

        Set<DuelRequest> processed = new HashSet<>();
        for (DuelRequest req : new ArrayList<>(outgoing.values())) {
            if (!processed.add(req)) continue;
            if (!req.isExpired()) continue;

            clearRequest(req);

            Player s = Bukkit.getPlayer(req.getSender());
            Player t = Bukkit.getPlayer(req.getTarget());
            if (s != null && s.isOnline()) {
                plugin.messages().send(s, "duel.request-expired-sender",
                        Map.of("target", t != null ? t.getName() : "?"));
            }
            if (t != null && t.isOnline()) {
                plugin.messages().send(t, "duel.request-expired-target",
                        Map.of("sender", s != null ? s.getName() : "?"));
            }
        }
    }

    /**
     * Removes every entry of a request from both the outgoing and
     * incoming maps.
     *
     * @param request the request to clear
     */
    private void clearRequest(DuelRequest request) {
        for (UUID uuid : request.getTeamA()) outgoing.remove(uuid);
        for (UUID uuid : request.getTeamB()) incoming.remove(uuid);
    }

    /**
     * Handles a player disconnecting.
     *
     * <p>Any pending request in which the player is involved is cleared
     * for every participant on both sides. If the player was in an
     * active match, they are removed from the alive set; if this ends
     * the match, the session is closed with
     * {@link DuelEndReason#PLAYER_QUIT}.</p>
     *
     * @param player the disconnecting player
     */
    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();

        DuelRequest out = outgoing.remove(uuid);
        if (out != null) {
            clearRequest(out);
            for (UUID other : out.getTeamB()) {
                Player p = Bukkit.getPlayer(other);
                if (p != null && p.isOnline()) {
                    plugin.messages().send(p, "duel.request-cancelled");
                }
            }
        }

        DuelRequest in = incoming.remove(uuid);
        if (in != null) {
            clearRequest(in);
            for (UUID other : in.getTeamA()) {
                Player p = Bukkit.getPlayer(other);
                if (p != null && p.isOnline()) {
                    plugin.messages().send(p, "duel.request-cancelled");
                }
            }
        }

        DuelSession session = sessions.get(uuid);
        if (session == null) return;

        Set<UUID> alive = alivePlayers.get(session.getLeaderA());
        if (alive == null) return;
        alive.remove(uuid);

        List<UUID> winningUuids = session.winnersIfEnded(alive);
        if (winningUuids.isEmpty()) return;

        List<Player> winners = new ArrayList<>();
        List<Player> losers = new ArrayList<>();
        Set<UUID> winningSet = new HashSet<>(winningUuids);
        for (UUID other : session.getAllPlayers()) {
            Player p = Bukkit.getPlayer(other);
            if (p == null) continue;
            if (winningSet.contains(other)) winners.add(p);
            else losers.add(p);
        }
        endSession(session, winners, losers, DuelEndReason.PLAYER_QUIT, true);
    }
}