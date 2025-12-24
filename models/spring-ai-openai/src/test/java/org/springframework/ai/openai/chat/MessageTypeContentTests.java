/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.openai.chat;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
public class MessageTypeContentTests {

	@Mock
	OpenAiApi openAiApi;

	OpenAiChatModel chatModel;

	@Captor
	ArgumentCaptor<ChatCompletionRequest> pomptCaptor;

	@Captor
	ArgumentCaptor<HttpHeaders> headersCaptor;

	Flux<ChatCompletionChunk> fluxResponse = Flux.generate(
			() -> new ChatCompletionChunk("id", List.of(), 0L, "model", null, "fp", "object", null), (state, sink) -> {
				sink.next(state);
				sink.complete();
				return state;
			});

	@BeforeEach
	public void beforeEach() {
		this.chatModel = OpenAiChatModel.builder().openAiApi(this.openAiApi).build();
	}

	@Test
	public void systemMessageSimpleContentType() {

		given(this.openAiApi.chatCompletionEntity(this.pomptCaptor.capture(), this.headersCaptor.capture()))
			.willReturn(Mockito.mock(ResponseEntity.class));

		this.chatModel.call(new Prompt(List.of(new SystemMessage("test message"))));

		validateStringContent(this.pomptCaptor.getValue());
		assertThat(this.headersCaptor.getValue().isEmpty()).isTrue();
	}

	@Test
	public void userMessageSimpleContentType() {

		given(this.openAiApi.chatCompletionEntity(this.pomptCaptor.capture(), this.headersCaptor.capture()))
			.willReturn(Mockito.mock(ResponseEntity.class));

		this.chatModel.call(new Prompt(List.of(new UserMessage("test message"))));

		validateStringContent(this.pomptCaptor.getValue());
	}

	@Test
	public void streamUserMessageSimpleContentType() {

		given(this.openAiApi.chatCompletionStream(this.pomptCaptor.capture(), this.headersCaptor.capture()))
			.willReturn(this.fluxResponse);

		this.chatModel.stream(new Prompt(List.of(new UserMessage("test message")))).subscribe();

		validateStringContent(this.pomptCaptor.getValue());
		assertThat(this.headersCaptor.getValue().isEmpty()).isTrue();
	}

	private void validateStringContent(ChatCompletionRequest chatCompletionRequest) {

		assertThat(chatCompletionRequest.messages()).hasSize(1);
		var userMessage = chatCompletionRequest.messages().get(0);
		assertThat(userMessage.rawContent()).isInstanceOf(String.class);
		assertThat(userMessage.content()).isEqualTo("test message");
	}

	@Test
	public void userMessageWithMediaType() throws MalformedURLException {

		given(this.openAiApi.chatCompletionEntity(this.pomptCaptor.capture(), this.headersCaptor.capture()))
			.willReturn(Mockito.mock(ResponseEntity.class));

		this.chatModel
			.call(new Prompt(List.of(UserMessage.builder().text("test message").media(this.buildMediaList()).build())));

		validateComplexContent(this.pomptCaptor.getValue());
	}

	@Test
	public void streamUserMessageWithMediaType() throws MalformedURLException {

		given(this.openAiApi.chatCompletionStream(this.pomptCaptor.capture(), this.headersCaptor.capture()))
			.willReturn(this.fluxResponse);

		this.chatModel
			.stream(new Prompt(
					List.of(UserMessage.builder().text("test message").media(this.buildMediaList()).build())))
			.subscribe();

		validateComplexContent(this.pomptCaptor.getValue());
	}

	private void validateComplexContent(ChatCompletionRequest chatCompletionRequest) {

		assertThat(chatCompletionRequest.messages()).hasSize(1);
		var userMessage = chatCompletionRequest.messages().get(0);
		assertThat(userMessage.rawContent()).isInstanceOf(List.class);

		// Note: apparently the ArgumentCaptor converts the MediaContent into Map<String,
		// Object>
		@SuppressWarnings({ "unused", "unchecked" })
		List<Map<String, Object>> mediaContents = (List<Map<String, Object>>) userMessage.rawContent();

		assertThat(mediaContents).hasSize(3);

		// Assert text content
		Map<String, Object> textContent = mediaContents.get(0);
		assertThat(textContent.get("type")).isEqualTo("text");
		assertThat(textContent.get("text")).isEqualTo("test message");

		// Assert image content
		Map<String, Object> imageContent = mediaContents.get(1);

		assertThat(imageContent.get("type")).isEqualTo("image_url");
		assertThat(imageContent).containsKey("image_url");

		// Assert file content
		Map<String, Object> fileContent = mediaContents.get(2);
		assertThat(fileContent.get("type")).isEqualTo("file");
		assertThat(fileContent).containsKey("file");
		assertThat(fileContent.get("file")).isInstanceOf(Map.class);

		Map<String, Object> fileMap = (Map<String, Object>) fileContent.get("file");
		assertThat(fileMap.get("file_data")).isEqualTo("data:application/pdf;base64,JVBERi0xLjc=");
	}

