package com.angryguyy.duels.duel;

import com.angryguyy.duels.arena.Arena;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mutable runtime state of an active duel.
 *
 * <p>Three modes are supported:</p>
 * <ul>
 *     <li><b>1v1</b>: each team contains a single player.</li>
 *     <li><b>Team</b>: each team contains several players, typically
 *     every member of a party.</li>
 *     <li><b>FFA</b>: every player is their own team. The match ends
 *     when a single player is left alive.</li>
 * </ul>
 *
 * <p>In 1v1 and team modes, {@link #teamOf(UUID)} returns 1 or 2. In
 * FFA mode it returns a unique one-based index identifying the player
 * inside {@link #getFfaPlayers()}. This keeps the calling code uniform:
 * two players on the same team share the same index, while in FFA every
 * player has a distinct one.</p>
 *
 * <p>Every mutable field except the phase is fixed at construction. The
 * phase is advanced exactly once by the {@link DuelManager} when the
 * countdown completes.</p>
 *
 * <p>The class is not thread-safe; access is expected on the main
 * thread only.</p>
 */
public final class DuelSession {

    private final List<UUID> teamA;
    private final List<UUID> teamB;
    private final boolean ffaMode;
    private final List<UUID> ffaPlayers;
    private final @Nullable String kitId;
    private final @Nullable Arena arena;
    private final Map<UUID, Location> returnLocations;
    private final Instant startedAt;
    private DuelPhase phase = DuelPhase.COUNTDOWN;

    /**
     * Creates a 1v1 or team session.
     *
     * @param teamA           uuids of the first team; must not be empty
     * @param teamB           uuids of the second team; must not be empty
     * @param kitId           optional kit identifier, or {@code null}
     * @param arena           assigned arena, or {@code null}
     * @param returnLocations map of return location per player
     */
    public DuelSession(List<UUID> teamA, List<UUID> teamB,
                       @Nullable String kitId,
                       @Nullable Arena arena,
                       Map<UUID, Location> returnLocations) {
        this.teamA = List.copyOf(teamA);
        this.teamB = List.copyOf(teamB);
        this.ffaMode = false;
        this.ffaPlayers = List.of();
        this.kitId = kitId;
        this.arena = arena;
        this.returnLocations = Map.copyOf(returnLocations);
        this.startedAt = Instant.now();
    }

    /**
     * Creates a free-for-all session.
     *
     * <p>Every player is their own team. Internally the players are
     * stored as {@code teamA} so that generic iteration keeps working;
     * the {@code ffaPlayers} list preserves their individual identity
     * for the {@link #teamOf(UUID)} lookup.</p>
     *
     * @param players         every participant; must contain at least two
     * @param kitId           optional kit identifier, or {@code null}
     * @param arena           assigned arena, or {@code null}
     * @param returnLocations map of return location per player
     */
    public DuelSession(List<UUID> players,
                       @Nullable String kitId,
                       @Nullable Arena arena,
                       Map<UUID, Location> returnLocations) {
        this.teamA = List.copyOf(players);
        this.teamB = List.of();
        this.ffaMode = true;
        this.ffaPlayers = List.copyOf(players);
        this.kitId = kitId;
        this.arena = arena;
        this.returnLocations = Map.copyOf(returnLocations);
        this.startedAt = Instant.now();
    }

    /**
     * Convenience constructor for a classic 1v1.
     *
     * @param playerA         uuid of the first duelist
     * @param playerB         uuid of the second duelist
     * @param kitId           optional kit identifier, or {@code null}
     * @param arena           assigned arena, or {@code null}
     * @param returnA         return location for player A, or {@code null}
     * @param returnB         return location for player B, or {@code null}
     */
    public DuelSession(UUID playerA, UUID playerB, @Nullable String kitId,
                       @Nullable Arena arena,
                       @Nullable Location returnA, @Nullable Location returnB) {
        this(List.of(playerA), List.of(playerB), kitId, arena,
                buildReturnMap(playerA, returnA, playerB, returnB));
    }

    /**
     * Builds a return-locations map from two optional entries, skipping
     * any pair whose location is {@code null}.
     *
     * @param a  uuid of the first player
     * @param la return location for player A, or {@code null}
     * @param b  uuid of the second player
     * @param lb return location for player B, or {@code null}
     * @return map of return locations, possibly empty
     */
    private static Map<UUID, Location> buildReturnMap(UUID a, @Nullable Location la,
                                                      UUID b, @Nullable Location lb) {
        Map<UUID, Location> map = new HashMap<>();
        if (la != null) map.put(a, la);
        if (lb != null) map.put(b, lb);
        return map;
    }

    /**
     * Returns an unmodifiable view of the first team.
     *
     * @return team A
     */
    public List<UUID> getTeamA() {
        return teamA;
    }

    /**
     * Returns an unmodifiable view of the second team.
     *
     * @return team B
     */
    public List<UUID> getTeamB() {
        return teamB;
    }

    /**
     * Returns whether the session is a free-for-all.
     *
     * @return {@code true} for FFA
     */
    public boolean isFfaMode() {
        return ffaMode;
    }

    /**
     * Returns the FFA player list, in the order they were assigned.
     *
     * @return list of players, empty for non-FFA sessions
     */
    public List<UUID> getFfaPlayers() {
        return ffaPlayers;
    }

    /**
     * Returns every player taking part in the duel.
     *
     * @return team A followed by team B, or every FFA player
     */
    public List<UUID> getAllPlayers() {
        List<UUID> out = new ArrayList<>(teamA.size() + teamB.size());
        out.addAll(teamA);
        out.addAll(teamB);
        return Collections.unmodifiableList(out);
    }

    /**
     * Returns the first element of team A, used as representative.
     *
     * @return team A representative uuid
     */
    public UUID getLeaderA() {
        return teamA.get(0);
    }

    /**
     * Returns the representative of team B, if any.
     *
     * @return team B leader uuid, or {@code null} for FFA
     */
    public @Nullable UUID getLeaderB() {
        return teamB.isEmpty() ? null : teamB.get(0);
    }

    /**
     * Returns the optional kit identifier chosen for this duel.
     *
     * @return kit id, or {@code null}
     */
    public @Nullable String getKitId() {
        return kitId;
    }

    /**
     * Returns the arena assigned to this duel.
     *
     * @return arena, or {@code null}
     */
    public @Nullable Arena getArena() {
        return arena;
    }

    /**
     * Returns the return location registered for a player, if any.
     *
     * @param uuid player uuid
     * @return location, or {@code null}
     */
    public @Nullable Location getReturnLocation(UUID uuid) {
        return returnLocations.get(uuid);
    }

    /**
     * Returns the full return-locations map.
     *
     * @return unmodifiable map of return locations
     */
    public Map<UUID, Location> getReturnLocations() {
        return returnLocations;
    }

    /**
     * Returns the instant the session was created.
     *
     * @return creation timestamp
     */
    public Instant getStartedAt() {
        return startedAt;
    }

    /**
     * Returns the current phase of the duel.
     *
     * @return current phase
     */
    public DuelPhase getPhase() {
        return phase;
    }

    /**
     * Updates the phase of the duel.
     *
     * @param phase new phase
     */
    public void setPhase(DuelPhase phase) {
        this.phase = phase;
    }

    /**
     * Checks whether the given player is part of this session.
     *
     * @param uuid uuid to test
     * @return {@code true} if the uuid belongs to either team
     */
    public boolean contains(UUID uuid) {
        return teamA.contains(uuid) || teamB.contains(uuid);
    }

    /**
     * Returns the team index of a player.
     *
     * @param uuid player uuid
     * @return 1 or 2 for non-FFA, a unique 1-based index for FFA,
     *         {@code 0} if the player is not part of the session
     */
    public int teamOf(UUID uuid) {
        if (ffaMode) {
            int idx = ffaPlayers.indexOf(uuid);
            return idx < 0 ? 0 : idx + 1;
        }
        if (teamA.contains(uuid)) return 1;
        if (teamB.contains(uuid)) return 2;
        return 0;
    }

    /**
     * Returns the teammates of a player, including the player itself.
     *
     * @param uuid player uuid
     * @return list of teammates, or an empty list
     */
    public List<UUID> teammatesOf(UUID uuid) {
        if (ffaMode) {
            return ffaPlayers.contains(uuid) ? List.of(uuid) : List.of();
        }
        if (teamA.contains(uuid)) return teamA;
        if (teamB.contains(uuid)) return teamB;
        return List.of();
    }

    /**
     * Returns the opponents of a player.
     *
     * @param uuid player uuid
     * @return list of opponents, or an empty list
     */
    public List<UUID> opponentsOf(UUID uuid) {
        if (ffaMode) {
            if (!ffaPlayers.contains(uuid)) return List.of();
            List<UUID> out = new ArrayList<>();
            for (UUID other : ffaPlayers) {
                if (!other.equals(uuid)) out.add(other);
            }
            return out;
        }
        if (teamA.contains(uuid)) return teamB;
        if (teamB.contains(uuid)) return teamA;
        return List.of();
    }

    /**
     * Returns the winners of the match given the set of alive players.
     *
     * <p>In 1v1 and team modes, a team wins when the opposing team has
     * no alive members. In FFA mode, a player wins when they are the
     * only one left alive. Returns an empty list if the match should
     * continue.</p>
     *
     * @param alive set of uuids currently alive
     * @return list of winning player uuids, or an empty list
     */
    public List<UUID> winnersIfEnded(Set<UUID> alive) {
        if (ffaMode) {
            List<UUID> surviving = new ArrayList<>();
            for (UUID uuid : ffaPlayers) {
                if (alive.contains(uuid)) surviving.add(uuid);
            }
            return surviving.size() == 1 ? surviving : List.of();
        }
        boolean aAlive = teamA.stream().anyMatch(alive::contains);
        boolean bAlive = teamB.stream().anyMatch(alive::contains);
        if (aAlive && !bAlive) return teamA;
        if (bAlive && !aAlive) return teamB;
        return List.of();
    }
}