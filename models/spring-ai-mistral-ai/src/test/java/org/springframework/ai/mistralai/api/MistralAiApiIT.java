/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.mistralai.api;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletion;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ContentChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ImageUrlChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ImageUrlChunk.ImageUrl;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ReferenceChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.TextChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest.PromptMode;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest.ReasoningEffort;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest.ToolChoice;
import org.springframework.ai.mistralai.api.MistralAiApi.Embedding;
import org.springframework.ai.mistralai.api.MistralAiApi.EmbeddingList;
import org.springframework.ai.mistralai.api.MistralAiApi.FunctionTool;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Jason Smith
 * @author Nicolas Krier
 * @since 0.8.1
 */
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiApiIT {

	private static final Logger LOGGER = LoggerFactory.getLogger(MistralAiApiIT.class);

	private final MistralAiApi mistralAiApi = MistralAiApi.builder()
		.apiKey(System.getenv("MISTRAL_AI_API_KEY"))
		.build();

	@Test
	void chatCompletionEntityWithStringContent() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.USER);
		ResponseEntity<ChatCompletion> response = this.mistralAiApi.chatCompletionEntity(new ChatCompletionRequest(
				List.of(chatCompletionMessage), MistralAiApi.ChatModel.MISTRAL_SMALL.getValue(), 0.8, false));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void chatCompletionEntityWithTextChunkAndImageUrlChunkContent() throws IOException {
		var imageData = new ClassPathResource("test.png").getContentAsByteArray();
		List<ContentChunk> contentChunks = List.of(new TextChunk("Can you describe the content of this picture?"),
				new ImageUrlChunk(ImageUrl.fromImageData(MimeTypeUtils.IMAGE_PNG, imageData)));
		var chatCompletionRequest = new ChatCompletionRequest(
				List.of(new ChatCompletionMessage(contentChunks, Role.USER)),
				MistralAiApi.ChatModel.MISTRAL_MEDIUM.getValue());
		var responseEntity = this.mistralAiApi.chatCompletionEntity(chatCompletionRequest);

		assertThat(responseEntity).isNotNull();
		assertThat(responseEntity.getStatusCode().is2xxSuccessful()).isTrue();
		var chatCompletion = responseEntity.getBody();
		assertThat(chatCompletion).isNotNull();
		var choices = chatCompletion.choices();
		assertThat(choices).hasSize(1);
		var chatCompletionMessage = choices.get(0).message();
		var content = extractContent(chatCompletionMessage);
		assertThat(content).contains("bananas").contains("apples");
	}

	@EnumSource(value = MistralAiApi.ChatModel.class,
			names = { "MAGISTRAL_MEDIUM", "MAGISTRAL_SMALL", "MISTRAL_SMALL" })
	@ParameterizedTest
	void chatCompletionEntityWithThinkingModel(MistralAiApi.ChatModel chatModel) {
		var chatCompletionRequest = createChatCompletionRequest(chatModel, false);
		var responseEntity = this.mistralAiApi.chatCompletionEntity(chatCompletionRequest);

		assertThat(responseEntity).isNotNull();
		assertThat(responseEntity.getStatusCode().is2xxSuccessful()).isTrue();
		var chatCompletion = responseEntity.getBody();
		assertThat(chatCompletion).isNotNull();
		var choices = chatCompletion.choices();
		assertThat(choices).hasSize(1);
		var chatCompletionMessage = choices.get(0).message();
		var content = extractContent(chatCompletionMessage);
		assertThat(content).contains("Jupiter");
		var thinkingContent = extractThinkingContent(chatCompletionMessage);
		assertThat(thinkingContent).isNotEmpty();
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

		ResponseEntity<ChatCompletion> response = this.mistralAiApi.chatCompletionEntity(new ChatCompletionRequest(
				List.of(systemMessage, userMessage), MistralAiApi.ChatModel.MISTRAL_SMALL.getValue(), 0.8, false));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void chatCompletionStream() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.USER);
		Flux<ChatCompletionChunk> response = this.mistralAiApi.chatCompletionStream(new ChatCompletionRequest(
				List.of(chatCompletionMessage), MistralAiApi.ChatModel.MISTRAL_SMALL.getValue(), 0.8, true));

		assertThat(response).isNotNull();
		assertThat(response.collectList().block()).isNotNull();
	}

	@EnumSource(value = MistralAiApi.ChatModel.class,
			names = { "MAGISTRAL_MEDIUM", "MAGISTRAL_SMALL", "MISTRAL_SMALL" })
	@ParameterizedTest
	void chatCompletionStreamWithThinkingModel(MistralAiApi.ChatModel chatModel) {
		var chatCompletionRequest = createChatCompletionRequest(chatModel, true);
		var chatCompletionChunkFlux = this.mistralAiApi.chatCompletionStream(chatCompletionRequest);

		assertThat(chatCompletionChunkFlux).isNotNull();
		var chatCompletionChunks = chatCompletionChunkFlux.collectList().block();
		assertThat(chatCompletionChunks).isNotNull();
		var hasContent = hasContent(chatCompletionChunks, ChatCompletionMessage::extractContent);
		assertThat(hasContent).isTrue();
		var hasThinkingContent = hasContent(chatCompletionChunks, ChatCompletionMessage::extractThinkingContent);
		assertThat(hasThinkingContent).isTrue();
	}

	@Test
	void embeddings() {
		ResponseEntity<EmbeddingList<Embedding>> response = this.mistralAiApi
			.embeddings(new MistralAiApi.EmbeddingRequest<>("Hello world"));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().data()).hasSize(1);
		assertThat(response.getBody().data().get(0).embedding()).hasSize(1024);
	}

	/**
	 * This integration test verifies the correct handling of citations and references in
	 * responses generated by Mistral Large model, as described in the
	 * <a href="https://docs.mistral.ai/capabilities/citations">Mistral AI Citations &
	 * References Documentation</a>.
	 */
	@Test
	void chatCompletionEntityWithTextChunkAndReferenceChunkContent() {
		var chatCompletionRequest = createChatCompletionRequest();
		var responseEntity = this.mistralAiApi.chatCompletionEntity(chatCompletionRequest);

		assertThat(responseEntity).isNotNull();
		assertThat(responseEntity.getStatusCode().is2xxSuccessful()).isTrue();
		var chatCompletion = responseEntity.getBody();
		assertThat(chatCompletion).isNotNull();
		var choices = chatCompletion.choices();
		assertThat(choices).hasSize(1);
		var chatCompletionMessage = choices.get(0).message();
		var content = chatCompletionMessage.content();
		assertThat(content).isInstanceOf(List.class);
		var contentChunks = (List<ContentChunk>) content;
		assertThat(contentChunks).hasSize(3);
		var contentChunk1 = contentChunks.get(0);
		assertThat(contentChunk1).isInstanceOf(TextChunk.class);
		var textChunk1 = (TextChunk) contentChunk1;
		assertThat(textChunk1.text()).contains("Nihon Hidankyo");
		var contentChunk2 = contentChunks.get(1);
		assertThat(contentChunk2).isInstanceOf(ReferenceChunk.class);
		var referenceChunk = (ReferenceChunk) contentChunk2;
		assertThat(referenceChunk.referenceIds()).isEqualTo(List.of(0));
		var contentChunk3 = contentChunks.get(2);
		assertThat(contentChunk3).isInstanceOf(TextChunk.class);
		var textChunk2 = (TextChunk) contentChunk3;
		assertThat(textChunk2.text()).isNotEmpty();
	}

	private static String extractContent(ChatCompletionMessage chatCompletionMessage) {
		var content = chatCompletionMessage.extractContent();
		LOGGER.info("Extracted content: {}", content);

		return content;
	}

	private static String extractThinkingContent(ChatCompletionMessage chatCompletionMessage) {
		var thinkingContent = chatCompletionMessage.extractThinkingContent();
		LOGGER.info("Extracted thinking content: {}", thinkingContent);

		return thinkingContent;
	}

	private static ChatCompletionRequest createChatCompletionRequest() {
		var functionName = "get_information";
		var chatChatCompletionMessages = createChatCompletionMessages(functionName);
		// @formatter:off
		var parameters = Map.of(
				"type", "object",
				"properties", Map.of(),
				"additionalProperties", false
		);
		// @formatter:on
		var function = new FunctionTool.Function("Get information from external source.", functionName, parameters);
		var functionTool = new FunctionTool(FunctionTool.Type.FUNCTION, function);

		return new ChatCompletionRequest(chatChatCompletionMessages, MistralAiApi.ChatModel.MISTRAL_LARGE.getValue(),
				List.of(functionTool), ToolChoice.AUTO);
	}

	private static List<ChatCompletionMessage> createChatCompletionMessages(String functionName) {
		var referencesJsonString = extractReferencesAsJsonString();
		var systemChatCompletionMessage = new ChatCompletionMessage(
				"Answer the user by providing references to the source of the information using the tool available.",
				Role.SYSTEM);
		var userChatCompletionMessage = new ChatCompletionMessage("Who won the Nobel Prize in 2024?", Role.USER);
		var toolCallId = "3DHY8663m";
		var toolCall = new ToolCall(toolCallId, "function", new ChatCompletionFunction(functionName, "{}"), 0);
		var assistantChatCompletionMessage = new ChatCompletionMessage("", Role.ASSISTANT, null, List.of(toolCall));
		var toolChatCompletionMessage = new ChatCompletionMessage(referencesJsonString, Role.TOOL, functionName, null,
				toolCallId);

		return List.of(systemChatCompletionMessage, userChatCompletionMessage, assistantChatCompletionMessage,
				toolChatCompletionMessage);
	}

	private static String extractReferencesAsJsonString() {
		try {
			var references = new ClassPathResource("references.json").getContentAsString(StandardCharsets.UTF_8);
			var stringWriter = new StringWriter();
			var jsonGenerator = JsonMapper.shared().createGenerator(stringWriter);
			jsonGenerator.writeString(references);

			return stringWriter.toString();
		}
		catch (IOException ioException) {
			throw new IllegalStateException("Unable to extract file content!", ioException);
		}
	}

	private static ChatCompletionRequest createChatCompletionRequest(MistralAiApi.ChatModel chatModel, boolean stream) {
		var promptMode = Set.of(MistralAiApi.ChatModel.MAGISTRAL_MEDIUM, MistralAiApi.ChatModel.MAGISTRAL_SMALL)
			.contains(chatModel) ? PromptMode.REASONING : null;
		var reasoningEffort = MistralAiApi.ChatModel.MISTRAL_SMALL == chatModel ? ReasoningEffort.HIGH : null;
		var systemChatCompletionMessage = new ChatCompletionMessage(
				List.of(new TextChunk("You are a helpful assistant providing accurate short answers.")), Role.SYSTEM);
		var userChatCompletionMessage = new ChatCompletionMessage(
				"What is the first planet of the solar system based on the mass in descending order?", Role.USER);
		var chatCompletionMessages = List.of(systemChatCompletionMessage, userChatCompletionMessage);

		return new ChatCompletionRequest(chatModel.getValue(), chatCompletionMessages, null, null, 0.7, 1.0, null,
				stream, false, null, promptMode, reasoningEffort, null, null);
	}

	private static boolean hasContent(List<ChatCompletionChunk> chatCompletionChunks,
			Function<ChatCompletionMessage, String> extractingContentFunction) {
		return chatCompletionChunks.stream()
			.map(ChatCompletionChunk::choices)
			.flatMap(List::stream)
			.map(ChatCompletionChunk.ChunkChoice::delta)
			.map(extractingContentFunction)
			.filter(Objects::nonNull)
			.anyMatch(Predicate.not(String::isEmpty));
	}

}
