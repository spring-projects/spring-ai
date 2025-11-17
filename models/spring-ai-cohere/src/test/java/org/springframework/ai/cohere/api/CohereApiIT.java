package org.springframework.ai.cohere.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.cohere.CohereTestConfiguration;
import org.springframework.ai.cohere.testutils.AbstractIT;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import org.springframework.ai.cohere.api.CohereApi.ChatCompletion;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage.Role;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionRequest;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ricken Bazolo
 */
@SpringBootTest(classes = CohereTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "COHERE_API_KEY", matches = ".+")
class CohereApiIT extends AbstractIT {

	@Test
	void chatCompletionEntity() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.USER);
		ResponseEntity<ChatCompletion> response = this.cohereApi.chatCompletionEntity(new ChatCompletionRequest(
				List.of(chatCompletionMessage), CohereApi.ChatModel.COMMAND_A_R7B.getValue(), 0.8, false));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void chatCompletionEntityWithSystemMessage() {
		ChatCompletionMessage userMessage = new ChatCompletionMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did?", Role.USER);
		ChatCompletionMessage systemMessage = new ChatCompletionMessage("""
				You are an AI assistant that helps people find information.
				Your name is Bob.
				You should reply to the user's request with your name and also in the style of a pirate.
				""", Role.SYSTEM);

		ResponseEntity<ChatCompletion> response = this.cohereApi.chatCompletionEntity(new ChatCompletionRequest(
				List.of(systemMessage, userMessage), CohereApi.ChatModel.COMMAND_A_R7B.getValue(), 0.8, false));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void embeddings() {
		ResponseEntity<CohereApi.EmbeddingResponse> response = this.cohereApi
				.embeddings(CohereApi.EmbeddingRequest.<String>builder()
						.texts("Hello world")
						.build());

		assertThat(response).isNotNull();
		Assertions.assertNotNull(response.getBody());
		assertThat(response.getBody().getFloatEmbeddings()).hasSize(1);
		assertThat(response.getBody().getFloatEmbeddings().get(0)).hasSize(1536);
	}

	@Test
	void chatCompletionStream() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.USER);
		Flux<CohereApi.ChatCompletionChunk> response = this.cohereApi.chatCompletionStream(new ChatCompletionRequest(
				List.of(chatCompletionMessage), CohereApi.ChatModel.COMMAND_A_R7B.getValue(), 0.8, true));

		assertThat(response).isNotNull();
		assertThat(response.collectList().block()).isNotNull();
	}

}
