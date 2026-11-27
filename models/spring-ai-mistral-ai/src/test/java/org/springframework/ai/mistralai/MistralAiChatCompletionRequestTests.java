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

package org.springframework.ai.mistralai;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ContentChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.DocumentUrlChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.FileChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ImageUrlChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.TextChunk;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * @author Ricken Bazolo
 * @author Alexandros Pappas
 * @author Thomas Vitale
 * @author Nicolas Krier
 * @author Sebastien Deleuze
 * @since 0.8.1
 */
class MistralAiChatCompletionRequestTests {

	private static final String BASE_URL = "https://faked.url";

	private static final String API_KEY = "FAKED_API_KEY";

	private static final String TEXT_CONTENT = "Hello world!";

	private static final String IMAGE_URL = "https://example.com/image.png";

	private static final Media IMAGE_MEDIA = new Media(Media.Format.IMAGE_PNG, URI.create(IMAGE_URL));

	private final MistralAiChatModel chatModel = MistralAiChatModel.builder()
		.mistralAiApi(MistralAiApi.builder().baseUrl(BASE_URL).apiKey(API_KEY).build())
		.build();

	@Test
	void chatCompletionDefaultRequestTest() {
		var prompt = new Prompt("test content", MistralAiChatOptions.builder().build());
		var request = this.chatModel.createRequest(prompt, false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.topP()).isEqualTo(1);
		assertThat(request.temperature()).isEqualTo(0.7);
		assertThat(request.safePrompt()).isFalse();
		assertThat(request.maxTokens()).isNull();
		assertThat(request.n()).isNull();
		assertThat(request.frequencyPenalty()).isEqualTo(0.0);
		assertThat(request.presencePenalty()).isEqualTo(0.0);
		assertThat(request.stream()).isFalse();
	}

