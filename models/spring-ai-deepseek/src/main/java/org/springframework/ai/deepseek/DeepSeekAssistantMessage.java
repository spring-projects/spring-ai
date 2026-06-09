/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.deepseek;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.content.Media;

/**
 * @author Mark Pollack
 * @author Soby Chacko
 * @author Sun Yuhan
 */
public class DeepSeekAssistantMessage extends AssistantMessage {

	private @Nullable Boolean prefix;

	private @Nullable String reasoningContent;

	protected DeepSeekAssistantMessage(@Nullable String content, @Nullable String reasoningContent,
			@Nullable Boolean prefix, Map<String, Object> properties, List<ToolCall> toolCalls, List<Media> media) {
		super(content, properties, toolCalls, media);
		this.reasoningContent = reasoningContent;
		this.prefix = prefix;
	}

	@Deprecated(forRemoval = true, since = "2.0.0")
	public static DeepSeekAssistantMessage prefixAssistantMessage(@Nullable String content) {
		return prefixAssistantMessage(content, null);
	}

	@Deprecated(forRemoval = true, since = "2.0.0")
	public static DeepSeekAssistantMessage prefixAssistantMessage(@Nullable String content,
			@Nullable String reasoningContent) {
		return new Builder().content(content).prefix(true).reasoningContent(reasoningContent).build();
	}

	public @Nullable Boolean getPrefix() {
		return this.prefix;
	}

	public void setPrefix(Boolean prefix) {
		this.prefix = prefix;
	}

	public @Nullable String getReasoningContent() {
		return this.reasoningContent;
	}

	public void setReasoningContent(@Nullable String reasoningContent) {
		this.reasoningContent = reasoningContent;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof DeepSeekAssistantMessage that)) {
			return false;
		}
		if (!super.equals(o)) {
			return false;
		}
		return Objects.equals(this.reasoningContent, that.reasoningContent) && Objects.equals(this.prefix, that.prefix);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), this.prefix, this.reasoningContent);
	}

	@Override
	public String toString() {
		return "DeepSeekAssistantMessage [messageType=" + this.messageType + ", toolCalls=" + super.getToolCalls()
				+ ", textContent=" + this.textContent + ", reasoningContent=" + this.reasoningContent + ", prefix="
				+ this.prefix + ", metadata=" + this.metadata + "]";
	}

	public static Builder builder() {
		return new Builder();
	}

	// public Builder class exposed to users. Avoids having to deal with noisy generic
	// parameters.
	public static class Builder extends AbstractBuilder<Builder> {

	}

	public static class AbstractBuilder<B extends AbstractBuilder<B>> extends AssistantMessage.Builder<B> {

		protected @Nullable Boolean prefix;

		protected @Nullable String reasoningContent;

		public B prefix(@Nullable Boolean prefix) {
			this.prefix = prefix;
			return self();
		}

		public B reasoningContent(@Nullable String reasoningContent) {
			this.reasoningContent = reasoningContent;
			return self();
		}

		@Override
		public DeepSeekAssistantMessage build() {
			return new DeepSeekAssistantMessage(this.content, this.reasoningContent, this.prefix, this.properties,
					this.toolCalls, this.media);
		}

	}

}
