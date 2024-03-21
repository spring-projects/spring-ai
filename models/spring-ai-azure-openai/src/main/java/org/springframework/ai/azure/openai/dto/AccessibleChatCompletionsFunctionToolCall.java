package org.springframework.ai.azure.openai.dto;

import com.azure.ai.openai.models.ChatCompletionsFunctionToolCall;

public class AccessibleChatCompletionsFunctionToolCall extends AccessibleChatCompletionsToolCall {

	public AccessibleFunctionCall function;

	public static AccessibleChatCompletionsFunctionToolCall from(ChatCompletionsFunctionToolCall toolCall) {
		final var functionToolCall = new AccessibleChatCompletionsFunctionToolCall();
		functionToolCall.id = toolCall.getId();
		functionToolCall.type = toolCall.getType();
		functionToolCall.function = AccessibleFunctionCall.from(toolCall.getFunction());
		return functionToolCall;
	}

}
