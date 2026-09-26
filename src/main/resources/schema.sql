-- ================================================================
--  AngryDuels - database schema
--  Executed on startup. Every statement uses IF NOT EXISTS so the
--  script is idempotent and safe to run against an existing database.
-- ================================================================

CREATE TABLE IF NOT EXISTS duels_players (
    uuid        CHAR(36)    NOT NULL COMMENT 'Player UUID',
    username    VARCHAR(16) NOT NULL COMMENT 'Last known username',
    first_seen  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (uuid),
    INDEX idx_username (username)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS duels_stats (
    uuid          CHAR(36) NOT NULL COMMENT 'Player UUID',
    wins          INT      NOT NULL DEFAULT 0,
    losses        INT      NOT NULL DEFAULT 0,
    kills         INT      NOT NULL DEFAULT 0,
    deaths        INT      NOT NULL DEFAULT 0,
    forfeits      INT      NOT NULL DEFAULT 0,
    quits         INT      NOT NULL DEFAULT 0,
    streak        INT      NOT NULL DEFAULT 0 COMMENT 'Current consecutive win streak',
    best_streak   INT      NOT NULL DEFAULT 0 COMMENT 'Highest streak ever achieved',
    PRIMARY KEY (uuid),
    CONSTRAINT fk_stats_player FOREIGN KEY (uuid)
    REFERENCES duels_players (uuid) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS duels_kit_stats (
    uuid      CHAR(36)    NOT NULL COMMENT 'Player UUID',
    kit_id    VARCHAR(32) NOT NULL COMMENT 'Kit identifier',
    wins      INT         NOT NULL DEFAULT 0,
    losses    INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid, kit_id),
    CONSTRAINT fk_kit_player FOREIGN KEY (uuid)
    REFERENCES duels_players (uuid) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS duels_history (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    winner_uuid       CHAR(36)    NOT NULL COMMENT 'Winner UUID at the time of the match',
    loser_uuid        CHAR(36)    NULL     COMMENT 'Loser UUID, null when the opponent quit',
    kit_id            VARCHAR(32) NULL,
    arena_id          VARCHAR(32) NULL,
    duration_seconds  INT         NOT NULL DEFAULT 0,
    reason            VARCHAR(32) NOT NULL COMMENT 'DuelEndReason name',
    ended_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_winner (winner_uuid),
    INDEX idx_loser (loser_uuid),
    INDEX idx_ended_at (ended_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS duels_player_kits (
    uuid       CHAR(36)    NOT NULL COMMENT 'Player UUID',
    kit_id     VARCHAR(32) NOT NULL COMMENT 'Kit identifier',
    slot       INT         NOT NULL COMMENT 'Inventory slot index',
    item_data  BLOB        NOT NULL COMMENT 'ItemStack serialized as bytes',
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (uuid, kit_id, slot)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS duels_parties (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    leader_uuid CHAR(36)    NOT NULL COMMENT 'Party leader UUID',
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_leader (leader_uuid)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS duels_party_members (
    party_id  BIGINT   NOT NULL,
    uuid      CHAR(36) NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (party_id, uuid),
    INDEX idx_uuid (uuid),
    CONSTRAINT fk_party_members_party FOREIGN KEY (party_id)
    REFERENCES duels_parties (id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS duels_party_invites (
    party_id   BIGINT   NOT NULL,
    inviter    CHAR(36) NOT NULL,
    invitee    CHAR(36) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    PRIMARY KEY (party_id, invitee),
    INDEX idx_invitee (invitee),
    CONSTRAINT fk_party_invites_party FOREIGN KEY (party_id)
    REFERENCES duels_parties (id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;