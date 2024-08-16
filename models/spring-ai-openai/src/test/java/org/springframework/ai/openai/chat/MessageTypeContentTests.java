/*
 * Copyright 2024-2024 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
import org.springframework.ai.model.Media;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.MultiValueMap;

import reactor.core.publisher.Flux;

/**
 * @author Christian Tzolov
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

	Flux<ChatCompletionChunk> fluxResponse = Flux
		.generate(() -> new ChatCompletionChunk("id", List.of(), 0l, "model", "fp", "object", null), (state, sink) -> {
			sink.next(state);
			sink.complete();
			return state;
		});

	@BeforeEach
	public void beforeEach() {
		chatModel = new OpenAiChatModel(openAiApi);
	}

	@Test
	public void systemMessageSimpleContentType() {

		when(openAiApi.chatCompletionEntity(pomptCaptor.capture(), headersCaptor.capture()))
			.thenReturn(Mockito.mock(ResponseEntity.class));

		chatModel.call(new Prompt(List.of(new SystemMessage("test message"))));

		validateStringContent(pomptCaptor.getValue());
		assertThat(headersCaptor.getValue()).isEmpty();
	}

	@Test
	public void userMessageSimpleContentType() {

		when(openAiApi.chatCompletionEntity(pomptCaptor.capture(), headersCaptor.capture()))
			.thenReturn(Mockito.mock(ResponseEntity.class));

		chatModel.call(new Prompt(List.of(new UserMessage("test message"))));

		validateStringContent(pomptCaptor.getValue());
	}

	@Test
	public void streamUserMessageSimpleContentType() {

		when(openAiApi.chatCompletionStream(pomptCaptor.capture(), headersCaptor.capture())).thenReturn(fluxResponse);

		chatModel.stream(new Prompt(List.of(new UserMessage("test message")))).subscribe();

		validateStringContent(pomptCaptor.getValue());
		assertThat(headersCaptor.getValue()).isEmpty();
	}

	private void validateStringContent(ChatCompletionRequest chatCompletionRequest) {

		assertThat(chatCompletionRequest.messages()).hasSize(1);
		var userMessage = chatCompletionRequest.messages().get(0);
		assertThat(userMessage.rawContent()).isInstanceOf(String.class);
		assertThat(userMessage.content()).isEqualTo("test message");
	}

	@Test
	public void userMessageWithMediaType() throws MalformedURLException {

		when(openAiApi.chatCompletionEntity(pomptCaptor.capture(), headersCaptor.capture()))
			.thenReturn(Mockito.mock(ResponseEntity.class));

		URL mediaUrl = new URL("http://test");
		chatModel.call(new Prompt(
				List.of(new UserMessage("test message", List.of(new Media(MimeTypeUtils.IMAGE_JPEG, mediaUrl))))));

		validateComplexContent(pomptCaptor.getValue());
	}

	@Test
	public void streamUserMessageWithMediaType() throws MalformedURLException {

		when(openAiApi.chatCompletionStream(pomptCaptor.capture(), headersCaptor.capture())).thenReturn(fluxResponse);

		URL mediaUrl = new URL("http://test");
		chatModel
			.stream(new Prompt(
					List.of(new UserMessage("test message", List.of(new Media(MimeTypeUtils.IMAGE_JPEG, mediaUrl))))))
			.subscribe();

		validateComplexContent(pomptCaptor.getValue());
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
