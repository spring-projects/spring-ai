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

package org.springframework.ai.chat.service;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.transformer.ChatServiceContext;
import org.springframework.ai.evaluation.EvaluationRequest;

import java.util.Objects;

/**
 * Encapsulates the response from the ChatService. Contains the most up-to-date
 * ChatServiceContext and the final ChatResponse
 *
 * @author Mark Pollack
 * @since 1.0 M1
 */
public class ChatServiceResponse {

	private final ChatServiceContext chatServiceContext;

	private final ChatResponse chatResponse;

	public ChatServiceResponse(ChatServiceContext chatServiceContext, ChatResponse chatResponse) {
		this.chatServiceContext = chatServiceContext;
		this.chatResponse = chatResponse;
	}

	public ChatServiceContext getPromptContext() {
		return chatServiceContext;
	}

	public ChatResponse getChatResponse() {
		return chatResponse;
	}

	public EvaluationRequest toEvaluationRequest() {
		return new EvaluationRequest(getPromptContext().getPromptChanges().get(0).revised(),
				getPromptContext().getContents(), getChatResponse());
	}

	@Override
	public String toString() {
		return "ChatServiceResponse{" + "chatServiceContext=" + chatServiceContext + ", chatResponse=" + chatResponse
				+ '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ChatServiceResponse that))
			return false;
		return Objects.equals(chatServiceContext, that.chatServiceContext)
				&& Objects.equals(chatResponse, that.chatResponse);
	}

	@Override
	public int hashCode() {
		return Objects.hash(chatServiceContext, chatResponse);
	}

}
