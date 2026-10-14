-- MySQL has no CREATE INDEX IF NOT EXISTS, so indexes are declared inline to keep the
-- script safe to re-run (spring.ai.session.repository.jdbc.initialize-schema=always).
CREATE TABLE IF NOT EXISTS AI_SESSION (
    id            VARCHAR(255)  NOT NULL PRIMARY KEY,
    created_at    DATETIME(6)   NOT NULL,
    expires_at    DATETIME(6),
    metadata      LONGTEXT,
    event_version BIGINT        NOT NULL DEFAULT 0,
    INDEX idx_ai_session_expires_at (expires_at)
);

-- Indexed metadata attributes: one row per session and indexed attribute name (by
-- default the principal name). findByIndex(name, value) is one query over this table.
CREATE TABLE IF NOT EXISTS AI_SESSION_INDEX (
    session_id    VARCHAR(255)  NOT NULL,
    attr_name     VARCHAR(255)  NOT NULL,
    attr_value    VARCHAR(512)  NOT NULL,
    PRIMARY KEY (session_id, attr_name),
    INDEX idx_ai_session_index_name_value (attr_name, attr_value),
    CONSTRAINT fk_ai_session_index_session
        FOREIGN KEY (session_id) REFERENCES AI_SESSION (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS AI_SESSION_EVENT (
    seq             BIGINT        NOT NULL AUTO_INCREMENT,
    id              VARCHAR(255)  NOT NULL PRIMARY KEY,
    session_id      VARCHAR(255)  NOT NULL,
    timestamp       DATETIME(6)   NOT NULL,
    event_type      VARCHAR(32)   NOT NULL DEFAULT 'MESSAGE',
    -- NULL for an event that is not a message
    message_type    VARCHAR(20),
    message_content LONGTEXT,
    -- Message parts and metadata, or the payload of an event that is not a message
    payload         LONGTEXT,
    synthetic       TINYINT(1)    NOT NULL DEFAULT 0,
    metadata        LONGTEXT,
    UNIQUE KEY uq_ai_session_event_seq (seq),
    INDEX idx_ai_session_event_session_seq (session_id, seq),
    CONSTRAINT fk_ai_session_event_session
        FOREIGN KEY (session_id) REFERENCES AI_SESSION (id) ON DELETE CASCADE
);
