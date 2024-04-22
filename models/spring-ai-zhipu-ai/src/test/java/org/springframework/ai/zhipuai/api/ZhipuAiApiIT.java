package org.springframework.ai.zhipuai.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.zhipuai.api.ZhipuAiApi.*;
import org.springframework.ai.zhipuai.api.ZhipuAiApi.ChatCompletionMessage.Role;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ricken Bazolo
 */
@EnabledIfEnvironmentVariable(named = "ZHIPU_AI_API_KEY", matches = ".+")
public class ZhipuAiApiIT {

	ZhipuAiApi zhipuAiApi = new ZhipuAiApi(System.getenv("ZHIPU_AI_API_KEY"));

	@Test
	void chatCompletionEntity() {
		var chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.USER);
		var response = zhipuAiApi.chatCompletionEntity(new ChatCompletionRequest(List.of(chatCompletionMessage),
				ZhipuAiApi.ChatModel.GLM_4.getValue(), 0.8f, false));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void chatCompletionEntityWithSystemMessage() {
		var userMessage = new ChatCompletionMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did?", Role.USER);
		var systemMessage = new ChatCompletionMessage("""
				You are an AI assistant that helps people find information.
				Your name is Bob.
				You should reply to the user's request with your name and also in the style of a pirate.
					""", Role.SYSTEM);

		var response = zhipuAiApi.chatCompletionEntity(new ChatCompletionRequest(List.of(systemMessage, userMessage),
				ChatModel.GLM_4.getValue(), 0.8f, false));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void chatCompletionStream() {
		var chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.USER);
		Flux<ChatCompletionChunk> response = zhipuAiApi.chatCompletionStream(
				new ChatCompletionRequest(List.of(chatCompletionMessage), ChatModel.GLM_4.getValue(), 0.8f, true));

		assertThat(response).isNotNull();
		assertThat(response.collectList().block()).isNotNull();
	}

	@Test
	void embeddings() {
		var response = zhipuAiApi.embeddings(new ZhipuAiApi.EmbeddingRequest<>("Hello world"));

		assertThat(response).isNotNull();
		assertThat(response.getBody().data()).hasSize(1);
		assertThat(response.getBody().data().get(0).embedding()).hasSize(1024);
	}

}
