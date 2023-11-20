package org.springframework.ai.operator;

import org.springframework.ai.client.AiClient;
import org.springframework.ai.memory.Memory;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Map;

public interface AiOperator {

	String generate();

	String generate(Map<String, Object> parameters);

	static AiOperator create(AiClient aiClient) {
		return (new DefaultAiOperatorBuilder()).aiClient(aiClient).build();
	}

	static AiOperator.Builder builder() {
		return new DefaultAiOperatorBuilder();
	}

	AiOperator promptTemplate(String promptTemplate);

	public interface Builder {

		Builder aiClient(AiClient aiClient);

		Builder promptTemplate(String promptTemplate);

		Builder vectorStore(VectorStore vectorStore);

		Builder vectorStoreKey(String vectorStoreKey);

		Builder conversationMemory(Memory memory);

		AiOperator build();

	}

}
