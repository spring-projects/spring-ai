package org.springframework.ai.memory;

import org.springframework.ai.prompt.messages.Message;
import org.springframework.ai.prompt.messages.MessageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Buffered memory for storing conversation history.
 *
 * @author Craig Walls
 */
public class ConversationBufferMemory extends BaseChatMemory {

	private String memoryKey = "history";

	private String humanPrefix = MessageType.USER.getValue();

	private String aiPrefix = MessageType.ASSISTANT.getValue();

	public void setMemoryKey(String memoryKey) {
		this.memoryKey = memoryKey;
	}

	public void setHumanPrefix(String humanPrefix) {
		this.humanPrefix = humanPrefix;
	}

	public void setAiPrefix(String aiPrefix) {
		this.aiPrefix = aiPrefix;
	}

	@Override
	public List<String> getKeys() {
		return List.of(memoryKey);
	}

	@Override
	public Map<String, Object> load(Map<String, Object> inputs) {
		if (returnMessages) {
			return Map.of(memoryKey, bufferAsMessages());
		}
		return Map.of(memoryKey, getBufferAsString());
	}

	public String getBufferAsString() {
		List<String> stringMessages = new ArrayList<>();
		getMessages().forEach(message -> {
			String role = message.getMessageType().getValue();
			if (role.equals(MessageType.USER.getValue())) {
				role = humanPrefix;
			}
			else if (role.equals(MessageType.ASSISTANT.getValue())) {
				role = aiPrefix;
			}
			stringMessages.add(String.format("%s: %s", role, message.getContent()));
		});
		return String.join("\n", stringMessages);
	}

	private List<Message> bufferAsMessages() {
		return getMessages();
	}

}
