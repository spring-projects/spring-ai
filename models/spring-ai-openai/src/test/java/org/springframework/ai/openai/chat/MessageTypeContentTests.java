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
import java.net.URL;
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
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.MultiValueMap;

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
	ArgumentCaptor<MultiValueMap<String, String>> headersCaptor;

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
		assertThat(this.headersCaptor.getValue()).isEmpty();
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
		assertThat(this.headersCaptor.getValue()).isEmpty();
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

		URL mediaUrl = new URL("http://test");
		this.chatModel.call(new Prompt(List.of(new UserMessage("test message",
				List.of(Media.builder().mimeType(MimeTypeUtils.IMAGE_JPEG).data(mediaUrl).build())))));

		validateComplexContent(this.pomptCaptor.getValue());
	}

	@Test
	public void streamUserMessageWithMediaType() throws MalformedURLException {

		given(this.openAiApi.chatCompletionStream(this.pomptCaptor.capture(), this.headersCaptor.capture()))
			.willReturn(this.fluxResponse);

		URL mediaUrl = new URL("http://test");
		this.chatModel.stream(new Prompt(List.of(new UserMessage("test message",
				List.of(Media.builder().mimeType(MimeTypeUtils.IMAGE_JPEG).data(mediaUrl).build())))))
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

		assertThat(mediaContents).hasSize(2);

		Map<String, Object> textContent = mediaContents.get(0);
		assertThat(textContent.get("type")).isEqualTo("text");
		assertThat(textContent.get("text")).isEqualTo("test message");

		Map<String, Object> imageContent = mediaContents.get(1);

		assertThat(imageContent.get("type")).isEqualTo("image_url");
		assertThat(imageContent).containsKey("image_url");
	}

}
