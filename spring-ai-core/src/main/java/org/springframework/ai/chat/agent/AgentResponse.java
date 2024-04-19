package org.springframework.ai.chat.agent;

import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.transformer.PromptContext;

import java.util.Objects;

/**
 * Encapsulates the response from the ChatAgent. Contains the most up-to-date
 * PromptContext and the final ChatResponse
 *
 * @author Mark Pollack
 * @since 1.0 M1
 */
public class AgentResponse {

	private final PromptContext promptContext;

	private final ChatResponse chatResponse;

	public AgentResponse(PromptContext promptContext, ChatResponse chatResponse) {
		this.promptContext = promptContext;
		this.chatResponse = chatResponse;
	}

	public PromptContext getPromptContext() {
		return promptContext;
	}

	public ChatResponse getChatResponse() {
		return chatResponse;
	}

	@Override
	public String toString() {
		return "AgentResponse{" + "promptContext=" + promptContext + ", chatResponse=" + chatResponse + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof AgentResponse that))
			return false;
		return Objects.equals(promptContext, that.promptContext) && Objects.equals(chatResponse, that.chatResponse);
	}

	@Override
	public int hashCode() {
		return Objects.hash(promptContext, chatResponse);
	}

}
