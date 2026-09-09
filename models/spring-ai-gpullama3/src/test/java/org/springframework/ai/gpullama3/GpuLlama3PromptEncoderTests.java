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

package org.springframework.ai.gpullama3;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.List;
import java.util.Set;

import org.beehive.gpullama3.model.Model;
import org.beehive.gpullama3.model.format.ChatFormat;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GpuLlama3PromptEncoderTests {

	private final GpuLlama3PromptEncoder encoder = new GpuLlama3PromptEncoder();

	@Test
	void encodesMessagesInPromptOrderAndAppendsAssistantHeader() {
		Prompt prompt = new Prompt(List.of(new SystemMessage("sys"), new UserMessage("hello"),
				new AssistantMessage("answer"), new UserMessage("again")));

		List<Integer> tokens = this.encoder.encode(prompt, model(true, true));

		assertThat(tokens).containsExactly(1, 10, 3, 20, 5, 30, 6, 20, 5, 30);
	}

	@Test
	void skipsBeginOfTextWhenModelDoesNotRequireIt() {
		Prompt prompt = new Prompt(new UserMessage("hello"));

		List<Integer> tokens = this.encoder.encode(prompt, model(false, true));

		assertThat(tokens).containsExactly(20, 5, 30);
	}

	@Test
	void skipsSystemMessageWhenModelDoesNotSupportSystemPrompt() {
		Prompt prompt = new Prompt(List.of(new SystemMessage("sys"), new UserMessage("hello")));

		List<Integer> tokens = this.encoder.encode(prompt, model(true, false));

		assertThat(tokens).containsExactly(1, 20, 5, 30);
	}

	@Test
	void returnsImmutableTokenList() {
		Prompt prompt = new Prompt(new UserMessage("hello"));

		List<Integer> tokens = this.encoder.encode(prompt, model(true, true));

		assertThatThrownBy(() -> tokens.add(99)).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void rejectsToolResponseMessages() {
		ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "tool", "{}")))
			.build();

		assertThatThrownBy(() -> this.encoder.encode(new Prompt(toolResponseMessage), model(true, true)))
			.isInstanceOf(UnsupportedOperationException.class)
			.hasMessageContaining("tool response");
	}

	@Test
	void rejectsAssistantToolCalls() {
		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("calling tool")
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "tool", "{}")))
			.build();

		assertThatThrownBy(() -> this.encoder.encode(new Prompt(assistantMessage), model(true, true)))
			.isInstanceOf(UnsupportedOperationException.class)
			.hasMessageContaining("assistant tool calls");
	}

	@Test
	void rejectsMediaMessages() {
		Media media = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(URI.create("file:/tmp/image.png")).build();
		UserMessage userMessage = UserMessage.builder().text("describe").media(media).build();

		assertThatThrownBy(() -> this.encoder.encode(new Prompt(userMessage), model(true, true)))
			.isInstanceOf(UnsupportedOperationException.class)
			.hasMessageContaining("media content");
	}

	@Test
	void rejectsUnknownMessageTypes() {
		Message message = new Message() {
			@Override
			public String getText() {
				return "unknown";
			}

			@Override
			public java.util.Map<String, Object> getMetadata() {
				return java.util.Map.of();
			}

			@Override
			public org.springframework.ai.chat.messages.MessageType getMessageType() {
				return org.springframework.ai.chat.messages.MessageType.USER;
			}
		};

		assertThatThrownBy(() -> this.encoder.encode(new Prompt(message), model(true, true)))
			.isInstanceOf(UnsupportedOperationException.class)
			.hasMessageContaining("Unsupported Spring AI message type");
	}

	private static Model model(boolean addBeginOfText, boolean addSystemPrompt) {
		FakeChatFormat chatFormat = new FakeChatFormat();
		return (Model) Proxy.newProxyInstance(Model.class.getClassLoader(), new Class<?>[] { Model.class },
				(proxy, method, args) -> switch (method.getName()) {
					case "chatFormat" -> chatFormat;
					case "shouldAddBeginOfText" -> addBeginOfText;
					case "shouldAddSystemPrompt" -> addSystemPrompt;
					case "shouldIncludeReasoning" -> false;
					case "toString" -> "FakeModel";
					default -> throw new UnsupportedOperationException(method.getName());
				});
	}

	private static final class FakeChatFormat implements ChatFormat {

		@Override
		public List<Integer> encodeHeader(ChatFormat.Message message) {
			return List.of(roleToken(message.role()));
		}

		@Override
		public List<Integer> encodeMessage(ChatFormat.Message message) {
			return List.of(roleToken(message.role()), message.content().length());
		}

		@Override
		public int getBeginOfText() {
			return 1;
		}

		@Override
		public Set<Integer> getStopTokens() {
			return Set.of();
		}

		private static int roleToken(ChatFormat.Role role) {
			if (ChatFormat.Role.SYSTEM.equals(role)) {
				return 10;
			}
			if (ChatFormat.Role.USER.equals(role)) {
				return 20;
			}
			if (ChatFormat.Role.ASSISTANT.equals(role)) {
				return 30;
			}
			throw new IllegalArgumentException("Unsupported role: " + role);
		}

	}

}
