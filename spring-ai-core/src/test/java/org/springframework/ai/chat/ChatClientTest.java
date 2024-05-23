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

package org.springframework.ai.chat;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class ChatClientTest {

	public static interface MixChatModel extends ChatModel, StreamingChatModel {

	}

	@Mock
	MixChatModel chatModel;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	private String join(Flux<String> fluxContent) {
		return fluxContent.collectList().block().stream().collect(Collectors.joining());
	}

	// ChatClient Builder Tests
	@Test
	public void defaultSystemText() {

		when(chatModel.call(promptCaptor.capture()))
				.thenReturn(new ChatResponse(List.of(new Generation("response"))));

		when(chatModel.stream(promptCaptor.capture()))
				.thenReturn(
						Flux.generate(() -> new ChatResponse(List.of(new Generation("response"))), (state, sink) -> {
							sink.next(state);
							sink.complete();
							return state;
						}));

		var chatClient = ChatClient.builder(chatModel)
				.defaultSystem("Default system text").build();

		var content = chatClient.prompt().call().content();

		assertThat(content).isEqualTo("response");

		Message systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("Default system text");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		content = join(chatClient.prompt().stream().content());

		assertThat(content).isEqualTo("response");

		systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("Default system text");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		// Override the default system text with prompt system
		content = chatClient.prompt()
				.system("Override default system text")
				.call().content();

		assertThat(content).isEqualTo("response");
		systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("Override default system text");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		// Streaming
		content = join(chatClient.prompt()
				.system("Override default system text")
				.stream().content());

		assertThat(content).isEqualTo("response");
		systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("Override default system text");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
	}

	@Test
	public void defaultSystemTextLambda() {

		when(chatModel.call(promptCaptor.capture()))
				.thenReturn(new ChatResponse(List.of(new Generation("response"))));

		when(chatModel.stream(promptCaptor.capture()))
				.thenReturn(
						Flux.generate(() -> new ChatResponse(List.of(new Generation("response"))), (state, sink) -> {
							sink.next(state);
							sink.complete();
							return state;
						}));

		var chatClient = ChatClient.builder(chatModel)
				.defaultSystem(s -> s.text("Default system text {param1}, {param2}")
						.param("param1", "value1")
						.param("param2", "value2"))
				.build();

		var content = chatClient.prompt().call().content();

		assertThat(content).isEqualTo("response");

		Message systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("Default system text value1, value2");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		// Streaming
		content = join(chatClient.prompt().stream().content());

		assertThat(content).isEqualTo("response");

		systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("Default system text value1, value2");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		// Override single default system parameter
		content = chatClient.prompt()
				.system(s -> s.param("param1", "value1New"))
				.call().content();

		assertThat(content).isEqualTo("response");
		systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("Default system text value1New, value2");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		// streaming
		content = join(chatClient.prompt()
				.system(s -> s.param("param1", "value1New"))
				.stream().content());

		assertThat(content).isEqualTo("response");
		systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("Default system text value1New, value2");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		// Override default system text
		content = chatClient.prompt()
				.system(s -> s.text("Override default system text {param3}")
						.param("param3", "value3"))
				.call().content();

		assertThat(content).isEqualTo("response");
		systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("Override default system text value3");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		// Streaming
		content = join(chatClient.prompt()
				.system(s -> s.text("Override default system text {param3}")
						.param("param3", "value3"))
				.stream().content());

		assertThat(content).isEqualTo("response");
		systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("Override default system text value3");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
	}

	@Test
	public void defaultUserText() {

		when(chatModel.call(promptCaptor.capture()))
				.thenReturn(new ChatResponse(List.of(new Generation("response"))));

		var chatClient = ChatClient.builder(chatModel)
				.defaultUser("Default user text").build();

		var content = chatClient.prompt().call().content();

		assertThat(content).isEqualTo("response");

		Message userMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getContent()).isEqualTo("Default user text");
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);

		// Override the default system text with prompt system
		content = chatClient.prompt()
				.user("Override default user text")
				.call().content();

		assertThat(content).isEqualTo("response");
		userMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getContent()).isEqualTo("Override default user text");
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
	}

	@Test
	public void simpleUserPrompt() {
		when(chatModel.call(promptCaptor.capture()))
				.thenReturn(new ChatResponse(List.of(new Generation("response"))));

		assertThat(ChatClient.builder(chatModel).build().prompt().user("User prompt").call().content())
				.isEqualTo("response");

		Message userMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getContent()).isEqualTo("User prompt");
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
	}

	@Test
	public void simpleUserPromptObject() throws MalformedURLException {
		when(chatModel.call(promptCaptor.capture()))
				.thenReturn(new ChatResponse(List.of(new Generation("response"))));

		UserMessage message = new UserMessage("User prompt");
		Prompt prompt = new Prompt(message);
		assertThat(ChatClient.builder(chatModel).build().prompt(prompt).call().content()).isEqualTo("response");

		Message userMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getContent()).isEqualTo("User prompt");
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
	}

	@Test
	public void simpleSystemPrompt() throws MalformedURLException {
		when(chatModel.call(promptCaptor.capture()))
				.thenReturn(new ChatResponse(List.of(new Generation("response"))));

		String response = ChatClient.builder(chatModel).build().prompt().system("System prompt").call().content();

		assertThat(response).isEqualTo("response");

		assertThat(promptCaptor.getValue().getInstructions()).hasSize(2);

		Message systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("System prompt");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		// Is this expected?
		Message userMessage = promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getContent()).isEqualTo("");
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
	}

	@Test
	public void complexCall() throws MalformedURLException {
		when(chatModel.call(promptCaptor.capture()))
				.thenReturn(new ChatResponse(List.of(new Generation("response"))));

		var options = FunctionCallingOptions.builder().build();
		when(chatModel.getDefaultOptions()).thenReturn(options);

		var url = new URL("https://docs.spring.io/spring-ai/reference/1.0-SNAPSHOT/_images/multimodal.test.png");

		// @formatter:off
		ChatClient client = ChatClient.builder(chatModel)
				.defaultSystem("System text")
				.defaultFunctions("function1")
				.build();

		String response = client.prompt()
				.user(u -> u.text("User text {music}").param("music", "Rock").media(MimeTypeUtils.IMAGE_PNG, url))
				.call()
				.content();
		// @formatter:on

		assertThat(response).isEqualTo("response");
		assertThat(promptCaptor.getValue().getInstructions()).hasSize(2);

		Message systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("System text");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		Message userMessage = promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getContent()).isEqualTo("User text Rock");
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getMedia()).hasSize(1);
		assertThat(userMessage.getMedia().iterator().next().getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_PNG);
		assertThat(userMessage.getMedia().iterator().next().getData())
				.isEqualTo("https://docs.spring.io/spring-ai/reference/1.0-SNAPSHOT/_images/multimodal.test.png");

		assertThat(options.getFunctions()).containsExactly("function1");
	}

}
