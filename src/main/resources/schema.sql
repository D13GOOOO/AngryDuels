CREATE TABLE IF NOT EXISTS duels_players (
    uuid        CHAR(36)    NOT NULL,
    username    VARCHAR(16) NOT NULL,
    first_seen  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (uuid),
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS duels_stats (
    uuid          CHAR(36) NOT NULL,
    wins          INT      NOT NULL DEFAULT 0,
    losses        INT      NOT NULL DEFAULT 0,
    kills         INT      NOT NULL DEFAULT 0,
    deaths        INT      NOT NULL DEFAULT 0,
    forfeits      INT      NOT NULL DEFAULT 0,
    quits         INT      NOT NULL DEFAULT 0,
    streak        INT      NOT NULL DEFAULT 0,
    best_streak   INT      NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid),
    CONSTRAINT fk_stats_player FOREIGN KEY (uuid)
        REFERENCES duels_players (uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS duels_kit_stats (
    uuid      CHAR(36)    NOT NULL,
    kit_id    VARCHAR(32) NOT NULL,
    wins      INT         NOT NULL DEFAULT 0,
    losses    INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid, kit_id),
    CONSTRAINT fk_kit_player FOREIGN KEY (uuid)
        REFERENCES duels_players (uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS duels_history (
    id                BIGINT     NOT NULL AUTO_INCREMENT,
    winner_uuid       CHAR(36)   NOT NULL,
    loser_uuid        CHAR(36)   NULL,
    kit_id            VARCHAR(32) NULL,
    arena_id          VARCHAR(32) NULL,
    duration_seconds  INT        NOT NULL DEFAULT 0,
    reason            VARCHAR(32) NOT NULL,
    ended_at          TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_winner (winner_uuid),
    INDEX idx_loser (loser_uuid),
    INDEX idx_ended_at (ended_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;