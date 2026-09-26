package com.angryguyy.duels.party;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.event.PartyCreateEvent;
import com.angryguyy.duels.event.PartyDisbandEvent;
import com.angryguyy.duels.event.PartyJoinEvent;
import com.angryguyy.duels.event.PartyLeaveEvent;
import com.angryguyy.duels.stats.DatabaseManager;
import com.angryguyy.duels.util.Log;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages persistent parties and their invitations.
 *
 * <p>Parties and invitations live in three database tables. The manager
 * keeps a full in-memory cache of both, which is loaded on startup and
 * kept in sync with every mutation. Because party operations are
 * relatively rare, the cache is refreshed from the source of truth
 * (the database) only at startup; at runtime the cache is authoritative
 * and every change is mirrored asynchronously.</p>
 *
 * <p>All public methods must be invoked on the Bukkit main thread. The
 * database writes they trigger are asynchronous.</p>
 *
 * <p>A party is automatically disbanded when its last member leaves,
 * either voluntarily or via kick. The disband is persisted by deleting
 * the row in {@code duels_parties}, and the foreign key cascades clean
 * up members and invitations.</p>
 *
 * <p>The public flag is stored on the {@link Party} instance itself and
 * is not persisted. After a server restart every party reverts to
 * private; if persisted visibility is required, an {@code is_public}
 * column can be added to the parties table.</p>
 */
public class PartyManager {

    /**
     * Duration of a pending invitation, in seconds.
     */
    private static final int INVITE_TIMEOUT_SECONDS = 30;

    /**
     * Interval between invitation expiry checks, in ticks.
     */
    private static final long INVITE_SWEEP_INTERVAL_TICKS = 20L * 30L;

    /**
     * Owning plugin.
     */
    private final DuelsPlugin plugin;

    /**
     * Underlying database manager.
     */
    private final DatabaseManager database;

    /**
     * Parties by id.
     */
    private final Map<Long, Party> parties = new HashMap<>();

    /**
     * Mapping from player uuid to party id.
     */
    private final Map<UUID, Long> playerToParty = new HashMap<>();

    /**
     * Pending invitations keyed by invitee uuid.
     */
    private final Map<UUID, PartyInvite> invitesByInvitee = new HashMap<>();

    /**
     * Next party id to assign.
     */
    private final AtomicLong nextPartyId = new AtomicLong(1);

    /**
     * The scheduled invitation sweeper task, if running.
     */
    private BukkitTask sweepTask;

