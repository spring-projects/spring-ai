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

package org.springframework.ai.jitllm;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.beehive.jitllm.api.ChatContent;
import org.beehive.jitllm.api.ChatMessage;
import org.beehive.jitllm.api.ChatRole;
import org.beehive.jitllm.api.FinishReason;
import org.beehive.jitllm.api.ToolSpec;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.JsonHelper;
import org.springframework.util.StringUtils;

/**
 * Conversions between Spring AI's message and tool types and jitLLM's public API.
 *
 * @author Michalis Papadimitriou
 * @since 2.1.0
 */
final class JitLlmConversions {

	static final String FINISH_REASON_STOP = "STOP";

	static final String FINISH_REASON_LENGTH = "LENGTH";

	static final String FINISH_REASON_TOOL_CALLS = "TOOL_CALLS";

	private static final String EMPTY_OBJECT_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

	private static final JsonHelper jsonHelper = new JsonHelper();

	private JitLlmConversions() {
	}

	/**
	 * The conversation, in the engine's vocabulary. Every message is sent on every
	 * request: a Spring AI {@code ChatModel} is stateless.
	 */
	static List<ChatMessage> toEngineMessages(List<Message> messages) {
		List<ChatMessage> converted = new ArrayList<>(messages.size());
		for (Message message : messages) {
			switch (message.getMessageType()) {
				case SYSTEM -> converted.add(ChatMessage.of(ChatRole.SYSTEM, textOf(message)));
				case USER -> converted.add(ChatMessage.of(ChatRole.USER, textOf(message)));
				case ASSISTANT -> converted.add(toEngineAssistantMessage((AssistantMessage) message));
				case TOOL -> {
					for (ToolResponseMessage.ToolResponse response : ((ToolResponseMessage) message).getResponses()) {
						converted.add(new ChatMessage(ChatRole.TOOL,
								List.of(new ChatContent.ToolResult(idOrGenerated(response.id()), response.name(),
										unwrapToolResult(response.responseData())))));
					}
				}
			}
		}
		return converted;
	}

	private static ChatMessage toEngineAssistantMessage(AssistantMessage message) {
		List<ChatContent> content = new ArrayList<>();
		String text = message.getText();
		if (StringUtils.hasLength(text)) {
			content.add(new ChatContent.Text(text));
		}
		for (AssistantMessage.ToolCall call : message.getToolCalls()) {
			content.add(new ChatContent.ToolCall(idOrGenerated(call.id()), call.name(),
					StringUtils.hasText(call.arguments()) ? call.arguments() : "{}"));
		}
		if (content.isEmpty()) {
			// The engine rejects an empty turn; an assistant turn with neither text nor
			// calls is still a turn.
			content.add(new ChatContent.Text(""));
		}
		return new ChatMessage(ChatRole.ASSISTANT, content);
	}

	/** The tool definitions, in the engine's vocabulary. */
	static List<ToolSpec> toEngineTools(List<ToolDefinition> tools) {
		List<ToolSpec> specs = new ArrayList<>(tools.size());
		for (ToolDefinition tool : tools) {
			specs.add(new ToolSpec(tool.name(), tool.description() != null ? tool.description() : "",
					StringUtils.hasText(tool.inputSchema()) ? tool.inputSchema() : EMPTY_OBJECT_SCHEMA));
		}
		return specs;
	}

	/** The engine's tool calls, as Spring AI tool calls, in order. */
	static List<AssistantMessage.ToolCall> toSpringToolCalls(List<ChatContent.ToolCall> calls) {
		List<AssistantMessage.ToolCall> converted = new ArrayList<>(calls.size());
		for (ChatContent.ToolCall call : calls) {
			converted.add(new AssistantMessage.ToolCall(call.id(), "function", call.name(), call.argumentsJson()));
		}
		return converted;
	}

	/** The engine's finish reason, in the vocabulary Spring AI's providers use. */
	static String toSpringFinishReason(FinishReason reason) {
		return switch (reason) {
			case TOOL_CALL -> FINISH_REASON_TOOL_CALLS;
			case MAX_TOKENS, CONTEXT_FULL -> FINISH_REASON_LENGTH;
			case STOP_TOKEN, STOP_SEQUENCE -> FINISH_REASON_STOP;
		};
	}

	/**
	 * A tool result as the model should read it. Spring AI serializes a tool's return
	 * value to JSON, so a {@code String} result arrives as a quoted JSON literal; the
	 * model reads the text better than its encoding.
	 */
	static String unwrapToolResult(@Nullable String result) {
		if (result == null) {
			return "";
		}
		if (result.startsWith("\"")) {
			try {
				String unwrapped = jsonHelper.fromJson(result, String.class);
				if (unwrapped != null) {
					return unwrapped;
				}
			}
			catch (RuntimeException ex) {
				// Not a JSON string literal; pass it through unchanged.
			}
		}
		return result;
	}

	private static String textOf(Message message) {
		String text = message.getText();
		return text != null ? text : "";
	}

	/**
	 * The engine requires a non-blank id to match a result to its call; Spring AI allows
	 * a blank one.
	 */
	private static String idOrGenerated(@Nullable String id) {
		return StringUtils.hasText(id) ? id : generateCallId();
	}

	static String generateCallId() {
		return "call_" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36);
	}

}
