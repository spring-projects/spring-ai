package org.springframework.ai.watsonx;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public class WatsonxChatClient implements ChatClient, StreamingChatClient {

	@Override
	public ChatResponse call(Prompt prompt) {
		return null;
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return null;
	}

}
