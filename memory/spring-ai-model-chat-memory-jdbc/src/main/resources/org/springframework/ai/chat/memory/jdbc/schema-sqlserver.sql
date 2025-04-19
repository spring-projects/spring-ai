CREATE TABLE ai_chat_memory (
    conversation_id VARCHAR(36) NOT NULL,
    content NVARCHAR(MAX) NOT NULL,
    type VARCHAR(10) NOT NULL,
    [timestamp] DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT type_check CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL'))
);

CREATE INDEX ai_chat_memory_conversation_id_timestamp_idx ON ai_chat_memory(conversation_id, [timestamp] DESC);