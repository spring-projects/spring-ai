package org.springframework.ai.azure.openai.dto;

import com.azure.ai.openai.models.ChatCompletionsFunctionToolCall;
import com.azure.ai.openai.models.ChatCompletionsToolCall;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.util.Assert;

import java.util.Objects;

public class AccessibleChatCompletionsToolCall {

	@JsonProperty(value = "id")
	public String id;

	@JsonProperty("type")
	public String type;

	public static AccessibleChatCompletionsToolCall from(ChatCompletionsToolCall toolCall) {
		if (toolCall == null) {
			return null;
		}
		if ("function".equals(toolCall.getType())) {
			return AccessibleChatCompletionsFunctionToolCall.from(((ChatCompletionsFunctionToolCall) toolCall));
		}
		throw new UnsupportedOperationException("Only function chat completion tool is supported");
	}

	public static AccessibleChatCompletionsToolCall merge(AccessibleChatCompletionsToolCall left, AccessibleChatCompletionsToolCall right) {
		Assert.isTrue(Objects.equals(left.type, right.type), "Cannot merge different type of AccessibleChatCompletionsToolCall");
		if (!"function".equals(left.type)) {
			throw new UnsupportedOperationException("Only function chat completion tool is supported");
		}
		return AccessibleChatCompletionsFunctionToolCall.merge((AccessibleChatCompletionsFunctionToolCall) left, (AccessibleChatCompletionsFunctionToolCall) right);
	}
}
