package com.angryguyy.duels.duel;

import com.angryguyy.duels.arena.Arena;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Mutable runtime state of an active duel.
 *
 * <p>An instance is created when a request is accepted and lives until
 * the duel ends. It binds together the two duelists, the optional kit
 * identifier, the assigned {@link Arena}, the positions each player
 * will be returned to, and the current {@link DuelPhase}.</p>
 *
 * <p>All fields except the phase are immutable. The phase is mutated
 * exactly once by the {@link DuelManager} when the countdown completes,
 * transitioning the session from {@link DuelPhase#COUNTDOWN} to
 * {@link DuelPhase#ACTIVE}.</p>
 *
 * <p>The class is not thread-safe; access is expected on the main
 * thread only.</p>
 */
public final class DuelSession {

    private final UUID playerA;
    private final UUID playerB;
    private final @Nullable String kitId;
    private final @Nullable Arena arena;
    private final @Nullable Location returnA;
    private final @Nullable Location returnB;
    private final Instant startedAt;
    private DuelPhase phase = DuelPhase.COUNTDOWN;

    /**
     * Creates a new session.
     *
     * @param playerA  uuid of the first duelist (the request sender)
     * @param playerB  uuid of the second duelist (the request target)
     * @param kitId    optional kit identifier, or {@code null}
     * @param arena    assigned arena, or {@code null} if none was
     *                 available
     * @param returnA  position to teleport the first duelist back to,
     *                 or {@code null}
     * @param returnB  position to teleport the second duelist back to,
     *                 or {@code null}
     */
    public DuelSession(UUID playerA, UUID playerB, @Nullable String kitId,
                       @Nullable Arena arena,
                       @Nullable Location returnA, @Nullable Location returnB) {
        this.playerA = playerA;
        this.playerB = playerB;
        this.kitId = kitId;
        this.arena = arena;
        this.returnA = returnA;
        this.returnB = returnB;
        this.startedAt = Instant.now();
    }

    /**
     * Returns the uuid of the first duelist.
     *
     * @return first duelist uuid
     */
    public UUID getPlayerA() {
        return playerA;
    }

    /**
     * Returns the uuid of the second duelist.
     *
     * @return second duelist uuid
     */
    public UUID getPlayerB() {
        return playerB;
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
     * Returns the location the first duelist will be returned to.
     *
     * @return return location for player A, or {@code null}
     */
    public @Nullable Location getReturnA() {
        return returnA;
    }

    /**
     * Returns the location the second duelist will be returned to.
     *
     * @return return location for player B, or {@code null}
     */
    public @Nullable Location getReturnB() {
        return returnB;
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
     * <p>This method is intended to be called only by the
     * {@link DuelManager} when the countdown completes.</p>
     *
     * @param phase new phase
     */
    public void setPhase(DuelPhase phase) {
        this.phase = phase;
    }

    /**
     * Checks whether the given player is one of the two duelists.
     *
     * @param uuid uuid to test
     * @return {@code true} if the uuid matches player A or player B
     */
    public boolean contains(UUID uuid) {
        return playerA.equals(uuid) || playerB.equals(uuid);
    }

    /**
     * Returns the opponent of the given player.
     *
     * @param uuid uuid of one of the two duelists
     * @return uuid of the other duelist, or {@code null} if the given
     *         uuid does not belong to this session
     */
    public @Nullable UUID opponentOf(UUID uuid) {
        if (playerA.equals(uuid)) return playerB;
        if (playerB.equals(uuid)) return playerA;
        return null;
    }
}