	@Test
	void chatCompletionRequestWithOptionsTest() {
		var options = MistralAiChatOptions.builder()
			.model(MistralAiApi.ChatModel.MISTRAL_SMALL.getValue())
			.temperature(0.5)
			.topP(0.8)
			.maxTokens(100)
			.safePrompt(true)
			.randomSeed(5)
			.stop(List.of("stop1", "stop2"))
			.frequencyPenalty(0.5)
			.presencePenalty(0.3)
			.n(2)
			.tools(List.of(new MistralAiApi.FunctionTool()))
			.toolChoice(MistralAiApi.ChatCompletionRequest.ToolChoice.AUTO)
			.build();

		var prompt = new Prompt("test content", options);
		var request = this.chatModel.createRequest(prompt, true);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.model()).isEqualTo(MistralAiApi.ChatModel.MISTRAL_SMALL.getValue());
		assertThat(request.temperature()).isEqualTo(0.5);
		assertThat(request.topP()).isEqualTo(0.8);
		assertThat(request.maxTokens()).isEqualTo(100);
		assertThat(request.safePrompt()).isTrue();
		assertThat(request.randomSeed()).isEqualTo(5);
		assertThat(request.stop()).containsExactly("stop1", "stop2");
		assertThat(request.frequencyPenalty()).isEqualTo(0.5);
		assertThat(request.presencePenalty()).isEqualTo(0.3);
		assertThat(request.n()).isEqualTo(2);
		assertThat(request.tools()).isNotEmpty()
			.extracting(MistralAiApi.FunctionTool::getType)
			.containsExactly(MistralAiApi.FunctionTool.Type.FUNCTION);
		assertThat(request.toolChoice()).isEqualTo(MistralAiApi.ChatCompletionRequest.ToolChoice.AUTO);
		assertThat(request.stream()).isTrue();
	}

	@Test
	void createChatCompletionMessagesWithUserMessage() {
		var userMessage = UserMessage.builder().text(TEXT_CONTENT).media(IMAGE_MEDIA).build();
		var prompt = createPrompt(userMessage);
		var chatCompletionRequest = this.chatModel.createRequest(prompt, false);
		verifyUserChatCompletionMessages(chatCompletionRequest.messages());
	}

	@Test
	void createChatCompletionMessagesWithMultipleUploadedFileIds() {
		Media fileOne = Media.builder().mimeType(Media.Format.DOC_PDF).id("file-uuid-1").data("file-uuid-1").build();
		Media fileTwo = Media.builder().mimeType(Media.Format.DOC_PDF).id("file-uuid-2").data("").build();
		var userMessage = UserMessage.builder().text("Classify these documents").media(fileOne, fileTwo).build();

		var request = this.chatModel.createRequest(createPrompt(userMessage), false);
		assertThat(request.messages()).hasSize(1);
		@SuppressWarnings("unchecked")
		var chunks = (List<ContentChunk>) request.messages().get(0).content();
		assertThat(chunks).containsExactly(new TextChunk("Classify these documents"), new FileChunk("file-uuid-1"),
				new FileChunk("file-uuid-2"));
	}

	@Test
	void createChatCompletionMessagesWithDocumentUrlAndBytesAndMixedMedia() throws MalformedURLException {
		Media pdfUri = new Media(Media.Format.DOC_PDF, URI.create("https://example.com/report.pdf"));
		Media pdfNamedUrl = Media.builder()
			.mimeType(Media.Format.DOC_PDF)
			.data(URI.create("https://example.com/named.pdf").toURL())
			.name("named.pdf")
			.build();
		Media pdfBytes = Media.builder()
			.mimeType(Media.Format.DOC_PDF)
			.data("%PDF-1.4".getBytes(StandardCharsets.UTF_8))
			.build();
		var userMessage = UserMessage.builder()
			.text("Inspect all")
			.media(IMAGE_MEDIA, pdfUri, pdfNamedUrl, pdfBytes)
			.build();

		var request = this.chatModel.createRequest(createPrompt(userMessage), false);
		assertThat(request.messages()).hasSize(1);
		@SuppressWarnings("unchecked")
		var chunks = (List<ContentChunk>) request.messages().get(0).content();
		assertThat(chunks).hasSize(5);
		assertThat(chunks.get(0)).isEqualTo(new TextChunk("Inspect all"));
		assertThat(chunks.get(1)).isInstanceOf(ImageUrlChunk.class);
		assertThat(chunks.get(2)).isEqualTo(new DocumentUrlChunk("https://example.com/report.pdf", null));
		assertThat(chunks.get(3)).isEqualTo(new DocumentUrlChunk("https://example.com/named.pdf", "named.pdf"));
		assertThat(chunks.get(4)).isInstanceOfSatisfying(DocumentUrlChunk.class, chunk -> {
			assertThat(chunk.documentUrl()).startsWith("data:application/pdf;base64,");
			assertThat(chunk.documentName()).isNull();
		});
	}

	@Test
	void createChatCompletionMessagesWithDocumentEdgeCases() {
		// Omitted text + bare base64 string gets formatted as a data URI
		Media bareBase64Pdf = Media.builder().mimeType(Media.Format.DOC_PDF).data("JVBERi0xLjQ=").build();
		var bareMsg = UserMessage.builder().media(bareBase64Pdf).build();
		@SuppressWarnings("unchecked")
		var bareChunks = (List<ContentChunk>) this.chatModel.createRequest(createPrompt(bareMsg), false)
			.messages()
			.get(0)
			.content();
		assertThat(bareChunks).containsExactly(new DocumentUrlChunk("data:application/pdf;base64,JVBERi0xLjQ=", null));

		// Image or document with tracking id + URL preserves the URL; id + empty data
		// routes to FileChunk
		Media imageWithTrackingId = Media.builder()
			.mimeType(Media.Format.IMAGE_PNG)
			.id("tracking-1")
			.data(IMAGE_URL)
			.build();
		Media pdfWithTrackingId = Media.builder()
			.mimeType(Media.Format.DOC_PDF)
			.id("tracking-2")
			.data("https://example.com/tracked.pdf")
			.build();
		Media uploadedImageFile = Media.builder().mimeType(Media.Format.IMAGE_PNG).id("file-img-1").data("").build();
		Media urlMatchingId = Media.builder().mimeType(Media.Format.IMAGE_PNG).id(IMAGE_URL).data(IMAGE_URL).build();
		var mixedImageMsg = UserMessage.builder()
			.media(imageWithTrackingId, pdfWithTrackingId, uploadedImageFile, urlMatchingId)
			.build();
		@SuppressWarnings("unchecked")
		var imageChunks = (List<ContentChunk>) this.chatModel.createRequest(createPrompt(mixedImageMsg), false)
			.messages()
			.get(0)
			.content();
		assertThat(imageChunks).hasSize(4);
		assertThat(imageChunks.get(0)).isInstanceOf(ImageUrlChunk.class);
		assertThat(imageChunks.get(1)).isEqualTo(new DocumentUrlChunk("https://example.com/tracked.pdf", null));
		assertThat(imageChunks.get(2)).isEqualTo(new FileChunk("file-img-1"));
		assertThat(imageChunks.get(3)).isInstanceOf(ImageUrlChunk.class);

		// Blank string data, empty byte[], and wildcard MimeType fail fast
		Media emptyText = Media.builder().mimeType(Media.Format.DOC_PDF).data("   ").build();
		Media emptyBytes = Media.builder().mimeType(Media.Format.DOC_PDF).data(new byte[0]).build();
		Media wildcardMime = Media.builder().mimeType(MimeTypeUtils.ALL).data(new byte[] { 1 }).build();
		assertThatIllegalArgumentException().isThrownBy(() -> this.chatModel
			.createRequest(createPrompt(UserMessage.builder().media(emptyText).build()), false));
		assertThatIllegalArgumentException().isThrownBy(() -> this.chatModel
			.createRequest(createPrompt(UserMessage.builder().media(emptyBytes).build()), false));
		assertThatIllegalArgumentException().isThrownBy(() -> this.chatModel
			.createRequest(createPrompt(UserMessage.builder().media(wildcardMime).build()), false));
	}

	@Test
	void createChatCompletionMessagesWithSystemMessage() {
		var systemMessage = new SystemMessage(TEXT_CONTENT);
		var prompt = createPrompt(systemMessage);
		var chatCompletionRequest = this.chatModel.createRequest(prompt, false);
		verifySystemChatCompletionMessages(chatCompletionRequest.messages());
	}

	@Test
	void createChatCompletionMessagesWithAssistantMessage() {
		var toolCall1 = createToolCall(1);
		var toolCall2 = createToolCall(2);
		var toolCall3 = createToolCall(3);
		// @formatter:off
		var assistantMessage = AssistantMessage.builder()
				.content(TEXT_CONTENT)
				.toolCalls(List.of(toolCall1, toolCall2, toolCall3))
				.build();
		// @formatter:on
		var prompt = createPrompt(assistantMessage);
		var chatCompletionRequest = this.chatModel.createRequest(prompt, false);
		var chatCompletionMessages = chatCompletionRequest.messages();
		assertThat(chatCompletionMessages).hasSize(1);
		var chatCompletionMessage = chatCompletionMessages.get(0);
		assertThat(chatCompletionMessage.role()).isEqualTo(ChatCompletionMessage.Role.ASSISTANT);
		assertThat(chatCompletionMessage.content()).isEqualTo(TEXT_CONTENT);
		var toolCalls = chatCompletionMessage.toolCalls();
		assertThat(toolCalls).hasSize(3);
		verifyToolCall(toolCalls.get(0), toolCall1);
		verifyToolCall(toolCalls.get(1), toolCall2);
		verifyToolCall(toolCalls.get(2), toolCall3);
	}

	@Test
	void createChatCompletionMessagesWithToolResponseMessage() {
		var toolResponse1 = createToolResponse(1);
		var toolResponse2 = createToolResponse(2);
		var toolResponse3 = createToolResponse(3);
		var toolResponseMessage = ToolResponseMessage.builder()
			.responses(List.of(toolResponse1, toolResponse2, toolResponse3))
			.build();
		var prompt = createPrompt(toolResponseMessage);
		var chatCompletionRequest = this.chatModel.createRequest(prompt, false);
		var chatCompletionMessages = chatCompletionRequest.messages();
		assertThat(chatCompletionMessages).hasSize(3);
		verifyToolChatCompletionMessage(chatCompletionMessages.get(0), toolResponse1);
		verifyToolChatCompletionMessage(chatCompletionMessages.get(1), toolResponse2);
		verifyToolChatCompletionMessage(chatCompletionMessages.get(2), toolResponse3);
	}

	private Prompt createPrompt(Message message) {
		var chatOptions = MistralAiChatOptions.builder().temperature(0.7d).build();
		var prompt = new Prompt(message, chatOptions);
		return prompt;
	}

	private static void verifyToolChatCompletionMessage(ChatCompletionMessage chatCompletionMessage,
			ToolResponseMessage.ToolResponse toolResponse) {
		assertThat(chatCompletionMessage.role()).isEqualTo(ChatCompletionMessage.Role.TOOL);
		assertThat(chatCompletionMessage.content()).isEqualTo(toolResponse.responseData());
		assertThat(chatCompletionMessage.name()).isEqualTo(toolResponse.name());
		assertThat(chatCompletionMessage.toolCalls()).isNull();
		assertThat(chatCompletionMessage.toolCallId()).isEqualTo(toolResponse.id());
	}

	private static ToolResponseMessage.ToolResponse createToolResponse(int number) {
		return new ToolResponseMessage.ToolResponse("id" + number, "name" + number, "responseData" + number);
	}

	private static void verifyToolCall(ChatCompletionMessage.ToolCall mistralToolCall,
			AssistantMessage.ToolCall toolCall) {
		assertThat(mistralToolCall.id()).isEqualTo(toolCall.id());
		assertThat(mistralToolCall.type()).isEqualTo(toolCall.type());
		var function = mistralToolCall.function();
		assertThat(function).isNotNull();
		assertThat(function.name()).isEqualTo(toolCall.name());
		assertThat(function.arguments()).isEqualTo(toolCall.arguments());
	}

	private static AssistantMessage.ToolCall createToolCall(int number) {
		return new AssistantMessage.ToolCall("id" + number, "type" + number, "name" + number, "arguments " + number);
	}

	private static void verifySystemChatCompletionMessages(List<ChatCompletionMessage> chatCompletionMessages) {
		assertThat(chatCompletionMessages).hasSize(1);
		var chatCompletionMessage = chatCompletionMessages.get(0);
		assertThat(chatCompletionMessage.role()).isEqualTo(ChatCompletionMessage.Role.SYSTEM);
		assertThat(chatCompletionMessage.content()).isEqualTo(TEXT_CONTENT);
	}

	private static void verifyUserChatCompletionMessages(List<ChatCompletionMessage> chatCompletionMessages) {
		assertThat(chatCompletionMessages).hasSize(1);
		var chatCompletionMessage = chatCompletionMessages.get(0);
		assertThat(chatCompletionMessage.role()).isEqualTo(ChatCompletionMessage.Role.USER);
		var contentChunks = (List<MistralAiApi.ChatCompletionMessage.ContentChunk>) chatCompletionMessage.content();
		assertThat(contentChunks).hasSize(2);
		var textChunk = (MistralAiApi.ChatCompletionMessage.TextChunk) contentChunks.get(0);
		assertThat(textChunk).isNotNull();
		assertThat(textChunk.text()).isEqualTo(TEXT_CONTENT);
		var imageUrlChunk = (MistralAiApi.ChatCompletionMessage.ImageUrlChunk) contentChunks.get(1);
		assertThat(imageUrlChunk).isNotNull();
		var imageUrl = imageUrlChunk.imageUrl();
		assertThat(imageUrl.url()).isEqualTo(IMAGE_URL);
		assertThat(imageUrl.detail()).isNull();
	}

}
