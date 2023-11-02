package org.springframework.ai.memory.histories;

import org.springframework.ai.prompt.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory implementation of {@link ChatMessageHistory}.
 *
 * @author Craig Walls
 */
public class InMemoryChatMessageHistory implements ChatMessageHistory {

	private List<Message> messages = new ArrayList<>();

	@Override
	public List<Message> getMessages() {
		return messages;
	}

}
