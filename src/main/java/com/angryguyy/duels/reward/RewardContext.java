package com.angryguyy.duels.reward;

import com.angryguyy.duels.duel.DuelEndReason;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Immutable context passed to every {@link Reward} when it is granted.
 *
 * <p>In addition to the raw values, the context exposes a
 * {@link #resolve(String)} helper that replaces the supported
 * placeholders inside a template string. The following placeholders
 * are recognized:</p>
 * <ul>
 *     <li>{@code %winner%} — winner name</li>
 *     <li>{@code %loser%} — loser name, or {@code -} if offline</li>
 *     <li>{@code %arena%} — arena id, or {@code -}</li>
 *     <li>{@code %kit%} — kit id, or {@code -}</li>
 *     <li>{@code %reason%} — lowercase end reason</li>
 *     <li>{@code %duration%} — duel duration in seconds</li>
 * </ul>
 */
public final class RewardContext {

    private final Player winner;
    private final @Nullable Player loser;
    private final @Nullable String arenaId;
    private final @Nullable String kitId;
    private final DuelEndReason reason;
    private final long durationSeconds;

    /**
     * Creates a new reward context.
     *
     * @param winner          the winning player, must be online
     * @param loser           the losing player, or {@code null}
     * @param arenaId         arena id, or {@code null}
     * @param kitId           kit id, or {@code null}
     * @param reason          reason the duel ended
     * @param durationSeconds duel duration in seconds
     */
    public RewardContext(Player winner,
                         @Nullable Player loser,
                         @Nullable String arenaId,
                         @Nullable String kitId,
                         DuelEndReason reason,
                         long durationSeconds) {
        this.winner = winner;
        this.loser = loser;
        this.arenaId = arenaId;
        this.kitId = kitId;
        this.reason = reason;
        this.durationSeconds = durationSeconds;
    }

    /**
     * Returns the winning player.
     *
     * @return winner
     */
    public Player getWinner() {
        return winner;
    }

    /**
     * Returns the losing player, if still online.
     *
     * @return loser, or {@code null}
     */
    public @Nullable Player getLoser() {
        return loser;
    }

    /**
     * Returns the arena id.
     *
     * @return arena id, or {@code null}
     */
    public @Nullable String getArenaId() {
        return arenaId;
    }

    /**
     * Returns the kit id.
     *
     * @return kit id, or {@code null}
     */
    public @Nullable String getKitId() {
        return kitId;
    }

    /**
     * Returns the end reason.
     *
     * @return end reason
     */
    public DuelEndReason getReason() {
        return reason;
    }

    /**
     * Returns the duel duration in seconds.
     *
     * @return duration
     */
    public long getDurationSeconds() {
        return durationSeconds;
    }

    /**
     * Replaces the supported placeholders inside a template.
     *
     * <p>Missing values are replaced with a single dash so that
     * resulting strings remain readable in logs and broadcasts.</p>
     *
     * @param template template string, possibly {@code null}
     * @return the resolved string, or {@code null} if the template was
     *         {@code null}
     */
    public String resolve(String template) {
        if (template == null) return null;
        return template
                .replace("%winner%", winner.getName())
                .replace("%loser%", loser != null ? loser.getName() : "-")
                .replace("%arena%", arenaId != null ? arenaId : "-")
                .replace("%kit%", kitId != null ? kitId : "-")
                .replace("%reason%", reason.name().toLowerCase(Locale.ROOT))
                .replace("%duration%", String.valueOf(durationSeconds));
    }
}