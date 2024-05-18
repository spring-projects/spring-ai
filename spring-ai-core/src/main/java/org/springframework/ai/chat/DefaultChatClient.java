package org.springframework.ai.chat;

import org.springframework.ai.chat.prompt.Prompt;

/**
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Josh Long
 * @author Arjen Poutsma
 */
class DefaultChatClient implements ChatClient {

	private final ChatModel chatModel;

	private final ChatClientRequest defaultChatClientRequest;

	public DefaultChatClient(ChatModel chatModel, ChatClientRequest defaultChatClientRequest) {
		this.chatModel = chatModel;
		this.defaultChatClientRequest = defaultChatClientRequest;
	}

	@Override
	public ChatClientRequest prompt() {
		return new ChatClientRequest(this.defaultChatClientRequest);
	}

	@Override
	public ChatClientPromptRequest prompt(Prompt prompt) {
		return new ChatClientPromptRequest(this.chatModel, prompt);
	}

	/**
	 * use the new fluid DSL starting in {@link #prompt()}
	 * @param prompt the {@link Prompt prompt} object
	 * @return a {@link ChatResponse chat response}
	 */
	@Deprecated(forRemoval = true, since = "1.0.0 M1")
	@Override
	public ChatResponse call(Prompt prompt) {
		return this.chatModel.call(prompt);
	}

}