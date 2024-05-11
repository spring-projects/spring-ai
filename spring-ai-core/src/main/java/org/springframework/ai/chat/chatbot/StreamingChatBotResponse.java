package org.springframework.ai.chat.chatbot;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.transformer.PromptContext;

/**
 * Encapsulates the response from the ChatBot. Contains the most up-to-date PromptContext
 * and the final ChatResponse
 *
 * @author Mark Pollack
 * @since 1.0 M1
 */
public class StreamingChatBotResponse {

	private final PromptContext promptContext;

	private final Flux<ChatResponse> chatResponse;

	public StreamingChatBotResponse(PromptContext promptContext, Flux<ChatResponse> chatResponse) {
		this.promptContext = promptContext;
		this.chatResponse = chatResponse;
	}

	public PromptContext getPromptContext() {
		return promptContext;
	}

	public Flux<ChatResponse> getChatResponse() {
		return chatResponse;
	}

	@Override
	public String toString() {
		return "ChatBotResponse{" + "promptContext=" + promptContext + ", chatResponse=" + chatResponse + '}';
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((promptContext == null) ? 0 : promptContext.hashCode());
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
		StreamingChatBotResponse other = (StreamingChatBotResponse) obj;
		if (promptContext == null) {
			if (other.promptContext != null)
				return false;
		}
		else if (!promptContext.equals(other.promptContext))
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
