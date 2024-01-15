package org.springframework.ai.chat.prompt;

import org.springframework.ai.chat.messages.Message;

import java.util.Map;

public interface PromptTemplateMessageActions {

	Message createMessage();

	Message createMessage(Map<String, Object> model);

}
