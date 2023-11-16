package org.springframework.ai.client;

import org.springframework.ai.chain.AiOutput;
import org.springframework.ai.memory.Memory;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Map;

public interface AiThing {

    String generate();

    String generate(Map<String, Object> parameters);

    static AiThing create(AiClient aiClient) {
        return (new DefaultAiThingBuilder()).aiClient(aiClient).build();
    }

    static AiThing.Builder builder() {
        return new DefaultAiThingBuilder();
    }

    AiThing promptTemplate(String promptTemplate);

    public interface Builder {
        Builder aiClient(AiClient aiClient);

        Builder promptTemplate(String promptTemplate);

        Builder vectorStore(VectorStore vectorStore);

        Builder vectorStoreKey(String vectorStoreKey);

        Builder conversationMemory(Memory memory);

        AiThing build();
    }

}