    /**
     * Creates a new party manager.
     *
     * @param plugin   owning plugin
     * @param database underlying database manager
     */
    public PartyManager(DuelsPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    /**
     * Loads all parties, members and pending invitations from the
     * database into memory.
     *
     * <p>Called once on startup. If the database is unavailable, the
     * manager stays empty and every public method becomes a no-op until
     * the plugin is restarted.</p>
     *
     * <p>The method is idempotent: any data already held in memory is
     * discarded before the reload, and the invitation sweeper is only
     * started when the load succeeds.</p>
     */
    public void load() {
        if (!database.isReady()) {
            Log.warn("Party system disabled: database not ready.");
            return;
        }

        parties.clear();
        playerToParty.clear();
        invitesByInvitee.clear();

        boolean success = false;
        try (Connection conn = database.getDataSource().getConnection()) {
            long maxId = 0;

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, leader_uuid, created_at FROM duels_parties");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    UUID leader = UUID.fromString(rs.getString("leader_uuid"));
                    Instant createdAt = rs.getTimestamp("created_at").toInstant();
                    parties.put(id, new Party(id, leader, createdAt));
                    if (id > maxId) maxId = id;
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT party_id, uuid, joined_at FROM duels_party_members");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long partyId = rs.getLong("party_id");
                    Party party = parties.get(partyId);
                    if (party == null) continue;
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    Instant joinedAt = rs.getTimestamp("joined_at").toInstant();
                    party.addMember(uuid, joinedAt);
                    playerToParty.put(uuid, partyId);
                }
            }

            Timestamp now = Timestamp.from(Instant.now());
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT party_id, inviter, invitee, expires_at "
                            + "FROM duels_party_invites WHERE expires_at > ?")) {
                ps.setTimestamp(1, now);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long partyId = rs.getLong("party_id");
                        UUID inviter = UUID.fromString(rs.getString("inviter"));
                        UUID invitee = UUID.fromString(rs.getString("invitee"));
                        Instant expiresAt = rs.getTimestamp("expires_at").toInstant();
                        invitesByInvitee.put(invitee,
                                new PartyInvite(partyId, inviter, invitee, expiresAt));
                    }
                }
            }

            nextPartyId.set(maxId + 1);
            success = true;
            Log.info("Loaded %d party(ies), %d pending invite(s).",
                    parties.size(), invitesByInvitee.size());
        } catch (SQLException e) {
            Log.error(e, "Failed to load parties from database");
        }

        if (success) {
            startInviteSweeper();
        }
    }

    /**
     * Stops the invitation sweeper and clears the caches.
     */
    public void shutdown() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        parties.clear();
        playerToParty.clear();
        invitesByInvitee.clear();
    }

    /**
     * Returns the party a player belongs to, if any.
     *
     * @param uuid player uuid
     * @return party, or {@code null}
     */
    public Party getParty(UUID uuid) {
        Long id = playerToParty.get(uuid);
        return id == null ? null : parties.get(id);
    }

    /**
     * Returns a party by its id.
     *
     * @param partyId party id
     * @return party, or {@code null}
     */
    public Party getPartyById(long partyId) {
        return parties.get(partyId);
    }

    /**
     * Checks whether a player is in a party.
     *
     * @param uuid player uuid
     * @return {@code true} if the player belongs to a party
     */
    public boolean isInParty(UUID uuid) {
        return playerToParty.containsKey(uuid);
    }

    /**
     * Returns the pending invitation for a player, if any.
     *
     * @param uuid player uuid
     * @return invitation, or {@code null}
     */
    public PartyInvite getInvite(UUID uuid) {
        return invitesByInvitee.get(uuid);
    }

    /**
     * Creates a new party with the given player as leader.
     *
     * @param leader the future leader; must not already be in a party
     * @return the new party, or {@code null} if creation failed
     */
    public Party create(Player leader) {
        UUID uuid = leader.getUniqueId();
        if (isInParty(uuid)) return null;

        long id = nextPartyId.getAndIncrement();
        Instant now = Instant.now();
        Party party = new Party(id, uuid, now);

        parties.put(id, party);
        playerToParty.put(uuid, id);

        Bukkit.getPluginManager().callEvent(new PartyCreateEvent(party, leader));

        if (database.isReady()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = database.getDataSource().getConnection()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO duels_parties (id, leader_uuid, created_at) "
                                    + "VALUES (?, ?, ?)")) {
                        ps.setLong(1, id);
                        ps.setString(2, uuid.toString());
                        ps.setTimestamp(3, Timestamp.from(now));
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO duels_party_members (party_id, uuid, joined_at) "
                                    + "VALUES (?, ?, ?)")) {
                        ps.setLong(1, id);
                        ps.setString(2, uuid.toString());
                        ps.setTimestamp(3, Timestamp.from(now));
                        ps.executeUpdate();
                    }
                } catch (SQLException e) {
                    Log.error(e, "Failed to persist party creation for %s", uuid);
                }
            });
        }

        return party;
    }

    /**
     * Sends an invitation from a party leader to another player.
     *
     * <p>The invitation replaces any previous pending invitation for
     * the same invitee. If the invitee is already in a party or the
     * inviter is not the party leader, the request fails.</p>
     *
     * @param inviter the party leader
     * @param invitee the invited player
     * @return the created invitation, or {@code null} if rejected
     */
    public PartyInvite invite(Player inviter, Player invitee) {
        Party party = getParty(inviter.getUniqueId());
        if (party == null) return null;
        if (!party.isLeader(inviter.getUniqueId())) return null;
        if (party.size() >= Party.MAX_MEMBERS) return null;
        if (isInParty(invitee.getUniqueId())) return null;

        Instant expiresAt = Instant.now().plus(INVITE_TIMEOUT_SECONDS, ChronoUnit.SECONDS);
        PartyInvite invite = new PartyInvite(
                party.getId(), inviter.getUniqueId(), invitee.getUniqueId(), expiresAt);

        invitesByInvitee.put(invitee.getUniqueId(), invite);

        if (database.isReady()) {
            UUID inviterId = inviter.getUniqueId();
            UUID inviteeId = invitee.getUniqueId();
            long partyId = party.getId();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = database.getDataSource().getConnection()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM duels_party_invites WHERE invitee = ?")) {
                        ps.setString(1, inviteeId.toString());
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO duels_party_invites (party_id, inviter, invitee, expires_at) "
                                    + "VALUES (?, ?, ?, ?)")) {
                        ps.setLong(1, partyId);
                        ps.setString(2, inviterId.toString());
                        ps.setString(3, inviteeId.toString());
                        ps.setTimestamp(4, Timestamp.from(expiresAt));
                        ps.executeUpdate();
                    }
                } catch (SQLException e) {
                    Log.error(e, "Failed to persist invite from %s to %s",
                            inviterId, inviteeId);
                }
            });
        }

        return invite;
    }

    /**
     * Accepts the pending invitation of a player.
     *
     * <p>The invitation is removed from the cache in every outcome,
     * including when the party no longer exists, when the player is
     * already in another party, or when the target party is full, so
     * that a stale entry never lingers.</p>
     *
     * @param invitee the invited player
     * @return the party the player joined, or {@code null} if the
     *         invitation is missing, expired or no longer usable
     */
    public Party acceptInvite(Player invitee) {
        UUID inviteeId = invitee.getUniqueId();
        PartyInvite invite = invitesByInvitee.get(inviteeId);
        if (invite == null || invite.isExpired()) {
            invitesByInvitee.remove(inviteeId);
            return null;
        }

        Party party = parties.get(invite.partyId());
        if (party == null) {
            invitesByInvitee.remove(inviteeId);
            return null;
        }
        if (isInParty(inviteeId)) {
            invitesByInvitee.remove(inviteeId);
            return null;
        }
        if (party.size() >= Party.MAX_MEMBERS) {
            invitesByInvitee.remove(inviteeId);
            return null;
        }

        invitesByInvitee.remove(inviteeId);

        Instant now = Instant.now();
        party.addMember(inviteeId, now);
        playerToParty.put(inviteeId, party.getId());

        Bukkit.getPluginManager().callEvent(
                new PartyJoinEvent(party, inviteeId, invite.inviter()));

        if (database.isReady()) {
            long partyId = party.getId();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = database.getDataSource().getConnection()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM duels_party_invites WHERE invitee = ?")) {
                        ps.setString(1, inviteeId.toString());
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO duels_party_members (party_id, uuid, joined_at) "
                                    + "VALUES (?, ?, ?)")) {
                        ps.setLong(1, partyId);
                        ps.setString(2, inviteeId.toString());
                        ps.setTimestamp(3, Timestamp.from(now));
                        ps.executeUpdate();
                    }
                } catch (SQLException e) {
                    Log.error(e, "Failed to persist party join for %s", inviteeId);
                }
            });
        }

        return party;
    }

    /**
     * Denies the pending invitation of a player.
     *
     * @param invitee the invited player
     * @return {@code true} if an invitation was removed
     */
    public boolean denyInvite(Player invitee) {
        UUID inviteeId = invitee.getUniqueId();
        PartyInvite removed = invitesByInvitee.remove(inviteeId);
        if (removed == null) return false;

        if (database.isReady()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = database.getDataSource().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "DELETE FROM duels_party_invites WHERE invitee = ?")) {
                    ps.setString(1, inviteeId.toString());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    Log.error(e, "Failed to deny invite for %s", inviteeId);
                }
            });
        }
        return true;
    }

    /**
     * Kicks a member out of a party.
     *
     * @param leader the party leader
     * @param target uuid of the member to kick
     * @return {@code true} if the member was removed
     */
    public boolean kick(Player leader, UUID target) {
        Party party = getParty(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) return false;
        if (!party.isMember(target) || party.isLeader(target)) return false;

        party.removeMember(target);
        playerToParty.remove(target);

        Bukkit.getPluginManager().callEvent(
                new PartyLeaveEvent(party, target, PartyLeaveEvent.Reason.KICKED));

        persistMemberRemoval(party, target);
        return true;
    }

    /**
     * Promotes a member to leader.
     *
     * @param leader the current leader
     * @param target uuid of the member to promote
     * @return {@code true} if the promotion happened
     */
    public boolean promote(Player leader, UUID target) {
        Party party = getParty(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) return false;
        if (!party.isMember(target) || party.isLeader(target)) return false;

        party.setLeader(target);
        persistLeader(party, target);

        return true;
    }

    /**
     * Makes a player leave their party.
     *
     * <p>If the leaving player is the leader and other members remain,
     * the oldest member is promoted. If the player was the last member,
     * the party is disbanded.</p>
     *
     * @param player the leaving player
     * @return the party after the leave, or {@code null} if it was
     *         disbanded
     */
    public Party leave(Player player) {
        Party party = getParty(player.getUniqueId());
        if (party == null) return null;

        UUID uuid = player.getUniqueId();
        UUID promoted = party.removeMember(uuid);
        playerToParty.remove(uuid);

        if (party.size() == 0) {
            disbandInternal(party, PartyLeaveEvent.Reason.LEFT_LAST);
            return null;
        }

        if (promoted != null) {
            Bukkit.getPluginManager().callEvent(
                    new PartyLeaveEvent(party, uuid,
                            PartyLeaveEvent.Reason.LEFT_PROMOTED));
            persistLeader(party, promoted);
        } else {
            Bukkit.getPluginManager().callEvent(
                    new PartyLeaveEvent(party, uuid,
                            PartyLeaveEvent.Reason.LEFT));
        }

        persistMemberRemoval(party, uuid);
        return party;
    }

    /**
     * Disbands a party explicitly.
     *
     * @param leader the party leader
     * @return {@code true} if the party was disbanded
     */
    public boolean disband(Player leader) {
        Party party = getParty(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) return false;

        disbandInternal(party, PartyLeaveEvent.Reason.DISBANDED);
        return true;
    }

    /**
     * Adds a player to a public party.
     *
     * <p>The party must still be active and public, must not be full,
     * and the player must not already be in a party. The join is
     * persisted asynchronously.</p>
     *
     * @param player the joining player
     * @param party  the party to join
     * @return {@code true} if the join succeeded
     */
    public boolean joinPublic(Player player, Party party) {
        if (party == null) return false;
        if (parties.get(party.getId()) != party) return false;
        if (!party.isPublic()) return false;

        UUID uuid = player.getUniqueId();
        if (isInParty(uuid)) return false;
        if (party.size() >= Party.MAX_MEMBERS) return false;

        Instant now = Instant.now();
        party.addMember(uuid, now);
        playerToParty.put(uuid, party.getId());

        Bukkit.getPluginManager().callEvent(
                new PartyJoinEvent(party, uuid, party.getLeader()));

        if (database.isReady()) {
            long partyId = party.getId();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = database.getDataSource().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "INSERT INTO duels_party_members (party_id, uuid, joined_at) "
                                     + "VALUES (?, ?, ?)")) {
                    ps.setLong(1, partyId);
                    ps.setString(2, uuid.toString());
                    ps.setTimestamp(3, Timestamp.from(now));
                    ps.executeUpdate();
                } catch (SQLException e) {
                    Log.error(e, "Failed to persist public join for %s", uuid);
                }
            });
        }
        return true;
    }

    /**
     * Returns a snapshot of every active party.
     *
     * @return list of parties
     */
    public List<Party> getAllParties() {
        return new ArrayList<>(parties.values());
    }

    /**
     * Disbands a party internally, clearing its members and pending
     * invitations from the cache and removing the party itself.
     *
     * <p>The database row is deleted asynchronously; the cascading
     * foreign keys on {@code duels_party_members} and
     * {@code duels_party_invites} clean up the related rows in the
     * database without an explicit statement.</p>
     *
     * @param party  the party to disband
     * @param reason reason passed to the fired event
     */
    private void disbandInternal(Party party, PartyLeaveEvent.Reason reason) {
        long partyId = party.getId();
        for (UUID member : party.getMembers().keySet()) {
            playerToParty.remove(member);
        }
        invitesByInvitee.values().removeIf(invite -> invite.partyId() == partyId);
        parties.remove(partyId);

        Bukkit.getPluginManager().callEvent(new PartyDisbandEvent(party, reason));

        if (database.isReady()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = database.getDataSource().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "DELETE FROM duels_parties WHERE id = ?")) {
                    ps.setLong(1, partyId);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    Log.error(e, "Failed to disband party %d", partyId);
                }
            });
        }
    }

    /**
     * Persists the removal of a member from a party.
     *
     * @param party the party the member belonged to
     * @param uuid  uuid of the removed member
     */
    private void persistMemberRemoval(Party party, UUID uuid) {
        if (!database.isReady()) return;
        long partyId = party.getId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = database.getDataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM duels_party_members WHERE party_id = ? AND uuid = ?")) {
                ps.setLong(1, partyId);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                Log.error(e, "Failed to remove member %s from party %d", uuid, partyId);
            }
        });
    }

    /**
     * Persists the new leader of a party.
     *
     * <p>The new leader uuid is captured by the caller before the
     * asynchronous task is scheduled, so the write always uses the
     * value observed on the main thread.</p>
     *
     * @param party     the party whose leader changed
     * @param newLeader uuid of the new leader
     */
    private void persistLeader(Party party, UUID newLeader) {
        if (!database.isReady()) return;
        long partyId = party.getId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = database.getDataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE duels_parties SET leader_uuid = ? WHERE id = ?")) {
                ps.setString(1, newLeader.toString());
                ps.setLong(2, partyId);
                ps.executeUpdate();
            } catch (SQLException e) {
                Log.error(e, "Failed to update leader of party %d", partyId);
            }
        });
    }

    /**
     * Starts the periodic sweeper that removes expired invitations
     * from the in-memory cache.
     *
     * <p>The sweeper runs on the main thread so that the cache is only
     * touched from a single thread; the database purge is delegated to
     * an asynchronous task.</p>
     */
    private void startInviteSweeper() {
        sweepTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::sweepExpiredInvites,
                INVITE_SWEEP_INTERVAL_TICKS, INVITE_SWEEP_INTERVAL_TICKS);
    }

    /**
     * Removes expired invitations from the cache and asynchronously
     * purges them from the database.
     *
     * <p>The cache mutation happens on the main thread, so the call is
     * safe with respect to concurrent updates performed by commands.
     * The database delete uses a timestamp captured on the main thread,
     * so new invitations inserted after this point are not affected.</p>
     */
    private void sweepExpiredInvites() {
        Instant now = Instant.now();
        boolean removed = invitesByInvitee.values()
                .removeIf(invite -> invite.expiresAt().isBefore(now));

        if (!removed || !database.isReady()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = database.getDataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM duels_party_invites WHERE expires_at < ?")) {
                ps.setTimestamp(1, Timestamp.from(now));
                ps.executeUpdate();
            } catch (SQLException e) {
                Log.error(e, "Failed to purge expired party invites");
            }
        });
    }
}