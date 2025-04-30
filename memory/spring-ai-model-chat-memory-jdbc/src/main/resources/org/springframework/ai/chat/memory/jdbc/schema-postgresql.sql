CREATE TABLE IF NOT EXISTS ai_chat_memory (
    conversation_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp" TIMESTAMP NOT NULL
    );

CREATE INDEX IF NOT EXISTS ai_chat_memory_conversation_id_timestamp_idx
ON ai_chat_memory(conversation_id, "timestamp");