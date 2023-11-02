package org.springframework.ai.memory.histories;

import org.springframework.ai.prompt.messages.AssistantMessage;
import org.springframework.ai.prompt.messages.Message;
import org.springframework.ai.prompt.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Component for storing chat message history.
 *
 * @author Craig Walls
 */
public interface ChatMessageHistory {

	/**
	 * @return A list of {@link Message} stored in memory.
	 */
	List<Message> getMessages();

	/**
	 * Add a human message string to the store.
	 * @param message the String contents of the human message.
	 */
	default void addUserMessage(String message) {
		getMessages().add(new UserMessage(message));
	}

	/**
	 * Add an AI message string to the store.
	 * @param message the String contents of the AI message.
	 */
	default void addAiMessage(String message) {
		getMessages().add(new AssistantMessage(message));
	}

	/**
	 * Add a {@link Message} to the store.
	 * @param message the message to add.
	 */
	default void addMessage(Message message) {
		getMessages().add(message);
	}

	/**
	 * Remove all messages from the store.
	 */
	default void clear() {
		getMessages().clear();
	}

}
