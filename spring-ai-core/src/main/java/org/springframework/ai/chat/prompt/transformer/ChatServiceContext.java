/*
 * Copyright 2024-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.chat.prompt.transformer;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.service.ChatService;
import org.springframework.ai.model.Content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the execution context for the {@link ChatService}. This context is used to
 * pass initial parameters to the service and facilitate data sharing between different
 * components within the service.
 *
 * <p>
 * The {@code ChatServiceContext} includes essential information such as the initial
 * prompt and a conversation ID, which are crucial for the correct operation of the chat
 * service.
 * </p>
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @since 1.0.0 M1
 */
public class ChatServiceContext {

	private Prompt prompt; // The most up-to-date prompt to use

	private List<Content> contents; // The most up-to-date data to use

	private List<PromptChange> promptChanges; // The changes make due to transformations

	private String conversationId;

	/**
	 * Contextual data that can be shared between processing steps in a ChatService
	 * implementation.
	 */
	private Map<String, Object> context = new ConcurrentHashMap<>();

	public ChatServiceContext(Prompt prompt) {
		this(prompt, "default", new ArrayList<>());
	}

	public ChatServiceContext(Prompt prompt, String conversationId) {
		this(prompt, conversationId, new ArrayList<>());
	}

	public ChatServiceContext(Prompt prompt, String conversationId, List<Content> contents) {
		this.prompt = prompt;
		this.conversationId = conversationId;
		this.promptChanges = new ArrayList<>();
		this.promptChanges.add(new PromptChange(null, prompt, "none", "initial prompt"));
		this.contents = contents;
	}

	public Prompt getPrompt() {
		return this.prompt;
	}

	public void updatePrompt(Prompt prompt, String transformerName, String description) {
		this.promptChanges.add(new PromptChange(this.prompt, prompt, transformerName, description));
		this.prompt = prompt; // set the new prompt as current
	}

	public void addData(Content datum) {
		this.contents.add(datum);
	}

	public List<Content> getContents() {
		return this.contents;
	}

	public void setContents(List<Content> contents) {
		this.contents = contents;
	}

	public List<PromptChange> getPromptChanges() {
		return this.promptChanges;
	}

	public String getConversationId() {
		return this.conversationId;
	}

	public Map<String, Object> getContext() {
		return this.context;
	}

	public static Builder from(ChatServiceContext chatServiceContext) {
		return ChatServiceContext.builder()
			.withContents(new ArrayList<>(
					chatServiceContext.getContents() != null ? chatServiceContext.getContents() : List.of()))
			.withPrompt(chatServiceContext.getPrompt().copy()) // deep copy
			.withMetadata(
					new HashMap<>(chatServiceContext.getContext() != null ? chatServiceContext.getContext() : Map.of()))
			.withPromptChanges(new ArrayList<>(
					chatServiceContext.getPromptChanges() != null ? chatServiceContext.getPromptChanges() : List.of()))
			.withConversationId(chatServiceContext.getConversationId());
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private Prompt prompt;

		private List<Content> contents;

		private List<PromptChange> promptChanges;

		private String conversationId;

		private Map<String, Object> context = new HashMap<>();

		public Builder withPrompt(Prompt prompt) {
			this.prompt = prompt;
			return this;
		}

		public Builder withContents(List<Content> contents) {
			this.contents = new ArrayList<>(contents);
			return this;
		}

		public Builder withPromptChanges(List<PromptChange> promptChanges) {
			this.promptChanges = new ArrayList<>(promptChanges);
			return this;
		}

		public Builder withPromptChange(PromptChange promptChange) {
			this.promptChanges.add(promptChange);
			return this;
		}

		public Builder withConversationId(String conversationId) {
			this.conversationId = conversationId;
			return this;
		}

		public Builder withMetadata(Map<String, Object> context) {
			this.context = new HashMap<>(context);
			return this;
		}

		public ChatServiceContext build() {
			ChatServiceContext chatServiceContext = new ChatServiceContext(this.prompt, this.conversationId,
					this.contents);
			chatServiceContext.promptChanges = promptChanges;
			chatServiceContext.context = context;
			return chatServiceContext;
		}

	}

	@Override
	public String toString() {
		return "ChatServiceContext{" + "prompt=" + prompt + ", contents=" + contents + ", promptHistory="
				+ promptChanges + ", conversationId='" + conversationId + '\'' + ", metadata=" + context + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ChatServiceContext that))
			return false;
		return Objects.equals(prompt, that.prompt) && Objects.equals(contents, that.contents)
				&& Objects.equals(promptChanges, that.promptChanges)
				&& Objects.equals(conversationId, that.conversationId) && Objects.equals(context, that.context);
	}

	@Override
	public int hashCode() {
		return Objects.hash(prompt, contents, promptChanges, conversationId, context);
	}

}
