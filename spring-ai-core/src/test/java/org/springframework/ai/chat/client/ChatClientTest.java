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

package org.springframework.ai.chat.client;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.model.function.FunctionCallingOptionsBuilder;
import org.springframework.ai.model.function.FunctionCallingOptionsBuilder.PortableFunctionCallingOptions;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class ChatClientTest {

	@Mock
	ChatModel chatModel;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	private String join(Flux<String> fluxContent) {
		return fluxContent.collectList().block().stream().collect(Collectors.joining());
	}

	// ChatClient Builder Tests
	@Test
	public void defaultSystemText() {

		when(chatModel.call(promptCaptor.capture()))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		when(chatModel.stream(promptCaptor.capture())).thenReturn(Flux.generate(
				() -> new ChatResponse(List.of(new Generation(new AssistantMessage("response")))), (state, sink) -> {
					sink.next(state);
					sink.complete();
					return state;
				}));

		var chatClient = ChatClient.builder(chatModel).defaultSystem("Default system text").build();

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
		content = chatClient.prompt().system("Override default system text").call().content();

		assertThat(content).isEqualTo("response");
		systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("Override default system text");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		// Streaming
		content = join(chatClient.prompt().system("Override default system text").stream().content());

		assertThat(content).isEqualTo("response");
		systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("Override default system text");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
	}

	@Test
	public void defaultSystemTextLambda() {

		when(chatModel.call(promptCaptor.capture()))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		when(chatModel.stream(promptCaptor.capture())).thenReturn(Flux.generate(
				() -> new ChatResponse(List.of(new Generation(new AssistantMessage("response")))), (state, sink) -> {
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
		content = chatClient.prompt().system(s -> s.param("param1", "value1New")).call().content();

		assertThat(content).isEqualTo("response");
		systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("Default system text value1New, value2");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		// streaming
		content = join(chatClient.prompt().system(s -> s.param("param1", "value1New")).stream().content());

		assertThat(content).isEqualTo("response");
		systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("Default system text value1New, value2");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		// Override default system text
		content = chatClient.prompt()
			.system(s -> s.text("Override default system text {param3}").param("param3", "value3"))
			.call()
			.content();

		assertThat(content).isEqualTo("response");
		systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("Override default system text value3");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		// Streaming
		content = join(chatClient.prompt()
			.system(s -> s.text("Override default system text {param3}").param("param3", "value3"))
			.stream()
			.content());

		assertThat(content).isEqualTo("response");
		systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("Override default system text value3");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
	}

	static Function<String, String> mockFunction = new Function<String, String>() {
		@Override
		public String apply(String s) {
			return s;
		}
	};

	@Test
	public void mutateDefaults() {

		PortableFunctionCallingOptions options = new FunctionCallingOptionsBuilder().build();
		when(chatModel.getDefaultOptions()).thenReturn(options);

		when(chatModel.call(promptCaptor.capture()))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		when(chatModel.stream(promptCaptor.capture())).thenReturn(Flux.generate(
				() -> new ChatResponse(List.of(new Generation(new AssistantMessage("response")))), (state, sink) -> {
					sink.next(state);
					sink.complete();
					return state;
				}));

		// @formatter:off
		var chatClient = ChatClient.builder(chatModel)
				.defaultSystem(s -> s.text("Default system text {param1}, {param2}")
						.param("param1", "value1")
						.param("param2", "value2"))
				.defaultFunctions("fun1", "fun2")
				.defaultFunction("fun3", "fun3description", mockFunction)
				.defaultUser(u -> u.text("Default user text {uparam1}, {uparam2}")
						.param("uparam1", "value1")
						.param("uparam2", "value2")
						.media(MimeTypeUtils.IMAGE_JPEG,
								new DefaultResourceLoader().getResource("classpath:/bikes.json")))
				.build();
		// @formatter:on

		var content = chatClient.prompt().call().content();

		assertThat(content).isEqualTo("response");

		Prompt prompt = promptCaptor.getValue();

		Message systemMessage = prompt.getInstructions().get(0);
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getContent()).isEqualTo("Default system text value1, value2");

		UserMessage userMessage = (UserMessage) prompt.getInstructions().get(1);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getContent()).isEqualTo("Default user text value1, value2");
		assertThat(userMessage.getMedia()).hasSize(1);
		assertThat(userMessage.getMedia().iterator().next().getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_JPEG);

		var fco = (FunctionCallingOptions) prompt.getOptions();

		assertThat(fco.getFunctions()).containsExactly("fun1", "fun2");
		assertThat(fco.getFunctionCallbacks().iterator().next().getName()).isEqualTo("fun3");

		// Streaming
		content = join(chatClient.prompt().stream().content());

		assertThat(content).isEqualTo("response");

		prompt = promptCaptor.getValue();

		systemMessage = prompt.getInstructions().get(0);
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getContent()).isEqualTo("Default system text value1, value2");

		userMessage = (UserMessage) prompt.getInstructions().get(1);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getContent()).isEqualTo("Default user text value1, value2");
		assertThat(userMessage.getMedia()).hasSize(1);
		assertThat(userMessage.getMedia().iterator().next().getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_JPEG);

		fco = (FunctionCallingOptions) prompt.getOptions();

		assertThat(fco.getFunctions()).containsExactly("fun1", "fun2");
		assertThat(fco.getFunctionCallbacks().iterator().next().getName()).isEqualTo("fun3");

		// mutate builder
		// @formatter:off
		chatClient = chatClient.mutate()
				.defaultSystem("Mutated default system text {param1}, {param2}")
				.defaultFunctions("fun4")
				.defaultUser("Mutated default user text {uparam1}, {uparam2}")
				.build();
		// @formatter:on

		content = chatClient.prompt().call().content();

		assertThat(content).isEqualTo("response");

		prompt = promptCaptor.getValue();

		systemMessage = prompt.getInstructions().get(0);
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getContent()).isEqualTo("Mutated default system text value1, value2");

		userMessage = (UserMessage) prompt.getInstructions().get(1);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getContent()).isEqualTo("Mutated default user text value1, value2");
		assertThat(userMessage.getMedia()).hasSize(1);
		assertThat(userMessage.getMedia().iterator().next().getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_JPEG);

		fco = (FunctionCallingOptions) prompt.getOptions();

		assertThat(fco.getFunctions()).containsExactly("fun1", "fun2", "fun4");
		assertThat(fco.getFunctionCallbacks().iterator().next().getName()).isEqualTo("fun3");

		// Streaming
		content = join(chatClient.prompt().stream().content());

		assertThat(content).isEqualTo("response");

		prompt = promptCaptor.getValue();

		systemMessage = prompt.getInstructions().get(0);
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getContent()).isEqualTo("Mutated default system text value1, value2");

		userMessage = (UserMessage) prompt.getInstructions().get(1);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getContent()).isEqualTo("Mutated default user text value1, value2");
		assertThat(userMessage.getMedia()).hasSize(1);
		assertThat(userMessage.getMedia().iterator().next().getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_JPEG);

		fco = (FunctionCallingOptions) prompt.getOptions();

		assertThat(fco.getFunctions()).containsExactly("fun1", "fun2", "fun4");
		assertThat(fco.getFunctionCallbacks().iterator().next().getName()).isEqualTo("fun3");

	}

	@Test
	public void mutatePrompt() {

		PortableFunctionCallingOptions options = new FunctionCallingOptionsBuilder().build();
		when(chatModel.getDefaultOptions()).thenReturn(options);

		when(chatModel.call(promptCaptor.capture()))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		when(chatModel.stream(promptCaptor.capture())).thenReturn(Flux.generate(
				() -> new ChatResponse(List.of(new Generation(new AssistantMessage("response")))), (state, sink) -> {
					sink.next(state);
					sink.complete();
					return state;
				}));
		// @formatter:off
		var chatClient = ChatClient.builder(chatModel)
				.defaultSystem(s -> s.text("Default system text {param1}, {param2}")
						.param("param1", "value1")
						.param("param2", "value2"))
				.defaultFunctions("fun1", "fun2")
				.defaultFunction("fun3", "fun3description", mockFunction)
				.defaultUser(u -> u.text("Default user text {uparam1}, {uparam2}")
						.param("uparam1", "value1")
						.param("uparam2", "value2")
						.media(MimeTypeUtils.IMAGE_JPEG,
								new DefaultResourceLoader().getResource("classpath:/bikes.json")))
				.build();

		var content = chatClient
				.prompt()
					.system("New default system text {param1}, {param2}")
					.user(u -> u.param("uparam1", "userValue1")
						.param("uparam2", "userValue2"))
					.functions("fun5")
				.mutate().build() // mutate and build new prompt
				.prompt().call().content();
		// @formatter:on

		assertThat(content).isEqualTo("response");

		Prompt prompt = promptCaptor.getValue();

		Message systemMessage = prompt.getInstructions().get(0);
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getContent()).isEqualTo("New default system text value1, value2");

		UserMessage userMessage = (UserMessage) prompt.getInstructions().get(1);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getContent()).isEqualTo("Default user text userValue1, userValue2");
		assertThat(userMessage.getMedia()).hasSize(1);
		assertThat(userMessage.getMedia().iterator().next().getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_JPEG);

		var fco = (FunctionCallingOptions) prompt.getOptions();

		assertThat(fco.getFunctions()).containsExactly("fun1", "fun2", "fun5");
		assertThat(fco.getFunctionCallbacks().iterator().next().getName()).isEqualTo("fun3");

		// Streaming
		// @formatter:off
		content = join(chatClient
					.prompt()
						.system("New default system text {param1}, {param2}")
						.user(u -> u.param("uparam1", "userValue1")
							.param("uparam2", "userValue2"))
						.functions("fun5")
					.mutate().build() // mutate and build new prompt
					.prompt().stream().content());
		// @formatter:on

		assertThat(content).isEqualTo("response");

		prompt = promptCaptor.getValue();

		systemMessage = prompt.getInstructions().get(0);
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getContent()).isEqualTo("New default system text value1, value2");

		userMessage = (UserMessage) prompt.getInstructions().get(1);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getContent()).isEqualTo("Default user text userValue1, userValue2");
		assertThat(userMessage.getMedia()).hasSize(1);
		assertThat(userMessage.getMedia().iterator().next().getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_JPEG);

		fco = (FunctionCallingOptions) prompt.getOptions();

		assertThat(fco.getFunctions()).containsExactly("fun1", "fun2", "fun5");
		assertThat(fco.getFunctionCallbacks().iterator().next().getName()).isEqualTo("fun3");
	}

	@Test
	public void defaultUserText() {

		when(chatModel.call(promptCaptor.capture()))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		var chatClient = ChatClient.builder(chatModel).defaultUser("Default user text").build();

		var content = chatClient.prompt().call().content();

		assertThat(content).isEqualTo("response");

		Message userMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getContent()).isEqualTo("Default user text");
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);

		// Override the default system text with prompt system
		content = chatClient.prompt().user("Override default user text").call().content();

		assertThat(content).isEqualTo("response");
		userMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getContent()).isEqualTo("Override default user text");
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
	}

	@Test
	public void simpleUserPromptAsString() {
		when(chatModel.call(promptCaptor.capture()))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		assertThat(ChatClient.builder(chatModel).build().prompt("User prompt").call().content()).isEqualTo("response");

		Message userMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getContent()).isEqualTo("User prompt");
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
	}

	@Test
	public void simpleUserPrompt() {
		when(chatModel.call(promptCaptor.capture()))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		assertThat(ChatClient.builder(chatModel).build().prompt().user("User prompt").call().content())
			.isEqualTo("response");

		Message userMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getContent()).isEqualTo("User prompt");
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
	}

	@Test
	public void simpleUserPromptObject() throws MalformedURLException {
		when(chatModel.call(promptCaptor.capture()))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		var media = new Media(MimeTypeUtils.IMAGE_JPEG, new DefaultResourceLoader().getResource("classpath:/bikes.json"));

		UserMessage message = new UserMessage("User prompt", List.of(media));
		Prompt prompt = new Prompt(message);
		assertThat(ChatClient.builder(chatModel).build().prompt(prompt).call().content()).isEqualTo("response");

		assertThat(promptCaptor.getValue().getInstructions()).hasSize(1);
		Message userMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getContent()).isEqualTo("User prompt");
		assertThat(((UserMessage) userMessage).getMedia()).hasSize(1);
	}

	@Test
	public void simpleSystemPrompt() throws MalformedURLException {
		when(chatModel.call(promptCaptor.capture()))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		String response = ChatClient.builder(chatModel).build().prompt().system("System prompt").call().content();

		assertThat(response).isEqualTo("response");

		assertThat(promptCaptor.getValue().getInstructions()).hasSize(1);

		Message systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("System prompt");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
	}

	@Test
	public void complexCall() throws MalformedURLException {
		when(chatModel.call(promptCaptor.capture()))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

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

		UserMessage userMessage = (UserMessage) promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getContent()).isEqualTo("User text Rock");
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getMedia()).hasSize(1);
		assertThat(userMessage.getMedia().iterator().next().getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_PNG);
		assertThat(userMessage.getMedia().iterator().next().getData())
			.isEqualTo("https://docs.spring.io/spring-ai/reference/1.0-SNAPSHOT/_images/multimodal.test.png");

		FunctionCallingOptions runtieOptions = (FunctionCallingOptions) promptCaptor.getValue().getOptions();

		assertThat(runtieOptions.getFunctions()).containsExactly("function1");
		assertThat(options.getFunctions()).isEmpty();
	}

}