	private List<Media> buildMediaList() {
		URI imageUri = URI.create("http://test");
		Media imageMedia = Media.builder().mimeType(MimeTypeUtils.IMAGE_JPEG).data(imageUri).build();

		byte[] pdfData = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
		Media pdfMedia = Media.builder()
			.mimeType(MimeType.valueOf("application/pdf"))
			.data(new ByteArrayResource(pdfData))
			.build();

		return List.of(imageMedia, pdfMedia);
	}

	@Test
	public void userMessageWithEmptyMediaList() {
		given(this.openAiApi.chatCompletionEntity(this.pomptCaptor.capture(), this.headersCaptor.capture()))
			.willReturn(Mockito.mock(ResponseEntity.class));

		this.chatModel.call(new Prompt(List.of(UserMessage.builder()
			.text("test message")
			.media(List.of()) // Empty media list
			.build())));

		validateStringContent(this.pomptCaptor.getValue());
		assertThat(this.headersCaptor.getValue().isEmpty()).isTrue();
	}

	@Test
	public void userMessageWithEmptyText() {
		given(this.openAiApi.chatCompletionEntity(this.pomptCaptor.capture(), this.headersCaptor.capture()))
			.willReturn(Mockito.mock(ResponseEntity.class));

		this.chatModel.call(new Prompt(List.of(UserMessage.builder().text("").media(this.buildMediaList()).build())));

		ChatCompletionRequest request = this.pomptCaptor.getValue();
		assertThat(request.messages()).hasSize(1);
		var userMessage = request.messages().get(0);
		assertThat(userMessage.rawContent()).isInstanceOf(List.class);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> mediaContents = (List<Map<String, Object>>) userMessage.rawContent();

		// Should have empty text content plus media
		assertThat(mediaContents).hasSize(3);
		Map<String, Object> textContent = mediaContents.get(0);
		assertThat(textContent.get("type")).isEqualTo("text");
		assertThat(textContent.get("text")).isEqualTo("");
	}

	@Test
	public void multipleMessagesWithMixedContentTypes() {
		given(this.openAiApi.chatCompletionEntity(this.pomptCaptor.capture(), this.headersCaptor.capture()))
			.willReturn(Mockito.mock(ResponseEntity.class));

		this.chatModel.call(
				new Prompt(List.of(new SystemMessage("You are a helpful assistant"), new UserMessage("Simple message"),
						UserMessage.builder().text("Message with media").media(this.buildMediaList()).build())));

		ChatCompletionRequest request = this.pomptCaptor.getValue();
		assertThat(request.messages()).hasSize(3);

		// First message - system message with string content
		var systemMessage = request.messages().get(0);
		assertThat(systemMessage.rawContent()).isInstanceOf(String.class);
		assertThat(systemMessage.content()).isEqualTo("You are a helpful assistant");

		// Second message - user message with string content
		var simpleUserMessage = request.messages().get(1);
		assertThat(simpleUserMessage.rawContent()).isInstanceOf(String.class);
		assertThat(simpleUserMessage.content()).isEqualTo("Simple message");

		// Third message - user message with complex content
		var complexUserMessage = request.messages().get(2);
		assertThat(complexUserMessage.rawContent()).isInstanceOf(List.class);
	}

	@Test
	public void userMessageWithSingleImageMedia() {
		given(this.openAiApi.chatCompletionEntity(this.pomptCaptor.capture(), this.headersCaptor.capture()))
			.willReturn(Mockito.mock(ResponseEntity.class));

		URI imageUri = URI.create("http://example.com/image.jpg");
		Media imageMedia = Media.builder().mimeType(MimeTypeUtils.IMAGE_JPEG).data(imageUri).build();

		this.chatModel.call(new Prompt(
				List.of(UserMessage.builder().text("Describe this image").media(List.of(imageMedia)).build())));

		ChatCompletionRequest request = this.pomptCaptor.getValue();
		assertThat(request.messages()).hasSize(1);
		var userMessage = request.messages().get(0);
		assertThat(userMessage.rawContent()).isInstanceOf(List.class);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> mediaContents = (List<Map<String, Object>>) userMessage.rawContent();
		assertThat(mediaContents).hasSize(2);

		// Text content
		Map<String, Object> textContent = mediaContents.get(0);
		assertThat(textContent.get("type")).isEqualTo("text");
		assertThat(textContent.get("text")).isEqualTo("Describe this image");

		// Image content
		Map<String, Object> imageContent = mediaContents.get(1);
		assertThat(imageContent.get("type")).isEqualTo("image_url");
		assertThat(imageContent).containsKey("image_url");
	}

