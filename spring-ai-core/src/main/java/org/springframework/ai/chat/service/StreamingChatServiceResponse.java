package org.springframework.ai.chat.service;

import org.springframework.ai.chat.prompt.transformer.ChatServiceContext;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.model.ChatResponse;

/**
 * Encapsulates the response from the ChatService. Contains the most up-to-date
 * ChatServiceContext and the final ChatResponse
 *
 * @author Mark Pollack
 * @since 1.0 M1
 */
public class StreamingChatServiceResponse {

	private final ChatServiceContext chatServiceContext;

	private final Flux<ChatResponse> chatResponse;

	public StreamingChatServiceResponse(ChatServiceContext chatServiceContext, Flux<ChatResponse> chatResponse) {
		this.chatServiceContext = chatServiceContext;
		this.chatResponse = chatResponse;
	}

	public ChatServiceContext getPromptContext() {
		return chatServiceContext;
	}

	public Flux<ChatResponse> getChatResponse() {
		return chatResponse;
	}

	@Override
	public String toString() {
		return "ChatServiceResponse{" + "chatServiceContext=" + chatServiceContext + ", chatResponse=" + chatResponse
				+ '}';
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((chatServiceContext == null) ? 0 : chatServiceContext.hashCode());
		result = prime * result + ((chatResponse == null) ? 0 : chatResponse.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		StreamingChatServiceResponse other = (StreamingChatServiceResponse) obj;
		if (chatServiceContext == null) {
			if (other.chatServiceContext != null)
				return false;
		}
		else if (!chatServiceContext.equals(other.chatServiceContext))
			return false;
		if (chatResponse == null) {
			if (other.chatResponse != null)
				return false;
		}
		else if (!chatResponse.equals(other.chatResponse))
			return false;
		return true;
	}

}
