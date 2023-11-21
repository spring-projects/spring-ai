package org.springframework.ai.memory;

import org.springframework.ai.prompt.messages.AssistantMessage;
import org.springframework.ai.prompt.messages.Message;
import org.springframework.ai.prompt.messages.MessageType;
import org.springframework.ai.prompt.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConversationBufferMemory implements Memory {

	private String humanPrefix = MessageType.USER.getValue();

	private String aiPrefix = MessageType.ASSISTANT.getValue();

	private List<Message> messages = new ArrayList<>();

	private String memoryKey = "history";

	public void setMemoryKey(String memoryKey) {
		this.memoryKey = memoryKey;
	}

	@Override
	public List<String> getKeys() {
		return List.of(memoryKey);
	}

	public List<Message> getMessages() {
		return messages;
	}

	@Override
	public Map<String, Object> load(Map<String, Object> inputs) {
		return Map.of(memoryKey, getBufferAsString());
	}

	@Override
	public void save(Map<String, Object> inputs, Map<String, Object> outputs) {
		String promptInputKey = inputs.keySet().iterator().next();
		messages.add(new UserMessage(inputs.get(promptInputKey).toString()));

		String promptOutputKey = outputs.keySet().iterator().next();
		messages.add(new AssistantMessage(outputs.get(promptOutputKey).toString()));
	}

	private String getBufferAsString() {
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

}
