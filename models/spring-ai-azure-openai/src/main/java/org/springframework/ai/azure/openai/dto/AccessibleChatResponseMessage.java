package org.springframework.ai.azure.openai.dto;

import com.azure.ai.openai.models.AzureChatExtensionsMessageContext;
import com.azure.ai.openai.models.ChatResponseMessage;
import com.azure.ai.openai.models.ChatRole;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AccessibleChatResponseMessage {

	@JsonProperty(value = "role")
	public ChatRole role;

	@JsonProperty(value = "content")
	public String content;

	@JsonProperty(value = "tool_calls")
	public List<AccessibleChatCompletionsToolCall> toolCalls;

	@JsonProperty(value = "function_call")
	public AccessibleFunctionCall functionCall;

	@JsonProperty(value = "context")
	public AzureChatExtensionsMessageContext context;

	public static AccessibleChatResponseMessage from(ChatResponseMessage message) {
		if (message == null) {
			return null;
		}
		final var mapped = new AccessibleChatResponseMessage();
		mapped.role = message.getRole();
		mapped.content = message.getContent();
		mapped.toolCalls = Optional.ofNullable(message.getToolCalls())
			.map(tool -> tool.stream().map(AccessibleChatCompletionsToolCall::from).toList())
			.orElse(null);
		mapped.functionCall = AccessibleFunctionCall.from(message.getFunctionCall());
		mapped.context = message.getContext();
		return mapped;
	}

	public static AccessibleChatResponseMessage merge(AccessibleChatResponseMessage left, AccessibleChatResponseMessage right) {
		final var instance = new AccessibleChatResponseMessage();
		instance.role = left.role != null ? left.role : right.role;
		if (left.content != null && right.content != null) {
			instance.content = left.content.concat(right.content);
		} else if (left.content == null) {
			instance.content = right.content;
		} else {
			instance.content = left.content;
		}


		instance.toolCalls = new ArrayList<>();
		if (left.toolCalls == null) {
			if (right.toolCalls != null) {
				instance.toolCalls.addAll(right.toolCalls);
			}
		} else if (right.toolCalls == null) {
			instance.toolCalls.addAll(left.toolCalls);
		} else {
			instance.toolCalls.addAll(left.toolCalls);
			final var lastToolIndex = instance.toolCalls.size() - 1;
			var lastTool = instance.toolCalls.get(lastToolIndex);
			if (right.toolCalls.get(0).id == null) {
				lastTool = AccessibleChatCompletionsToolCall.merge(lastTool, right.toolCalls.get(0));
				instance.toolCalls.remove(lastToolIndex);
				instance.toolCalls.add(lastTool);
			} else {
				instance.toolCalls.add(right.toolCalls.get(0));
			}
		}

		if (left.functionCall == null) {
			instance.functionCall = right.functionCall;
		} else {
			instance.functionCall = AccessibleFunctionCall.merge(left.functionCall, right.functionCall);
		}
		instance.context = left.context != null ? left.context : right.context;
		return instance;
	}

	public ChatRole getRole() {
		return role;
	}

	public String getContent() {
		return content;
	}

	public List<AccessibleChatCompletionsToolCall> getToolCalls() {
		return toolCalls;
	}

	public AccessibleFunctionCall getFunctionCall() {
		return functionCall;
	}

	public AzureChatExtensionsMessageContext getContext() {
		return context;
	}

}
