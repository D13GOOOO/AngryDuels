package com.angryguyy.duels.duel;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable value object describing a pending duel request.
 *
 * <p>A request always carries two teams. In a 1v1 each team has a
 * single element; in a party match each team holds every member of the
 * corresponding party at the moment the invitation was sent.</p>
 *
 * <p>The request is stored in the {@link DuelManager} as one outgoing
 * entry per member of team A and one incoming entry per member of team
 * B, so any member can see the pending state. The accept action is
 * performed by a single player, identified by convention as the first
 * element of team B.</p>
 */
public final class DuelRequest {

    private final List<UUID> teamA;
    private final List<UUID> teamB;
    private final @Nullable String kitId;
    private final Instant createdAt;
    private final Duration timeout;

    /**
     * Creates a new request and records its creation time as the
     * current instant.
     *
     * @param teamA   uuids of the first team; must not be empty
     * @param teamB   uuids of the second team; must not be empty
     * @param kitId   optional kit identifier, or {@code null}
     * @param timeout duration after which the request is considered
     *                expired
     */
    public DuelRequest(List<UUID> teamA, List<UUID> teamB,
                       @Nullable String kitId, Duration timeout) {
        this.teamA = List.copyOf(teamA);
        this.teamB = List.copyOf(teamB);
        this.kitId = kitId;
        this.createdAt = Instant.now();
        this.timeout = timeout;
    }

    /**
     * Convenience constructor for a classic 1v1.
     *
     * @param sender  uuid of the sender
     * @param target  uuid of the target
     * @param kitId   optional kit identifier, or {@code null}
     * @param timeout request timeout
     */
    public DuelRequest(UUID sender, UUID target, @Nullable String kitId, Duration timeout) {
        this(List.of(sender), List.of(target), kitId, timeout);
    }

    /**
     * Returns the first team.
     *
     * @return team A
     */
    public List<UUID> getTeamA() {
        return teamA;
    }

    /**
     * Returns the second team.
     *
     * @return team B
     */
    public List<UUID> getTeamB() {
        return teamB;
    }

    /**
     * Returns the representative of the sender side, used as primary
     * recipient of the "sent" message.
     *
     * @return team A representative uuid
     */
    public UUID getSender() {
        return teamA.get(0);
    }

    /**
     * Returns the representative of the target side, used as the
     * player who can accept or deny the request.
     *
     * @return team B representative uuid
     */
    public UUID getTarget() {
        return teamB.get(0);
    }

    /**
     * Returns the optional kit identifier.
     *
     * @return kit id, or {@code null}
     */
    public @Nullable String getKitId() {
        return kitId;
    }

    /**
     * Returns the instant the request was created.
     *
     * @return creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the configured timeout duration.
     *
     * @return request timeout
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Checks whether the request has exceeded its timeout.
     *
     * @return {@code true} if the current time is past expiry
     */
    public boolean isExpired() {
        return Instant.now().isAfter(createdAt.plus(timeout));
    }
}