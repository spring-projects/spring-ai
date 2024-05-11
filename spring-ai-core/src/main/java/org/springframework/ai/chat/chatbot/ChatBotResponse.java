/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.chat.chatbot;

import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.transformer.PromptContext;

import java.util.Objects;

/**
 * Encapsulates the response from the ChatBot. Contains the most up-to-date PromptContext
 * and the final ChatResponse
 *
 * @author Mark Pollack
 * @since 1.0 M1
 */
public class ChatBotResponse {

	private final PromptContext promptContext;

	private final ChatResponse chatResponse;

	public ChatBotResponse(PromptContext promptContext, ChatResponse chatResponse) {
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
		return "ChatBotResponse{" + "promptContext=" + promptContext + ", chatResponse=" + chatResponse + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ChatBotResponse that))
			return false;
		return Objects.equals(promptContext, that.promptContext) && Objects.equals(chatResponse, that.chatResponse);
	}

	@Override
	public int hashCode() {
		return Objects.hash(promptContext, chatResponse);
	}

}
