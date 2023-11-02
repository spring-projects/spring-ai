package org.springframework.ai.memory.histories;

import org.springframework.ai.prompt.messages.AssistantMessage;
import org.springframework.ai.prompt.messages.Message;
import org.springframework.ai.prompt.messages.MessageType;
import org.springframework.ai.prompt.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for storing chat message history.
 *
 * @author Craig Walls
 */
public interface BaseChatMessageHistory {

    /**
     * A list of {@link Message} stored in memory.
     */
    List<Message> messages = new ArrayList<>();

    /**
     * Add a human message string to the store.
     * @param message the String contents of the human message.
     */
    default void addUserMessage(String message) {
        messages.add(new UserMessage(message));
    }

    /**
     * Add an AI message string to the store.
     * @param message the String contents of the AI message.
     */
    default void addAiMessage(String message) {
        messages.add(new AssistantMessage(message));
    }

    /**
     * Add a {@link Message} to the store.
     * @param message the message to add.
     */
    default void addMessage(Message message) {
        messages.add(message);
    }

    /**
     * Remove all messages from the store.
     */
    default void clear() {
        messages.clear();
    }


}
