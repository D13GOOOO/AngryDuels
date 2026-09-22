package com.angryguyy.duels.duel;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable value object describing a pending duel request.
 *
 * <p>A request is created when a player challenges another and is
 * stored in the {@link DuelManager} both as an outgoing entry for the
 * sender and an incoming entry for the target. It carries the optional
 * kit identifier that will be used if the duel is accepted, along with
 * the instant it was created and the configured timeout used to detect
 * expiry.</p>
 *
 * <p>Instances are thread-safe for reads since all fields are final
 * and immutable, but they are only ever accessed from the main thread
 * in practice.</p>
 */
public final class DuelRequest {

    private final UUID sender;
    private final UUID target;
    private final @Nullable String kitId;
    private final Instant createdAt;
    private final Duration timeout;

    /**
     * Creates a new request and records its creation time as the
     * current instant.
     *
     * @param sender  unique id of the player sending the request
     * @param target  unique id of the player receiving the request
     * @param kitId   optional kit identifier, or {@code null}
     * @param timeout duration after which the request is considered
     *                expired
     */
    public DuelRequest(UUID sender, UUID target, @Nullable String kitId, Duration timeout) {
        this.sender = sender;
        this.target = target;
        this.kitId = kitId;
        this.createdAt = Instant.now();
        this.timeout = timeout;
    }

    /**
     * Returns the unique id of the sender.
     *
     * @return sender uuid
     */
    public UUID getSender() {
        return sender;
    }

    /**
     * Returns the unique id of the target.
     *
     * @return target uuid
     */
    public UUID getTarget() {
        return target;
    }

    /**
     * Returns the optional kit identifier.
     *
     * @return kit id, or {@code null} if the duel has no preset kit
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
     * @return {@code true} if the current time is past
     *         {@code createdAt + timeout}
     */
    public boolean isExpired() {
        return Instant.now().isAfter(createdAt.plus(timeout));
    }
}