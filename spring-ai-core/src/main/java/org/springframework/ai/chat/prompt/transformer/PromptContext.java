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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Content;

/**
 * The shared, at the moment, mutable, data structure that can be used to implement
 * ChatBot functionality.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class PromptContext {

	private Prompt prompt; // The most up-to-date prompt to use

	private List<Content> contents; // The most up-to-date data to use

	private List<Prompt> promptHistory;

	private String conversationId = "default";

	private Map<String, Object> metadata = new HashMap<>();

	public PromptContext(Prompt prompt) {
		this(prompt, new ArrayList<>());
	}

	public PromptContext(Prompt prompt, String conversationId) {
		this(prompt, new ArrayList<>());
		this.conversationId = conversationId;
	}

	public PromptContext(Prompt prompt, List<Content> contents) {
		this.prompt = prompt;
		this.promptHistory = new ArrayList<>();
		this.promptHistory.add(prompt);
		this.contents = contents;
	}

	public Prompt getPrompt() {
		return prompt;
	}

	public void setPrompt(Prompt prompt) {
		this.prompt = prompt;
	}

	public void addData(Content datum) {
		this.contents.add(datum);
	}

	public List<Content> getContents() {
		return contents;
	}

	public void setContents(List<Content> contents) {
		this.contents = contents;
	}

	public void addPromptHistory(Prompt prompt) {
		this.promptHistory.add(prompt);
	}

	public List<Prompt> getPromptHistory() {
		return promptHistory;
	}

	public String getConversationId() {
		return conversationId;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public static Builder from(PromptContext promptContext) {
		return PromptContext.builder()
			.withContents(
					new ArrayList<>(promptContext.getContents() != null ? promptContext.getContents() : List.of()))
			.withPrompt(promptContext.getPrompt().copy()) // deep copy
			.withMetadata(new HashMap<>(promptContext.getMetadata() != null ? promptContext.getMetadata() : Map.of()))
			.withPromptHistory(new ArrayList<>(
					promptContext.getPromptHistory() != null ? promptContext.getPromptHistory() : List.of()))
			.withConversationId(promptContext.getConversationId());
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private Prompt prompt;

		private List<Content> contents;

		private List<Prompt> promptHistory;

		private String conversationId;

		private Map<String, Object> metadata = new HashMap<>();

		public Builder withPrompt(Prompt prompt) {
			this.prompt = prompt;
			return this;
		}

		public Builder withContents(List<Content> contents) {
			this.contents = new ArrayList<>(contents);
			return this;
		}

		public Builder withPromptHistory(List<Prompt> promptHistory) {
			this.promptHistory = new ArrayList<>(promptHistory);
			return this;
		}

		public Builder addPromptHistory(Prompt prompt) {
			this.promptHistory.add(prompt);
			return this;
		}

		public Builder withConversationId(String conversationId) {
			this.conversationId = conversationId;
			return this;
		}

		public Builder withMetadata(Map<String, Object> metadata) {
			this.metadata = new HashMap<>(metadata);
			return this;
		}

		public PromptContext build() {
			PromptContext promptContext = new PromptContext(prompt, contents);
			promptContext.promptHistory = promptHistory;
			promptContext.conversationId = conversationId;
			promptContext.metadata = metadata;
			return promptContext;
		}

	}

	@Override
	public String toString() {
		return "PromptContext{" + "prompt=" + prompt + ", contents=" + contents + ", promptHistory=" + promptHistory
				+ ", conversationId='" + conversationId + '\'' + ", metadata=" + metadata + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof PromptContext that))
			return false;
		return Objects.equals(prompt, that.prompt) && Objects.equals(contents, that.contents)
				&& Objects.equals(promptHistory, that.promptHistory)
				&& Objects.equals(conversationId, that.conversationId) && Objects.equals(metadata, that.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(prompt, contents, promptHistory, conversationId, metadata);
	}

}
