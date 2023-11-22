package org.springframework.ai.operator;

import org.junit.jupiter.api.Test;
import org.springframework.ai.client.AiClient;
import org.springframework.ai.client.AiResponse;
import org.springframework.ai.client.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.memory.ConversationBufferMemory;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AiOperatorTests {

	@Test
	public void simplePrompt() {
		AiClient aiClient = mock(AiClient.class);
		AiResponse aiResponse = new AiResponse(List.of(new Generation("Because of Rayleigh scattering.")));
		when(aiClient.generate(new Prompt("Why is the sky blue?"))).thenReturn(aiResponse);

		AiOperator aiOperator = AiOperator.create(aiClient).promptTemplate("Why is the sky blue?");
		String response = aiOperator.generate();

		assertThat(response).isEqualTo("Because of Rayleigh scattering.");
	}

	@Test
	public void simplePromptUsingBuilder() {
		AiClient aiClient = mock(AiClient.class);
		AiResponse aiResponse = new AiResponse(List.of(new Generation("Because of Rayleigh scattering.")));
		when(aiClient.generate(new Prompt("Why is the sky blue?"))).thenReturn(aiResponse);

		AiOperator aiOperator = AiOperator.builder().aiClient(aiClient).promptTemplate("Why is the sky blue?").build();
		String response = aiOperator.generate();

		assertThat(response).isEqualTo("Because of Rayleigh scattering.");
	}

	@Test
	public void promptWithParameters() {
		AiClient aiClient = mock(AiClient.class);
		AiResponse aiResponse = new AiResponse(
				List.of(new Generation("What do you call a herd of wealthy cows? Cash cows.")));

		when(aiClient.generate(new Prompt("Tell me a joke about cows."))).thenReturn(aiResponse);

		AiOperator aiOperator = AiOperator.builder()
			.aiClient(aiClient)
			.promptTemplate("Tell me a joke about {subject}.")
			.build();
		String response = aiOperator.generate(Map.of("subject", "cows"));

		assertThat(response).isEqualTo("What do you call a herd of wealthy cows? Cash cows.");
	}

	@Test
	public void usingVectorStore() {
		// Mock the vector store
		VectorStore vectorStore = mock(VectorStore.class);
		when(vectorStore.similaritySearch(SearchRequest.query("How do you score roads?").withTopK(2)))
			.thenReturn(List.of(
					new Document("Roads are scored when completed and are worth 1 point per tile they go through.",
							Map.of()),
					new Document("Roads are terminated at cities, monasteries, and crossroads.", Map.of())));

		// Create a resolved version of the template for mocking purposes only
		String resolvedRagTemplate = DefaultPromptTemplateStrings.RAG_PROMPT.replace("{documents}", """
				Roads are scored when completed and are worth 1 point per tile they go through.
				Roads are terminated at cities, monasteries, and crossroads.""")
			.replace("{input}", "How do you score roads?");

		// Mock the AiClient
		AiClient aiClient = mock(AiClient.class);
		Prompt prompt = new Prompt(resolvedRagTemplate);
		AiResponse aiResponse = new AiResponse(
				List.of(new Generation("Roads are worth 1 point for each tile of a completed road.")));
		when(aiClient.generate(prompt)).thenReturn(aiResponse);

		AiOperator aiOperator = AiOperator.builder()
			.aiClient(aiClient)
			.promptTemplate(DefaultPromptTemplateStrings.RAG_PROMPT)
			.vectorStore(vectorStore)
			.build();

		// Ask the question
		String response = aiOperator.generate(Map.of("input", "How do you score roads?"));

		// Assert the response
		assertThat(response).isEqualTo("Roads are worth 1 point for each tile of a completed road.");
	}

	@Test
	public void conversationMemory() {
		AiClient aiClient = mock(AiClient.class);
		when(aiClient.generate(any(Prompt.class)))
			.thenReturn(new AiResponse(List.of(new Generation("Because of Rayleigh scattering."))));

		ConversationBufferMemory memory = new ConversationBufferMemory();

		AiOperator aiOperator = AiOperator.builder()
			.aiClient(aiClient)
			.promptTemplate(DefaultPromptTemplateStrings.CHAT_PROMPT)
			.conversationMemory(memory)
			.build();

		aiOperator.generate(Map.of("input", "Why is the sky blue?"));
		Map<String, Object> memoryMap = memory.load(Map.of());
		assertThat(memoryMap.get("history")).isEqualTo("""
				user: Why is the sky blue?
				assistant: Because of Rayleigh scattering.""");

		aiOperator.generate(Map.of("input", "Why is the sky blue?"));
		memoryMap = memory.load(Map.of());
		assertThat(memoryMap.get("history")).isEqualTo("""
				user: Why is the sky blue?
				assistant: Because of Rayleigh scattering.
				user: Why is the sky blue?
				assistant: Because of Rayleigh scattering.""");

	}

	@Test
	public void withVectorStoreAndMemory() {
		AiClient aiClient = mock(AiClient.class);
		ConversationBufferMemory memory = new ConversationBufferMemory();

		// preload the memory with some chat history
		String history = """
				user: Why is water wet?
				assistant: Because of the hydrogen bonds that hold water molecules together.
				user: Why is the sky blue?
				assistant: Because of Rayleigh scattering.""";
		memory.save(Map.of("input", "Why is water wet?"),
				Map.of("output", "Because of the hydrogen bonds that hold water molecules together."));
		memory.save(Map.of("input", "Why is the sky blue?"), Map.of("output", "Because of Rayleigh scattering."));

		// Mock the vector store
		VectorStore vectorStore = mock(VectorStore.class);
		when(vectorStore.similaritySearch(SearchRequest.query("Why is the sky blue?").withTopK(2)))
			.thenReturn(List.of(new Document("Because of Rayleigh scattering.", Map.of())));
		when(vectorStore.similaritySearch(SearchRequest.query("Is the sky ever green?").withTopK(2)))
			.thenReturn(List.of(new Document("The sky turns green on St. Patrick's Day.", Map.of())));

		String standalonePrompt = DefaultPromptTemplateStrings.STANDALONE_QUESTION_PROMPT.replace("{history}", history)
			.replace("{input}", "Is it ever green?");

		when(aiClient.generate(new Prompt(standalonePrompt)))
			.thenReturn(new AiResponse(List.of(new Generation("Is the sky ever green?"))));

		String questionPrompt = DefaultPromptTemplateStrings.RAG_PROMPT
			.replace("{documents}", "The sky turns green on St. Patrick's Day.")
			.replace("{input}", "Is the sky ever green?");
		when(aiClient.generate(new Prompt(questionPrompt)))
			.thenReturn(new AiResponse(List.of(new Generation("The sky is only green on St. Patrick's Day."))));

		AiOperator aiOperator = AiOperator.builder()
			.aiClient(aiClient)
			.promptTemplate(DefaultPromptTemplateStrings.RAG_PROMPT)
			.conversationMemory(memory)
			.vectorStore(vectorStore)
			.build();

		String result = aiOperator.generate(Map.of("input", "Is it ever green?"));
		assertThat(result).isEqualTo("The sky is only green on St. Patrick's Day.");
	}

}
