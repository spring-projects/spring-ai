package org.springframework.ai.deepseek;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.model.Media;

import java.util.List;
import java.util.Map;

public class PrefixCompletionAssistantMessage extends AssistantMessage {

	public PrefixCompletionAssistantMessage(String content) {
		super(content);
	}

	public PrefixCompletionAssistantMessage(String content, Map<String, Object> properties) {
		super(content, properties);
	}

	public PrefixCompletionAssistantMessage(String content, Map<String, Object> properties, List<ToolCall> toolCalls) {
		super(content, properties, toolCalls);
	}

	public PrefixCompletionAssistantMessage(String content, Map<String, Object> properties, List<ToolCall> toolCalls,
			List<Media> media) {
		super(content, properties, toolCalls, media);
	}

}
