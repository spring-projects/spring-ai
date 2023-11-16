package org.springframework.ai.client;

import org.springframework.ai.memory.Memory;
import org.springframework.ai.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.VectorStore;

public class DefaultAiThingBuilder implements AiThing.Builder {

    private AiClient aiClient;

    private PromptTemplate promptTemplate;

    private VectorStore vectorStore;

    private String vectorStoreKey = "documents";

    private Memory memory;

    public DefaultAiThingBuilder() {}

    @Override
    public AiThing.Builder aiClient(AiClient aiClient) {
        this.aiClient = aiClient;
        return this;
    }

    @Override
    public AiThing.Builder promptTemplate(String promptTemplate) {
        this.promptTemplate = new PromptTemplate(promptTemplate);
        return this;
    }

    @Override
    public AiThing.Builder vectorStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        return this;
    }

    @Override
    public AiThing.Builder vectorStoreKey(String vectorStoreKey) {
        this.vectorStoreKey = vectorStoreKey;
        return this;
    }

    public AiThing.Builder conversationMemory(Memory memory) {
        this.memory = memory;
        return this;
    }

    public AiThing build() {
        DefaultAiThing aiThing = new DefaultAiThing(aiClient, promptTemplate);
        aiThing.vectorStore(vectorStore);
        aiThing.vectorStoreKey(vectorStoreKey);
        aiThing.conversationMemory(memory);
        return aiThing;
    }
    
}
