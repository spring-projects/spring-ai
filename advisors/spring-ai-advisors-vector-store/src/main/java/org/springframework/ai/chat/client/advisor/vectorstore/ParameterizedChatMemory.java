package org.springframework.ai.chat.client.advisor.vectorstore;

import java.util.List;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

/**
 * Interface for chat memories that support parameterized retrieval. Implementations can
 * define their own parameter types.
 *
 * @param <P> The type of parameters used for retrieval
 */
public interface ParameterizedChatMemory<P> extends ChatMemory {

	/**
	 * Retrieve messages based on the provided parameters.
	 * @param conversationId The conversation identifier
	 * @param parameters The retrieval parameters
	 * @return List of retrieved messages
	 */
	List<Message> retrieve(String conversationId, P parameters);

}
