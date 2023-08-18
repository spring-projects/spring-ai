package org.springframework.ai.prompt;

import org.springframework.ai.prompt.messages.Message;

import java.util.Map;

public interface PromptTemplateMessageActions {

	Message createMessage();

	Message createMessage(Map<String, Object> model);

}
