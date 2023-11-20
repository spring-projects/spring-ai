package org.springframework.ai.operator;

import org.springframework.ai.client.AiClient;
import org.springframework.ai.memory.Memory;
import org.springframework.ai.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.VectorStore;

public class DefaultAiOperatorBuilder implements AiOperator.Builder {

	private AiClient aiClient;

	private PromptTemplate promptTemplate;

	private VectorStore vectorStore;

	private String vectorStoreKey = "documents";

	private Memory memory;

	public DefaultAiOperatorBuilder() {
	}

	@Override
	public AiOperator.Builder aiClient(AiClient aiClient) {
		this.aiClient = aiClient;
		return this;
	}

	@Override
	public AiOperator.Builder promptTemplate(String promptTemplate) {
		this.promptTemplate = new PromptTemplate(promptTemplate);
		return this;
	}

	@Override
	public AiOperator.Builder vectorStore(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
		return this;
	}

	@Override
	public AiOperator.Builder vectorStoreKey(String vectorStoreKey) {
		this.vectorStoreKey = vectorStoreKey;
		return this;
	}

	public AiOperator.Builder conversationMemory(Memory memory) {
		this.memory = memory;
		return this;
	}

	public AiOperator build() {
		DefaultAiOperator aiOperator = new DefaultAiOperator(aiClient, promptTemplate);
		aiOperator.vectorStore(vectorStore);
		aiOperator.vectorStoreKey(vectorStoreKey);
		aiOperator.conversationMemory(memory);
		return aiOperator;
	}

}
