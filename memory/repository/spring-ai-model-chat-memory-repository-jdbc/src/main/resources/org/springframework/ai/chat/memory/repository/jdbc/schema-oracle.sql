CREATE TABLE SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR2(36) NOT NULL,
    content CLOB NOT NULL,
    type VARCHAR2(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp" TIMESTAMP NOT NULL
);

CREATE INDEX idx_conversation_id ON SPRING_AI_CHAT_MEMORY(conversation_id);
CREATE INDEX idx_conversation_id_timestamp ON SPRING_AI_CHAT_MEMORY(conversation_id, "timestamp");