	@Test
	public void streamWithMultipleMessagesAndMedia() {
		given(this.openAiApi.chatCompletionStream(this.pomptCaptor.capture(), this.headersCaptor.capture()))
			.willReturn(this.fluxResponse);

		this.chatModel
			.stream(new Prompt(List.of(new SystemMessage("System prompt"),
					UserMessage.builder().text("User message with media").media(this.buildMediaList()).build())))
			.subscribe();

		ChatCompletionRequest request = this.pomptCaptor.getValue();
		assertThat(request.messages()).hasSize(2);

		// System message should be string
		assertThat(request.messages().get(0).rawContent()).isInstanceOf(String.class);

		// User message should be complex
		assertThat(request.messages().get(1).rawContent()).isInstanceOf(List.class);
		assertThat(this.headersCaptor.getValue().isEmpty()).isTrue();
	}

	// Helper method for testing different image formats
	private List<Media> buildImageMediaList() {
		URI jpegUri = URI.create("http://example.com/image.jpg");
		Media jpegMedia = Media.builder().mimeType(MimeTypeUtils.IMAGE_JPEG).data(jpegUri).build();

		URI pngUri = URI.create("http://example.com/image.png");
		Media pngMedia = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(pngUri).build();

		URI webpUri = URI.create("http://example.com/image.webp");
		Media webpMedia = Media.builder().mimeType(MimeType.valueOf("image/webp")).data(webpUri).build();

		return List.of(jpegMedia, pngMedia, webpMedia);
	}

	@Test
	public void userMessageWithMultipleImageFormats() {
		given(this.openAiApi.chatCompletionEntity(this.pomptCaptor.capture(), this.headersCaptor.capture()))
			.willReturn(Mockito.mock(ResponseEntity.class));

		this.chatModel.call(new Prompt(
				List.of(UserMessage.builder().text("Compare these images").media(this.buildImageMediaList()).build())));

		ChatCompletionRequest request = this.pomptCaptor.getValue();
		assertThat(request.messages()).hasSize(1);
		var userMessage = request.messages().get(0);
		assertThat(userMessage.rawContent()).isInstanceOf(List.class);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> mediaContents = (List<Map<String, Object>>) userMessage.rawContent();
		assertThat(mediaContents).hasSize(4); // text + 3 images

		// Verify all are image types
		for (int i = 1; i < mediaContents.size(); i++) {
			Map<String, Object> imageContent = mediaContents.get(i);
			assertThat(imageContent.get("type")).isEqualTo("image_url");
			assertThat(imageContent).containsKey("image_url");
		}
	}

	@Test
	public void userMessageWithOnlyFileMedia() {
		given(this.openAiApi.chatCompletionEntity(this.pomptCaptor.capture(), this.headersCaptor.capture()))
			.willReturn(Mockito.mock(ResponseEntity.class));

		byte[] pdfData = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
		Media pdfMedia = Media.builder()
			.mimeType(MimeType.valueOf("application/pdf"))
			.data(new ByteArrayResource(pdfData))
			.build();

		this.chatModel.call(new Prompt(
				List.of(UserMessage.builder().text("Analyze this document").media(List.of(pdfMedia)).build())));

		ChatCompletionRequest request = this.pomptCaptor.getValue();
		assertThat(request.messages()).hasSize(1);
		var userMessage = request.messages().get(0);
		assertThat(userMessage.rawContent()).isInstanceOf(List.class);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> mediaContents = (List<Map<String, Object>>) userMessage.rawContent();
		assertThat(mediaContents).hasSize(2); // text + file

		// Text content
		Map<String, Object> textContent = mediaContents.get(0);
		assertThat(textContent.get("type")).isEqualTo("text");
		assertThat(textContent.get("text")).isEqualTo("Analyze this document");

		// File content
		Map<String, Object> fileContent = mediaContents.get(1);
		assertThat(fileContent.get("type")).isEqualTo("file");
		assertThat(fileContent).containsKey("file");
	}

	@Test
	public void systemMessageWithMultipleMessages() {
		given(this.openAiApi.chatCompletionEntity(this.pomptCaptor.capture(), this.headersCaptor.capture()))
			.willReturn(Mockito.mock(ResponseEntity.class));

		this.chatModel.call(new Prompt(List.of(new SystemMessage("First system message"),
				new SystemMessage("Second system message"), new UserMessage("User query"))));

		ChatCompletionRequest request = this.pomptCaptor.getValue();
		assertThat(request.messages()).hasSize(3);

		// All messages should have string content
		for (int i = 0; i < 3; i++) {
			var message = request.messages().get(i);
			assertThat(message.rawContent()).isInstanceOf(String.class);
		}

		assertThat(request.messages().get(0).content()).isEqualTo("First system message");
		assertThat(request.messages().get(1).content()).isEqualTo("Second system message");
		assertThat(request.messages().get(2).content()).isEqualTo("User query");
	}

}
