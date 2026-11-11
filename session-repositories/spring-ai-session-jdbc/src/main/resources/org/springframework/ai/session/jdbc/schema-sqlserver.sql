-- SQL Server has no CREATE TABLE IF NOT EXISTS, so each table is guarded by OBJECT_ID and
-- its indexes are declared inline, to keep the script safe to re-run
-- (spring.ai.session.repository.jdbc.initialize-schema=always). Requires SQL Server 2016+.
--
-- Keys are case-sensitive, as on the other databases, not compared with the server's
-- case-insensitive default collation. SQL Server still ignores trailing spaces when it
-- compares them. They are NVARCHAR because the driver sends strings as Unicode by
-- default, so lookups use the indexes without a conversion.
IF OBJECT_ID(N'AI_SESSION', N'U') IS NULL
CREATE TABLE AI_SESSION (
    id            NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
    created_at    DATETIME2(6)  NOT NULL,
    expires_at    DATETIME2(6),
    metadata      NVARCHAR(MAX) NOT NULL,
    event_version BIGINT        NOT NULL DEFAULT 0,
    INDEX idx_ai_session_expires_at (expires_at)
);

-- Indexed metadata attributes: one row per session and indexed attribute name (by
-- default the principal name). findByIndex(name, value) is one query over this table.
-- The keys are NONCLUSTERED: wider than the 900 bytes of a clustered key, within the
-- 1700 bytes of a nonclustered one.
IF OBJECT_ID(N'AI_SESSION_INDEX', N'U') IS NULL
CREATE TABLE AI_SESSION_INDEX (
    session_id    NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL,
    attr_name     NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL,
    attr_value    NVARCHAR(512) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT pk_ai_session_index PRIMARY KEY NONCLUSTERED (session_id, attr_name),
    INDEX idx_ai_session_index_name_value NONCLUSTERED (attr_name, attr_value),
    CONSTRAINT fk_ai_session_index_session
        FOREIGN KEY (session_id) REFERENCES AI_SESSION (id) ON DELETE CASCADE
);

IF OBJECT_ID(N'AI_SESSION_EVENT', N'U') IS NULL
CREATE TABLE AI_SESSION_EVENT (
    seq             BIGINT        IDENTITY(1, 1) NOT NULL,
    id              NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
    session_id      NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [timestamp]     DATETIME2(6)  NOT NULL,
    -- Sized for EventType.MAX_NAME_LENGTH
    event_type      NVARCHAR(64)  NOT NULL DEFAULT 'MESSAGE',
    -- NULL for an event that is not a message
    message_type    NVARCHAR(20),
    message_content NVARCHAR(MAX),
    -- Message parts and metadata, or the payload of an event that is not a message
    payload         NVARCHAR(MAX) NOT NULL,
    synthetic       BIT           NOT NULL DEFAULT 0,
    metadata        NVARCHAR(MAX) NOT NULL,
    CONSTRAINT uq_ai_session_event_seq UNIQUE (seq),
    INDEX idx_ai_session_event_session_seq (session_id, seq),
    -- The newest compaction boundary of a session, on every read of the active view
    INDEX idx_ai_session_event_session_type_seq (session_id, event_type, seq),
    CONSTRAINT fk_ai_session_event_session
        FOREIGN KEY (session_id) REFERENCES AI_SESSION (id) ON DELETE CASCADE
);
