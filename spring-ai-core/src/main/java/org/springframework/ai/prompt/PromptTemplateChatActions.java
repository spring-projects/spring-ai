package org.springframework.ai.prompt;

import org.springframework.ai.prompt.messages.Message;

import java.util.List;
import java.util.Map;

public interface PromptTemplateChatActions {

	List<Message> createMessages();

	List<Message> createMessages(Map<String, Object> model);

}
