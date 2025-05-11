package org.springframework.ai.chat.client.advisor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

/**
 * Advisor for standard ChatMemory implementations
 * @author Mark Pollack
 * @since 1.0.0
 */
public abstract class AbstractConversationHistoryAdvisor extends AbstractChatMemoryAdvisor<ChatMemory> {

	public AbstractConversationHistoryAdvisor(ChatMemory chatMemory) {
		this(chatMemory, ChatMemory.DEFAULT_CONVERSATION_ID, true, DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER);
	}

	public AbstractConversationHistoryAdvisor(ChatMemory chatMemory, String defaultConversationId, boolean protectFromBlocking,
											  int order) {
		super(chatMemory, defaultConversationId, protectFromBlocking, order);
	}

	protected List<Message> retrieveMessages(String conversationId, Map<String, Object> options) {
		return chatMemoryStore.get(conversationId);
	}

	@Override
	protected ChatClientRequest before(ChatClientRequest request, String conversationId) {
		Map<String, Object> contextMap = buildContextMap(request);
		List<Message> memoryMessages = retrieveMessages(conversationId, contextMap);
		return applyMessagesToRequest(request, memoryMessages);
	}

	protected ChatClientRequest applyMessagesToRequest(ChatClientRequest request, List<Message> memoryMessages) {
		if (memoryMessages == null || memoryMessages.isEmpty()) {
			return request;
		}
		// Combine memory messages with the instructions from the current prompt
		List<Message> combinedMessages = new ArrayList<>(memoryMessages);
		combinedMessages.addAll(request.prompt().getInstructions());

		// Mutate the prompt to use the combined messages
		var promptBuilder = request.prompt().mutate().messages(combinedMessages);

		// Return a new ChatClientRequest with the updated prompt
		return request.mutate().prompt(promptBuilder.build()).build();
	}

}
