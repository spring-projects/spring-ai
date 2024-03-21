package org.springframework.ai.azure.openai.dto;

import com.azure.ai.openai.models.AzureChatExtensionsMessageContext;
import com.azure.ai.openai.models.ChatResponseMessage;
import com.azure.ai.openai.models.ChatRole;
import com.fasterxml.jackson.annotation.JsonProperty;

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
