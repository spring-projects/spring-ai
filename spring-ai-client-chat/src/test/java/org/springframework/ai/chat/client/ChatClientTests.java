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

package org.springframework.ai.chat.client;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
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
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.ai.chat.messages.MessageType.USER;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
@ExtendWith(MockitoExtension.class)
public class ChatClientTests {

	static Function<String, String> mockFunction = s -> s;

	@Mock
	ChatModel chatModel;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	private String join(Flux<String> fluxContent) {
		return fluxContent.collectList().block().stream().collect(Collectors.joining());
	}

	// ChatClient Builder Tests
	@Test
	void defaultSystemText() {

		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		given(this.chatModel.stream(this.promptCaptor.capture())).willReturn(Flux.generate(
				() -> new ChatResponse(List.of(new Generation(new AssistantMessage("response")))), (state, sink) -> {
					sink.next(state);
					sink.complete();
					return state;
				}));

		var chatClient = ChatClient.builder(this.chatModel).defaultSystem("Default system text").build();

		var content = chatClient.prompt("What's Spring AI?").call().content();

		assertThat(content).isEqualTo("response");

		Message systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("Default system text");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getMetadata()).hasSize(1).containsEntry("messageType", MessageType.SYSTEM);

		content = join(chatClient.prompt("What's Spring AI?").stream().content());

		assertThat(content).isEqualTo("response");

		systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("Default system text");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getMetadata()).hasSize(1).containsEntry("messageType", MessageType.SYSTEM);

		// Override the default system text with prompt system
		content = chatClient.prompt("What's Spring AI?").system("Override default system text").call().content();

		assertThat(content).isEqualTo("response");
		systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("Override default system text");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getMetadata()).hasSize(1).containsEntry("messageType", MessageType.SYSTEM);

		// Streaming
		content = join(
				chatClient.prompt("What's Spring AI?").system("Override default system text").stream().content());

		assertThat(content).isEqualTo("response");
		systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("Override default system text");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getMetadata()).hasSize(1).containsEntry("messageType", MessageType.SYSTEM);
	}

	@Test
	void defaultSystemTextLambda() {

		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		given(this.chatModel.stream(this.promptCaptor.capture())).willReturn(Flux.generate(
				() -> new ChatResponse(List.of(new Generation(new AssistantMessage("response")))), (state, sink) -> {
					sink.next(state);
					sink.complete();
					return state;
				}));

		var chatClient = ChatClient.builder(this.chatModel)
			.defaultSystem(s -> s.text("Default system text {param1}, {param2}")
				.param("param1", "value1")
				.param("param2", "value2")
				.metadata("metadata1", "svalue1")
				.metadata("metadata2", "svalue2"))
			.build();

		var content = chatClient.prompt("What's Spring AI?").call().content();

		assertThat(content).isEqualTo("response");

		Message systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("Default system text value1, value2");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getMetadata()).hasSize(3)
			.containsEntry("messageType", MessageType.SYSTEM)
			.containsEntry("metadata1", "svalue1")
			.containsEntry("metadata2", "svalue2");

		// Streaming
		content = join(chatClient.prompt("What's Spring AI?").stream().content());

		assertThat(content).isEqualTo("response");

		systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("Default system text value1, value2");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getMetadata()).hasSize(3)
			.containsEntry("messageType", MessageType.SYSTEM)
			.containsEntry("metadata1", "svalue1")
			.containsEntry("metadata2", "svalue2");

		// Override single default system parameter
		content = chatClient.prompt("What's Spring AI?").system(s -> s.param("param1", "value1New")).call().content();

		assertThat(content).isEqualTo("response");
		systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("Default system text value1New, value2");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getMetadata()).hasSize(3)
			.containsEntry("messageType", MessageType.SYSTEM)
			.containsEntry("metadata1", "svalue1")
			.containsEntry("metadata2", "svalue2");

		// Override default system metadata
		content = chatClient.prompt("What's Spring AI?")
			.system(s -> s.metadata("metadata1", "svalue1New"))
			.call()
			.content();
		assertThat(content).isEqualTo("response");
		systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("Default system text value1, value2");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getMetadata()).hasSize(3)
			.containsEntry("messageType", MessageType.SYSTEM)
			.containsEntry("metadata1", "svalue1New")
			.containsEntry("metadata2", "svalue2");

		// streaming
		content = join(
				chatClient.prompt("What's Spring AI?").system(s -> s.param("param1", "value1New")).stream().content());

		assertThat(content).isEqualTo("response");
		systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("Default system text value1New, value2");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		// Override default system text
		content = chatClient.prompt("What's Spring AI?")
			.system(s -> s.text("Override default system text {param3}").param("param3", "value3"))
			.call()
			.content();

		assertThat(content).isEqualTo("response");
		systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("Override default system text value3");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getMetadata()).hasSize(3)
			.containsEntry("messageType", MessageType.SYSTEM)
			.containsEntry("metadata1", "svalue1")
			.containsEntry("metadata2", "svalue2");

		// Streaming
		content = join(chatClient.prompt("What's Spring AI?")
			.system(s -> s.text("Override default system text {param3}")
				.param("param3", "value3")
				.metadata("metadata3", "svalue3"))
			.stream()
			.content());

		assertThat(content).isEqualTo("response");
		systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("Override default system text value3");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getMetadata()).hasSize(4)
			.containsEntry("messageType", MessageType.SYSTEM)
			.containsEntry("metadata1", "svalue1")
			.containsEntry("metadata2", "svalue2")
			.containsEntry("metadata3", "svalue3");
	}

	@Test
	void mutateDefaults() {

		ToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		given(this.chatModel.getDefaultOptions()).willReturn(options);

		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		given(this.chatModel.stream(this.promptCaptor.capture())).willReturn(Flux.generate(
				() -> new ChatResponse(List.of(new Generation(new AssistantMessage("response")))), (state, sink) -> {
					sink.next(state);
					sink.complete();
					return state;
				}));

		// @formatter:off
		var chatClient = ChatClient.builder(this.chatModel)
				.defaultSystem(s -> s.text("Default system text {param1}, {param2}")
						.param("param1", "value1")
						.param("param2", "value2")
						.metadata("smetadata1", "svalue1")
						.metadata("smetadata2", "svalue2"))
				.defaultToolNames("fun1", "fun2")
				.defaultToolCallbacks(FunctionToolCallback.builder("fun3", mockFunction)
						.description("fun3description")
						.inputType(String.class)
						.build())
				.defaultUser(u -> u.text("Default user text {uparam1}, {uparam2}")
						.param("uparam1", "value1")
						.param("uparam2", "value2")
						.media(MimeTypeUtils.IMAGE_JPEG,
								new DefaultResourceLoader().getResource("classpath:/bikes.json"))
						.metadata("umetadata1", "udata1")
						.metadata("umetadata2", "udata2")
				)
				.build();
		// @formatter:on

		var content = chatClient.prompt().call().content();

		assertThat(content).isEqualTo("response");

		Prompt prompt = this.promptCaptor.getValue();

		Message systemMessage = prompt.getInstructions().get(0);
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getText()).isEqualTo("Default system text value1, value2");
		assertThat(systemMessage.getMetadata()).hasSize(3)
			.containsEntry("messageType", MessageType.SYSTEM)
			.containsEntry("smetadata1", "svalue1")
			.containsEntry("smetadata2", "svalue2");

		UserMessage userMessage = (UserMessage) prompt.getInstructions().get(1);
		assertThat(userMessage.getMessageType()).isEqualTo(USER);
		assertThat(userMessage.getText()).isEqualTo("Default user text value1, value2");
		assertThat(userMessage.getMedia()).hasSize(1);
		assertThat(userMessage.getMedia().iterator().next().getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_JPEG);
		assertThat(userMessage.getMetadata()).hasSize(3)
			.containsEntry("messageType", USER)
			.containsEntry("umetadata1", "udata1")
			.containsEntry("umetadata2", "udata2");

		var fco = (ToolCallingChatOptions) prompt.getOptions();

		assertThat(fco.getToolNames()).containsExactlyInAnyOrder("fun1", "fun2");
		assertThat(fco.getToolCallbacks().iterator().next().getToolDefinition().name()).isEqualTo("fun3");

		// Streaming
		content = join(chatClient.prompt().stream().content());

		assertThat(content).isEqualTo("response");

		prompt = this.promptCaptor.getValue();

		systemMessage = prompt.getInstructions().get(0);
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getText()).isEqualTo("Default system text value1, value2");
		assertThat(systemMessage.getMetadata()).hasSize(3)
			.containsEntry("messageType", MessageType.SYSTEM)
			.containsEntry("smetadata1", "svalue1")
			.containsEntry("smetadata2", "svalue2");

		userMessage = (UserMessage) prompt.getInstructions().get(1);
		assertThat(userMessage.getMessageType()).isEqualTo(USER);
		assertThat(userMessage.getText()).isEqualTo("Default user text value1, value2");
		assertThat(userMessage.getMedia()).hasSize(1);
		assertThat(userMessage.getMedia().iterator().next().getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_JPEG);
		assertThat(userMessage.getMetadata()).hasSize(3)
			.containsEntry("messageType", USER)
			.containsEntry("umetadata1", "udata1")
			.containsEntry("umetadata2", "udata2");

		fco = (ToolCallingChatOptions) prompt.getOptions();

		assertThat(fco.getToolNames()).containsExactlyInAnyOrder("fun1", "fun2");
		assertThat(fco.getToolCallbacks().iterator().next().getToolDefinition().name()).isEqualTo("fun3");

		// mutate builder
		// @formatter:off
		chatClient = chatClient.mutate()
				.defaultSystem("Mutated default system text {param1}, {param2}")
				.defaultToolNames("fun4")
				.defaultUser("Mutated default user text {uparam1}, {uparam2}")
				.build();
		// @formatter:on

		content = chatClient.prompt().call().content();

		assertThat(content).isEqualTo("response");

		prompt = this.promptCaptor.getValue();

		systemMessage = prompt.getInstructions().get(0);
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getText()).isEqualTo("Mutated default system text value1, value2");
		assertThat(systemMessage.getMetadata()).hasSize(3)
			.containsEntry("messageType", MessageType.SYSTEM)
			.containsEntry("smetadata1", "svalue1")
			.containsEntry("smetadata2", "svalue2");

		userMessage = (UserMessage) prompt.getInstructions().get(1);
		assertThat(userMessage.getMessageType()).isEqualTo(USER);
		assertThat(userMessage.getText()).isEqualTo("Mutated default user text value1, value2");
		assertThat(userMessage.getMedia()).hasSize(1);
		assertThat(userMessage.getMedia().iterator().next().getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_JPEG);
		assertThat(userMessage.getMetadata()).hasSize(3)
			.containsEntry("messageType", USER)
			.containsEntry("umetadata1", "udata1")
			.containsEntry("umetadata2", "udata2");

		fco = (ToolCallingChatOptions) prompt.getOptions();

		assertThat(fco.getToolNames()).containsExactlyInAnyOrder("fun1", "fun2", "fun4");
		assertThat(fco.getToolCallbacks().iterator().next().getToolDefinition().name()).isEqualTo("fun3");

		// Streaming
		content = join(chatClient.prompt().stream().content());

		assertThat(content).isEqualTo("response");

		prompt = this.promptCaptor.getValue();

		systemMessage = prompt.getInstructions().get(0);
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getText()).isEqualTo("Mutated default system text value1, value2");
		assertThat(systemMessage.getMetadata()).hasSize(3)
			.containsEntry("messageType", MessageType.SYSTEM)
			.containsEntry("smetadata1", "svalue1")
			.containsEntry("smetadata2", "svalue2");

		userMessage = (UserMessage) prompt.getInstructions().get(1);
		assertThat(userMessage.getMessageType()).isEqualTo(USER);
		assertThat(userMessage.getText()).isEqualTo("Mutated default user text value1, value2");
		assertThat(userMessage.getMedia()).hasSize(1);
		assertThat(userMessage.getMedia().iterator().next().getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_JPEG);
		assertThat(userMessage.getMetadata()).hasSize(3)
			.containsEntry("messageType", USER)
			.containsEntry("umetadata1", "udata1")
			.containsEntry("umetadata2", "udata2");

		fco = (ToolCallingChatOptions) prompt.getOptions();

		assertThat(fco.getToolNames()).containsExactlyInAnyOrder("fun1", "fun2", "fun4");
		assertThat(fco.getToolCallbacks().iterator().next().getToolDefinition().name()).isEqualTo("fun3");

	}

	@Test
	void mutatePrompt() {

		ToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		given(this.chatModel.getDefaultOptions()).willReturn(options);

		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		given(this.chatModel.stream(this.promptCaptor.capture())).willReturn(Flux.generate(
				() -> new ChatResponse(List.of(new Generation(new AssistantMessage("response")))), (state, sink) -> {
					sink.next(state);
					sink.complete();
					return state;
				}));
		// @formatter:off
		var chatClient = ChatClient.builder(this.chatModel)
				.defaultSystem(s -> s.text("Default system text {param1}, {param2}")
						.param("param1", "value1")
						.param("param2", "value2")
						.metadata("smetadata1", "svalue1")
						.metadata("smetadata2", "svalue2"))
				.defaultToolNames("fun1", "fun2")
				.defaultToolCallbacks(FunctionToolCallback.builder("fun3", mockFunction)
						.description("fun3description")
						.inputType(String.class)
						.build())
				.defaultUser(u -> u.text("Default user text {uparam1}, {uparam2}")
						.param("uparam1", "value1")
						.param("uparam2", "value2")
						.metadata("umetadata1", "udata1")
						.metadata("umetadata2", "udata2")
						.media(MimeTypeUtils.IMAGE_JPEG,
								new DefaultResourceLoader().getResource("classpath:/bikes.json")))
				.build();

		var content = chatClient
				.prompt()
					.system("New default system text {param1}, {param2}")
					.user(u -> u.param("uparam1", "userValue1")
						.param("uparam2", "userValue2")
						.metadata("umetadata2", "userData2"))
					.toolNames("fun5")
				.mutate().build() // mutate and build new prompt
				.prompt().call().content();
		// @formatter:on

		assertThat(content).isEqualTo("response");

		Prompt prompt = this.promptCaptor.getValue();

		Message systemMessage = prompt.getInstructions().get(0);
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getText()).isEqualTo("New default system text value1, value2");
		assertThat(systemMessage.getMetadata()).hasSize(3)
			.containsEntry("messageType", MessageType.SYSTEM)
			.containsEntry("smetadata1", "svalue1")
			.containsEntry("smetadata2", "svalue2");

		UserMessage userMessage = (UserMessage) prompt.getInstructions().get(1);
		assertThat(userMessage.getMessageType()).isEqualTo(USER);
		assertThat(userMessage.getText()).isEqualTo("Default user text userValue1, userValue2");
		assertThat(userMessage.getMedia()).hasSize(1);
		assertThat(userMessage.getMedia().iterator().next().getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_JPEG);
		assertThat(userMessage.getMetadata()).hasSize(3)
			.containsEntry("messageType", USER)
			.containsEntry("umetadata1", "udata1")
			.containsEntry("umetadata2", "userData2");

		var tco = (ToolCallingChatOptions) prompt.getOptions();

		assertThat(tco.getToolNames()).containsExactlyInAnyOrder("fun1", "fun2", "fun5");
		assertThat(tco.getToolCallbacks().iterator().next().getToolDefinition().name()).isEqualTo("fun3");

		// Streaming
		// @formatter:off
		content = join(chatClient
					.prompt()
						.system("New default system text {param1}, {param2}")
						.user(u -> u.param("uparam1", "userValue1")
							.param("uparam2", "userValue2")
							.metadata("umetadata2", "userData2"))
						.toolNames("fun5")
					.mutate().build() // mutate and build new prompt
					.prompt().stream().content());
		// @formatter:on

		assertThat(content).isEqualTo("response");

		prompt = this.promptCaptor.getValue();

		systemMessage = prompt.getInstructions().get(0);
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getText()).isEqualTo("New default system text value1, value2");
		assertThat(systemMessage.getMetadata()).hasSize(3)
			.containsEntry("messageType", MessageType.SYSTEM)
			.containsEntry("smetadata1", "svalue1")
			.containsEntry("smetadata2", "svalue2");

		userMessage = (UserMessage) prompt.getInstructions().get(1);
		assertThat(userMessage.getMessageType()).isEqualTo(USER);
		assertThat(userMessage.getText()).isEqualTo("Default user text userValue1, userValue2");
		assertThat(userMessage.getMedia()).hasSize(1);
		assertThat(userMessage.getMedia().iterator().next().getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_JPEG);
		assertThat(userMessage.getMetadata()).hasSize(3)
			.containsEntry("messageType", USER)
			.containsEntry("umetadata1", "udata1")
			.containsEntry("umetadata2", "userData2");

		var tcoptions = (ToolCallingChatOptions) prompt.getOptions();

		assertThat(tcoptions.getToolNames()).containsExactlyInAnyOrder("fun1", "fun2", "fun5");
		assertThat(tcoptions.getToolCallbacks().iterator().next().getToolDefinition().name()).isEqualTo("fun3");
	}

	@Test
	void defaultUserText() {

		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		var chatClient = ChatClient.builder(this.chatModel).defaultUser("Default user text").build();

		var content = chatClient.prompt().call().content();

		assertThat(content).isEqualTo("response");

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getText()).isEqualTo("Default user text");
		assertThat(userMessage.getMessageType()).isEqualTo(USER);
		assertThat(userMessage.getMetadata()).hasSize(1).containsEntry("messageType", USER);

		// Override the default system text with prompt system
		content = chatClient.prompt().user("Override default user text").call().content();

		assertThat(content).isEqualTo("response");
		userMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getText()).isEqualTo("Override default user text");
		assertThat(userMessage.getMessageType()).isEqualTo(USER);
		assertThat(userMessage.getMetadata()).hasSize(1).containsEntry("messageType", USER);
	}

	@Test
	void simpleUserPromptAsString() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		assertThat(ChatClient.builder(this.chatModel).build().prompt("User prompt").call().content())
			.isEqualTo("response");

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getText()).isEqualTo("User prompt");
		assertThat(userMessage.getMessageType()).isEqualTo(USER);
		assertThat(userMessage.getMetadata()).hasSize(1).containsEntry("messageType", USER);
	}

	@Test
	void simpleUserPrompt() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		assertThat(ChatClient.builder(this.chatModel).build().prompt().user("User prompt").call().content())
			.isEqualTo("response");

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getText()).isEqualTo("User prompt");
		assertThat(userMessage.getMessageType()).isEqualTo(USER);
		assertThat(userMessage.getMetadata()).hasSize(1).containsEntry("messageType", USER);
	}

	@Test
	void simpleUserPromptObject() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		var media = new Media(MimeTypeUtils.IMAGE_JPEG,
				new DefaultResourceLoader().getResource("classpath:/bikes.json"));

		UserMessage message = UserMessage.builder()
			.text("User prompt")
			.media(List.of(media))
			.metadata(Map.of("umetadata1", "udata1"))
			.build();
		Prompt prompt = new Prompt(message);
		assertThat(ChatClient.builder(this.chatModel).build().prompt(prompt).call().content()).isEqualTo("response");

		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(1);
		Message userMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getMessageType()).isEqualTo(USER);
		assertThat(userMessage.getText()).isEqualTo("User prompt");
		assertThat(((UserMessage) userMessage).getMedia()).hasSize(1);
		assertThat(((UserMessage) userMessage).getMetadata()).hasSize(2)
			.containsEntry("messageType", USER)
			.containsEntry("umetadata1", "udata1");
	}

	@Test
	void simpleSystemPrompt() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		String response = ChatClient.builder(this.chatModel)
			.build()
			.prompt("What's Spring AI?")
			.system("System prompt")
			.call()
			.content();

		assertThat(response).isEqualTo("response");

		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(2);

		Message systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("System prompt");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getMetadata()).hasSize(1).containsEntry("messageType", MessageType.SYSTEM);
	}

	@Test
	void complexCall() throws MalformedURLException {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		var options = ToolCallingChatOptions.builder().build();
		given(this.chatModel.getDefaultOptions()).willReturn(options);

		var url = new URL("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png");

		// @formatter:off
		ChatClient client = ChatClient.builder(this.chatModel)
				.defaultSystem("System text")
				.defaultToolNames("function1")
				.build();

		String response = client.prompt()
				.user(u -> u.text("User text {music}").param("music", "Rock").media(MimeTypeUtils.IMAGE_PNG, url).metadata(Map.of("umetadata1", "udata1")))
				.call()
				.content();
		// @formatter:on

		assertThat(response).isEqualTo("response");
		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(2);

		Message systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("System text");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		UserMessage userMessage = (UserMessage) this.promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getText()).isEqualTo("User text Rock");
		assertThat(userMessage.getMessageType()).isEqualTo(USER);
		assertThat(userMessage.getMedia()).hasSize(1);
		assertThat(userMessage.getMedia().iterator().next().getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_PNG);
		assertThat(userMessage.getMedia().iterator().next().getData())
			.isEqualTo("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png");
		assertThat(userMessage.getMetadata()).hasSize(2)
			.containsEntry("messageType", USER)
			.containsEntry("umetadata1", "udata1");

		ToolCallingChatOptions runtieOptions = (ToolCallingChatOptions) this.promptCaptor.getValue().getOptions();

		assertThat(runtieOptions.getToolNames()).containsExactly("function1");
		assertThat(options.getToolNames()).isEmpty();
	}

	// Constructors

	@Test
	void whenCreateAndChatModelIsNullThenThrow() {
		assertThatThrownBy(() -> ChatClient.create(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("chatModel cannot be null");
	}

	@Test
	void whenCreateAndObservationRegistryIsNullThenThrow() {
		assertThatThrownBy(() -> ChatClient.create(this.chatModel, null, null, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("observationRegistry cannot be null");
	}

	@Test
	void whenBuilderAndChatModelIsNullThenThrow() {
		assertThatThrownBy(() -> ChatClient.builder(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("chatModel cannot be null");
	}

	@Test
	void whenBuilderAndObservationRegistryIsNullThenThrow() {
		assertThatThrownBy(() -> ChatClient.builder(this.chatModel, null, null, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("observationRegistry cannot be null");
	}

	// Prompt Tests - User

	@Test
	void whenPromptWithStringContent() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		var chatClient = ChatClient.builder(this.chatModel).build();
		var content = chatClient.prompt("my question").call().content();

		assertThat(content).isEqualTo("response");

		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(1);
		var userMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getText()).isEqualTo("my question");
		assertThat(userMessage.getMessageType()).isEqualTo(USER);
	}

	@Test
	void whenPromptWithMessages() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		var chatClient = ChatClient.builder(this.chatModel).build();
		var prompt = new Prompt(new SystemMessage("instructions"), UserMessage.builder().text("my question").build());
		var content = chatClient.prompt(prompt).call().content();

		assertThat(content).isEqualTo("response");

		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(2);
		var userMessage = this.promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getText()).isEqualTo("my question");
		assertThat(userMessage.getMessageType()).isEqualTo(USER);
		assertThat(userMessage.getMetadata()).hasSize(1).containsEntry("messageType", USER);
	}

	@Test
	void whenPromptWithStringContentAndUserText() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		var chatClient = ChatClient.builder(this.chatModel).build();
		var content = chatClient.prompt("my question").user("another question").call().content();

		assertThat(content).isEqualTo("response");

		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(2);
		var userMessage = this.promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getText()).isEqualTo("another question");
		assertThat(userMessage.getMessageType()).isEqualTo(USER);
		assertThat(userMessage.getMetadata()).hasSize(1).containsEntry("messageType", USER);
	}

	@Test
	void whenPromptWithHistoryAndUserText() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		var chatClient = ChatClient.builder(this.chatModel).build();
		var prompt = new Prompt(new UserMessage("my question"), new AssistantMessage("your answer"));
		var content = chatClient.prompt(prompt).user("another question").call().content();

		assertThat(content).isEqualTo("response");

		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(3);
		var userMessage = this.promptCaptor.getValue().getInstructions().get(2);
		assertThat(userMessage.getText()).isEqualTo("another question");
		assertThat(userMessage.getMessageType()).isEqualTo(USER);
		assertThat(userMessage.getMetadata()).hasSize(1).containsEntry("messageType", USER);
	}

	@Test
	void whenPromptWithUserMessageAndUserText() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		var chatClient = ChatClient.builder(this.chatModel).build();
		var prompt = new Prompt(new UserMessage("my question"));
		var content = chatClient.prompt(prompt).user("another question").call().content();

		assertThat(content).isEqualTo("response");

		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(2);
		var userMessage = this.promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getText()).isEqualTo("another question");
		assertThat(userMessage.getMessageType()).isEqualTo(USER);
		assertThat(userMessage.getMetadata()).hasSize(1).containsEntry("messageType", USER);
	}

	@Test
	void whenMessagesWithHistoryAndUserText() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		var chatClient = ChatClient.builder(this.chatModel).build();
		List<Message> messages = List.of(new UserMessage("my question"), new AssistantMessage("your answer"));
		var content = chatClient.prompt().messages(messages).user("another question").call().content();

		assertThat(content).isEqualTo("response");

		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(3);
		var userMessage = this.promptCaptor.getValue().getInstructions().get(2);
		assertThat(userMessage.getText()).isEqualTo("another question");
		assertThat(userMessage.getMessageType()).isEqualTo(USER);
		assertThat(userMessage.getMetadata()).hasSize(1).containsEntry("messageType", USER);
	}

	@Test
	void whenMessagesWithUserMessageAndUserText() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		var chatClient = ChatClient.builder(this.chatModel).build();
		List<Message> messages = List.of(new UserMessage("my question"));
		var content = chatClient.prompt().messages(messages).user("another question").call().content();

		assertThat(content).isEqualTo("response");

		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(2);
		var userMessage = this.promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getText()).isEqualTo("another question");
		assertThat(userMessage.getMessageType()).isEqualTo(USER);
		assertThat(userMessage.getMetadata()).hasSize(1).containsEntry("messageType", USER);
	}

	// Prompt Tests - System

	@Test
	void whenPromptWithMessagesAndSystemText() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		var chatClient = ChatClient.builder(this.chatModel).build();
		var prompt = new Prompt(new UserMessage("my question"), new AssistantMessage("your answer"));
		var content = chatClient.prompt(prompt).system("instructions").user("another question").call().content();

		assertThat(content).isEqualTo("response");

		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(4);
		var systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("instructions");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getMetadata()).hasSize(1).containsEntry("messageType", MessageType.SYSTEM);
	}

	@Test
	void whenPromptWithSystemMessageAndNoSystemText() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		var chatClient = ChatClient.builder(this.chatModel).build();
		var prompt = new Prompt(new SystemMessage("instructions"), new UserMessage("my question"));
		var content = chatClient.prompt(prompt).user("another question").call().content();

		assertThat(content).isEqualTo("response");

		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(3);
		var systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("instructions");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getMetadata()).hasSize(1).containsEntry("messageType", MessageType.SYSTEM);
	}

	@Test
	void whenPromptWithSystemMessageAndSystemText() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		var chatClient = ChatClient.builder(this.chatModel).build();
		var prompt = new Prompt(new SystemMessage("instructions"), new UserMessage("my question"));
		var content = chatClient.prompt(prompt).system("other instructions").user("another question").call().content();

		assertThat(content).isEqualTo("response");

		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(4);
		var systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("other instructions");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getMetadata()).hasSize(1).containsEntry("messageType", MessageType.SYSTEM);
	}

	@Test
	void whenMessagesAndSystemText() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		var chatClient = ChatClient.builder(this.chatModel).build();
		List<Message> messages = List.of(new UserMessage("my question"), new AssistantMessage("your answer"));
		var content = chatClient.prompt()
			.messages(messages)
			.system("instructions")
			.user("another question")
			.call()
			.content();

		assertThat(content).isEqualTo("response");

		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(4);
		var systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("instructions");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getMetadata()).hasSize(1).containsEntry("messageType", MessageType.SYSTEM);
	}

	@Test
	void whenMessagesWithSystemMessageAndNoSystemText() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		var chatClient = ChatClient.builder(this.chatModel).build();
		List<Message> messages = List.of(new SystemMessage("instructions"), new UserMessage("my question"));
		var content = chatClient.prompt().messages(messages).user("another question").call().content();

		assertThat(content).isEqualTo("response");

		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(3);
		var systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("instructions");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getMetadata()).hasSize(1).containsEntry("messageType", MessageType.SYSTEM);
	}

	@Test
	void whenMessagesWithSystemMessageAndSystemText() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		var chatClient = ChatClient.builder(this.chatModel).build();
		List<Message> messages = List.of(new SystemMessage("instructions"), new UserMessage("my question"));
		var content = chatClient.prompt()
			.messages(messages)
			.system("other instructions")
			.user("another question")
			.call()
			.content();

		assertThat(content).isEqualTo("response");

		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(4);
		var systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("other instructions");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(systemMessage.getMetadata()).hasSize(1).containsEntry("messageType", MessageType.SYSTEM);
	}

}
