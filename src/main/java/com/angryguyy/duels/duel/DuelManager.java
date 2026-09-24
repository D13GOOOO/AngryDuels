package com.angryguyy.duels.duel;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.arena.Arena;
import com.angryguyy.duels.event.DuelEndEvent;
import com.angryguyy.duels.event.DuelRequestEvent;
import com.angryguyy.duels.event.DuelStartEvent;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Central coordinator for every duel-related activity.
 *
 * <p>The manager holds all in-memory state needed to drive the duel
 * lifecycle: pending outgoing and incoming requests, active sessions,
 * and per-player request cooldowns. It is the only component allowed
 * to mutate that state; listeners, commands and future modules interact
 * with duels exclusively through the public methods declared here.</p>
 *
 * <p>The typical flow is:</p>
 * <ol>
 *     <li>{@link #sendRequest(Player, Player, String)} registers a new
 *     request and applies a cooldown to the sender.</li>
 *     <li>{@link #accept(Player)} or {@link #deny(Player)} resolves the
 *     request. Accepting assigns an arena and opens a
 *     {@link DuelSession}.</li>
 *     <li>{@link #startCountdown(DuelSession)} runs a pre-fight timer
 *     during which damage is blocked.</li>
 *     <li>{@link #endSession(DuelSession, Player, DuelEndReason, boolean)}
 *     tears the session down, fires the end event, releases the arena
 *     and returns both players to their original positions.</li>
 * </ol>
 *
 * <p>A single repeating task ({@link #tick()}) is scheduled on start to
 * expire stale requests. It is cancelled on {@link #shutdown()}.</p>
 *
 * <p>This class is not thread-safe and must only be used from the
 * Bukkit main thread. The maps are backed by {@link HashMap} and are
 * accessed without synchronization.</p>
 */
public class DuelManager {

    private final DuelsPlugin plugin;

    private final Map<UUID, DuelRequest> outgoing = new HashMap<>();
    private final Map<UUID, DuelRequest> incoming = new HashMap<>();
    private final Map<UUID, DuelSession> sessions = new HashMap<>();
    private final Map<UUID, Instant> cooldowns = new HashMap<>();

    private BukkitTask timeoutTask;

    /**
     * Creates a new duel manager.
     *
     * @param plugin owning plugin instance, used for config access,
     *               messaging, arena assignment and scheduling
     */
    public DuelManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the manager.
     *
     * <p>Schedules the repeating task that expires requests whose
     * timeout has elapsed. The task runs every second.</p>
     */
    public void start() {
        timeoutTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /**
     * Stops the manager and cleans up all in-flight state.
     *
     * <p>Any active session is terminated with
     * {@link DuelEndReason#PLUGIN_DISABLE}. The end event is fired so
     * that external modules can react, but player-facing messages and
     * teleports are skipped because the server is shutting down.</p>
     */
    public void shutdown() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
        for (DuelSession session : new ArrayList<>(sessions.values())) {
            endSession(session, null, DuelEndReason.PLUGIN_DISABLE, false);
        }
        sessions.clear();
        outgoing.clear();
        incoming.clear();
        cooldowns.clear();
    }

    /**
     * Returns the current state of a player with respect to duels.
     *
     * @param uuid unique id of the player
     * @return {@link DuelState#IN_DUEL} if the player is inside an
     *         active session, {@link DuelState#IN_REQUEST} if the
     *         player has a pending incoming or outgoing request,
     *         {@link DuelState#IDLE} otherwise
     */
    public DuelState getState(UUID uuid) {
        if (sessions.containsKey(uuid)) return DuelState.IN_DUEL;
        if (outgoing.containsKey(uuid) || incoming.containsKey(uuid)) return DuelState.IN_REQUEST;
        return DuelState.IDLE;
    }

    /**
     * Checks whether a player is currently inside an active duel.
     *
     * @param uuid unique id of the player
     * @return {@code true} if a session exists for the player
     */
    public boolean isInDuel(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    /**
     * Checks whether a player has any pending request, either outgoing
     * or incoming.
     *
     * @param uuid unique id of the player
     * @return {@code true} if the player has a pending request
     */
    public boolean hasPending(UUID uuid) {
        return outgoing.containsKey(uuid) || incoming.containsKey(uuid);
    }

    /**
     * Returns the active session of a player, if any.
     *
     * @param uuid unique id of the player
     * @return the session, or {@code null} if the player is not in a duel
     */
    public DuelSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    /**
     * Returns the outgoing request sent by a player, if any.
     *
     * @param uuid unique id of the sender
     * @return the outgoing request, or {@code null} if none
     */
    public DuelRequest getOutgoing(UUID uuid) {
        return outgoing.get(uuid);
    }

    /**
     * Returns the incoming request received by a player, if any.
     *
     * @param uuid unique id of the target
     * @return the incoming request, or {@code null} if none
     */
    public DuelRequest getIncoming(UUID uuid) {
        return incoming.get(uuid);
    }

    /**
     * Returns the number of seconds left before a player can send
     * another request.
     *
     * @param uuid unique id of the player
     * @return remaining cooldown in seconds, or {@code 0} if none
     */
    public long getRemainingCooldown(UUID uuid) {
        Instant until = cooldowns.get(uuid);
        if (until == null) return 0;
        return Math.max(0, Duration.between(Instant.now(), until).getSeconds());
    }

    /**
     * Checks whether a player is currently on request cooldown.
     *
     * @param uuid unique id of the player
     * @return {@code true} if the player cannot send a request yet
     */
    public boolean hasCooldown(UUID uuid) {
        return getRemainingCooldown(uuid) > 0;
    }

    /**
     * Applies the configured cooldown to a player.
     *
     * <p>If the configured cooldown is zero or negative, no cooldown
     * is registered.</p>
     *
     * @param uuid unique id of the player
     */
    private void applyCooldown(UUID uuid) {
        int cd = plugin.config().cooldownSeconds();
        if (cd > 0) {
            cooldowns.put(uuid, Instant.now().plusSeconds(cd));
        }
    }

    /**
     * Sends a duel request from one player to another.
     *
     * <p>Before registering the request, several preconditions are
     * checked in order and the appropriate error message is sent to
     * the sender when one fails: the two players must be different,
     * neither may be in an active duel, neither may have a pending
     * request, the sender must not be on cooldown, and the duel world
     * must be available.</p>
     *
     * <p>If a kit id is provided, it is resolved through the
     * {@code KitManager} and validated against the sender's permissions.
     * The canonical id is stored in the request, so later lookups do
     * not need to resolve aliases again.</p>
     *
     * <p>A {@link DuelRequestEvent} is fired before the request is
     * stored. If the event is cancelled by a listener, the request is
     * discarded and no cooldown is applied.</p>
     *
     * @param sender player sending the request
     * @param target player receiving the request
     * @param kitId  optional kit identifier or alias, or {@code null}
     */
    public void sendRequest(Player sender, Player target, String kitId) {
        UUID su = sender.getUniqueId();
        UUID tu = target.getUniqueId();

        if (su.equals(tu)) {
            plugin.messages().send(sender, "duel.self");
            return;
        }
        if (isInDuel(su) || isInDuel(tu)) {
            plugin.messages().send(sender, isInDuel(su) ? "duel.sender-busy" : "duel.target-busy");
            return;
        }
        if (hasPending(su) || hasPending(tu)) {
            plugin.messages().send(sender, hasPending(su) ? "duel.sender-busy" : "duel.target-busy");
            return;
        }
        if (hasCooldown(su)) {
            plugin.messages().send(sender, "duel.cooldown",
                    Map.of("seconds", String.valueOf(getRemainingCooldown(su))));
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
        DuelRequest request = new DuelRequest(su, tu, resolvedKitId, timeout);
        outgoing.put(su, request);
        incoming.put(tu, request);
        applyCooldown(su);

        if (resolvedKitId != null) {
            plugin.messages().send(sender, "duel.request-sent", Map.of(
                    "target", target.getName(),
                    "kit", resolvedKitId
            ));
            plugin.messages().send(target, "duel.request-received", Map.of(
                    "sender", sender.getName(),
                    "kit", resolvedKitId
            ));
        } else {
            plugin.messages().send(sender, "duel.request-sent-no-kit", Map.of(
                    "target", target.getName()
            ));
            plugin.messages().send(target, "duel.request-received-no-kit", Map.of(
                    "sender", sender.getName()
            ));
        }

        Log.debug("Duel request: %s -> %s (kit=%s)", sender.getName(), target.getName(), resolvedKitId);
    }

    /**
     * Accepts the pending incoming request of a player and starts the
     * duel.
     *
     * <p>The following steps are performed:</p>
     * <ol>
     *     <li>The incoming request is looked up and validated; if the
     *     original sender is offline, the request is discarded.</li>
     *     <li>An arena is assigned through the {@code ArenaManager}.
     *     If no arena is free, both players are notified and nothing
     *     else happens.</li>
     *     <li>Both players are snapshotted and their current locations
     *     are captured so they can be restored when the duel ends.</li>
     *     <li>A {@link DuelSession} is created and a
     *     {@link DuelStartEvent} is fired. If the event is cancelled,
     *     the arena is released, the snapshots are discarded, and the
     *     duel is aborted.</li>
     *     <li>Both players are teleported to the assigned spawns and
     *     switched to survival. The session is registered only after
     *     the teleport, so that the isolation listener does not block
     *     the initial move to the arena.</li>
     *     <li>The kit is applied and the countdown is started.</li>
     * </ol>
     *
     * @param target player accepting the incoming request
     */
    public void accept(Player target) {
        DuelRequest request = incoming.get(target.getUniqueId());
        if (request == null) {
            plugin.messages().send(target, "duel.no-incoming");
            return;
        }
        Player sender = Bukkit.getPlayer(request.getSender());
        if (sender == null || !sender.isOnline()) {
            clearRequest(request);
            plugin.messages().send(target, "duel.no-incoming");
            return;
        }

        Arena arena = plugin.arenas().assign();
        if (arena == null) {
            clearRequest(request);
            plugin.messages().send(sender, "arena.no-arena-available");
            plugin.messages().send(target, "arena.no-arena-available");
            return;
        }

        clearRequest(request);

        plugin.snapshots().capture(sender);
        plugin.snapshots().capture(target);

        Location returnA = sender.getLocation().clone();
        Location returnB = target.getLocation().clone();

        DuelSession session = new DuelSession(
                sender.getUniqueId(), target.getUniqueId(), request.getKitId(),
                arena, returnA, returnB
        );

        DuelStartEvent event = new DuelStartEvent(session, sender, target);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            plugin.arenas().release(arena);
            plugin.snapshots().restore(sender);
            plugin.snapshots().restore(target);
            plugin.messages().send(sender, "duel.ended-cancelled");
            plugin.messages().send(target, "duel.ended-cancelled");
            return;
        }

        teleportSafe(sender, arena.getSpawn1());
        teleportSafe(target, arena.getSpawn2());

        sender.setGameMode(GameMode.SURVIVAL);
        target.setGameMode(GameMode.SURVIVAL);
        sender.setFireTicks(0);
        target.setFireTicks(0);

        sessions.put(sender.getUniqueId(), session);
        sessions.put(target.getUniqueId(), session);

        if (session.getKitId() != null) {
            var kit = plugin.kits().get(session.getKitId());
            if (kit != null) {
                kit.apply(sender);
                kit.apply(target);
                Log.debug("Applied kit '%s' to %s and %s",
                        kit.getId(), sender.getName(), target.getName());
            }
        }

        plugin.messages().send(sender, "duel.accepted-sender", Map.of("target", target.getName()));
        plugin.messages().send(target, "duel.accepted-target");

        Log.debug("Duel started: %s vs %s on arena '%s'",
                sender.getName(), target.getName(), arena.getId());

        startCountdown(session);
    }

    /**
     * Runs the pre-duel countdown for a newly created session.
     *
     * <p>If the configured countdown duration is zero or negative, the
     * session is activated immediately. Otherwise a repeating task
     * shows a title and plays a sound every second. If the session is
     * removed from the manager while the countdown is running, the task
     * cancels itself.</p>
     *
     * @param session session entering the countdown phase
     */
    private void startCountdown(DuelSession session) {
        int seconds = plugin.config().countdownSeconds();

        Player a = Bukkit.getPlayer(session.getPlayerA());
        Player b = Bukkit.getPlayer(session.getPlayerB());

        if (seconds <= 0) {
            activateSession(session, a, b);
            return;
        }

        sendCountdown(a, b, seconds);

        new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (sessions.get(session.getPlayerA()) != session) {
                    cancel();
                    return;
                }
                remaining--;
                if (remaining <= 0) {
                    activateSession(session,
                            Bukkit.getPlayer(session.getPlayerA()),
                            Bukkit.getPlayer(session.getPlayerB()));
                    cancel();
                    return;
                }
                sendCountdown(
                        Bukkit.getPlayer(session.getPlayerA()),
                        Bukkit.getPlayer(session.getPlayerB()),
                        remaining
                );
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Shows a single countdown tick to both duelists.
     *
     * <p>Null players are silently skipped, which allows the method to
     * be called even if one of the two disconnected during the
     * countdown.</p>
     *
     * @param a         first duelist, possibly {@code null}
     * @param b         second duelist, possibly {@code null}
     * @param remaining remaining seconds shown in the title
     */
    private void sendCountdown(@Nullable Player a, @Nullable Player b, int remaining) {
        Component titleComp = MiniMessage.miniMessage().deserialize("<gold><bold>" + remaining + "</bold></gold>");
        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(200));
        Title title = Title.title(titleComp, Component.empty(), times);
        if (a != null) {
            a.showTitle(title);
            a.playSound(a.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.6f);
        }
        if (b != null) {
            b.showTitle(title);
            b.playSound(b.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.6f);
        }
    }

    /**
     * Transitions a session from {@link DuelPhase#COUNTDOWN} to
     * {@link DuelPhase#ACTIVE}.
     *
     * <p>If the session is no longer the active one registered in the
     * manager, the method returns silently. Otherwise it updates the
     * phase, shows the {@code FIGHT!} title and plays a Wither spawn
     * sound to both duelists.</p>
     *
     * @param session session to activate
     * @param a       first duelist, possibly {@code null}
     * @param b       second duelist, possibly {@code null}
     */
    private void activateSession(DuelSession session, @Nullable Player a, @Nullable Player b) {
        if (sessions.get(session.getPlayerA()) != session) return;
        session.setPhase(DuelPhase.ACTIVE);

        Component fightComp = MiniMessage.miniMessage().deserialize("<red><bold>FIGHT!");
        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(200));
        Title fight = Title.title(fightComp, Component.empty(), times);

        if (a != null) {
            a.showTitle(fight);
            a.playSound(a.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
            plugin.messages().send(a, "duel.started");
        }
        if (b != null) {
            b.showTitle(fight);
            b.playSound(b.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
            plugin.messages().send(b, "duel.started");
        }

        Log.debug("Duel active: %s vs %s", session.getPlayerA(), session.getPlayerB());
    }

    /**
     * Denies the pending incoming request of a player.
     *
     * @param target player denying the request
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
            plugin.messages().send(sender, "duel.denied-sender", Map.of("target", target.getName()));
        }
        plugin.messages().send(target, "duel.denied-target");
    }

    /**
     * Makes a player forfeit their active duel.
     *
     * <p>The opponent is declared winner and the session is ended with
     * {@link DuelEndReason#FORFEIT}. If the player is not currently in
     * a duel, an informational message is sent.</p>
     *
     * @param player player forfeiting the duel
     */
    public void forfeit(Player player) {
        DuelSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            plugin.messages().send(player, "duel.not-in-duel");
            return;
        }
        UUID opponentId = session.opponentOf(player.getUniqueId());
        Player opponent = opponentId != null ? Bukkit.getPlayer(opponentId) : null;
        endSession(session, opponent, DuelEndReason.FORFEIT, true);
    }

    /**
     * Ends a session and performs the related cleanup.
     *
     * <p>The method is idempotent: if the given session is no longer
     * the one registered for its first player, it returns immediately.
     * Otherwise it removes both players from the sessions map, releases
     * the arena, resolves the loser from the winner, fires a
     * {@link DuelEndEvent}, and — if {@code cleanup} is {@code true} —
     * sends the outcome messages and restores the players.</p>
     *
     * <p>When the end reason is {@link DuelEndReason#PLAYER_DIED},
     * snapshot restoration is delegated to the respawn listener, because
     * applying the snapshot before the vanilla respawn would be
     * overwritten by the server. For every other reason, restoration is
     * scheduled directly from this method.</p>
     *
     * <p>The {@code cleanup} flag exists so that shutdown can fire the
     * end event for external modules without performing player-facing
     * side effects, which would be unsafe or pointless during server
     * stop.</p>
     *
     * @param session session to end
     * @param winner  the winning player, or {@code null} if there is no
     *                winner (cancelled or drawn duel)
     * @param reason  reason the duel is ending
     * @param cleanup whether to send messages and restore players
     */
    public void endSession(DuelSession session, Player winner, DuelEndReason reason, boolean cleanup) {
        if (sessions.get(session.getPlayerA()) != session) return;

        sessions.remove(session.getPlayerA());
        sessions.remove(session.getPlayerB());

        if (session.getArena() != null) {
            plugin.arenas().release(session.getArena());
        }

        Player loser = null;
        if (winner != null) {
            UUID loserId = session.opponentOf(winner.getUniqueId());
            loser = loserId != null ? Bukkit.getPlayer(loserId) : null;
        }

        Bukkit.getPluginManager().callEvent(new DuelEndEvent(session, winner, loser, reason));

        if (cleanup) {
            if (winner != null) {
                plugin.messages().send(winner, "duel.ended-win");
                winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
            if (loser != null) plugin.messages().send(loser, "duel.ended-loss");

            if (winner == null && (reason == DuelEndReason.CANCELLED || reason == DuelEndReason.DRAW)) {
                Player a = Bukkit.getPlayer(session.getPlayerA());
                Player b = Bukkit.getPlayer(session.getPlayerB());
                if (a != null) plugin.messages().send(a, "duel.ended-cancelled");
                if (b != null) plugin.messages().send(b, "duel.ended-cancelled");
            }

            Player a = Bukkit.getPlayer(session.getPlayerA());
            Player b = Bukkit.getPlayer(session.getPlayerB());

            if (reason != DuelEndReason.PLAYER_DIED) {
                if (a != null && plugin.snapshots().hasPending(a.getUniqueId())) {
                    plugin.snapshots().scheduleRestore(a, 4L);
                }
                if (b != null && plugin.snapshots().hasPending(b.getUniqueId())) {
                    plugin.snapshots().scheduleRestore(b, 4L);
                }
            }

            if (a != null && session.getReturnA() != null
                    && !plugin.snapshots().hasPending(a.getUniqueId())) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> a.teleport(session.getReturnA()), 2L);
            }
            if (b != null && session.getReturnB() != null
                    && !plugin.snapshots().hasPending(b.getUniqueId())) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> b.teleport(session.getReturnB()), 2L);
            }
        }

        Log.debug("Duel ended (%s): winner=%s loser=%s",
                reason,
                winner != null ? winner.getName() : "-",
                loser != null ? loser.getName() : "-");
    }

    /**
     * Convenience overload of
     * {@link #endSession(DuelSession, Player, DuelEndReason, boolean)}
     * that always performs full cleanup.
     *
     * @param session session to end
     * @param winner  the winning player, or {@code null}
     * @param reason  reason the duel is ending
     */
    public void endSession(DuelSession session, Player winner, DuelEndReason reason) {
        endSession(session, winner, reason, true);
    }

    /**
     * Teleports a player to a location and resets their fall distance.
     *
     * <p>Resetting fall distance prevents accidental fall damage on the
     * first tick after the teleport, since the server may still carry
     * over the accumulated value from the previous position.</p>
     *
     * @param player player to teleport
     * @param loc    destination
     */
    private void teleportSafe(Player player, Location loc) {
        player.teleport(loc);
        player.setFallDistance(0f);
    }

    /**
     * Repeating task body that expires stale duel requests.
     *
     * <p>Iterates over a snapshot of the outgoing requests so that
     * removing entries during the loop is safe.</p>
     */
    private void tick() {
        for (DuelRequest req : new ArrayList<>(outgoing.values())) {
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
     * Removes a request from both the outgoing and incoming maps.
     *
     * @param request request to remove
     */
    private void clearRequest(DuelRequest request) {
        outgoing.remove(request.getSender());
        incoming.remove(request.getTarget());
    }

    /**
     * Handles a player disconnecting.
     *
     * <p>Any request the player had pending, in either direction, is
     * cancelled and the counterpart is notified. If the player was in
     * an active duel, the opponent is declared winner and the session
     * is ended with {@link DuelEndReason#PLAYER_QUIT}.</p>
     *
     * @param player player who disconnected
     */
    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();

        DuelRequest out = outgoing.remove(uuid);
        if (out != null) {
            incoming.remove(out.getTarget());
            Player target = Bukkit.getPlayer(out.getTarget());
            if (target != null && target.isOnline()) {
                plugin.messages().send(target, "duel.request-cancelled");
            }
        }

        DuelRequest in = incoming.remove(uuid);
        if (in != null) {
            outgoing.remove(in.getSender());
            Player sender = Bukkit.getPlayer(in.getSender());
            if (sender != null && sender.isOnline()) {
                plugin.messages().send(sender, "duel.request-cancelled");
            }
        }

        DuelSession session = sessions.get(uuid);
        if (session != null) {
            UUID opponentId = session.opponentOf(uuid);
            Player opponent = opponentId != null ? Bukkit.getPlayer(opponentId) : null;
            endSession(session, opponent, DuelEndReason.PLAYER_QUIT, true);
        }
    }
}