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

package org.springframework.ai.bedrock.converse;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.CollectionUtils;

/**
 * Internal Bedrock-specific {@link AssistantMessage} that carries the signed
 * {@code reasoningContent} blocks returned alongside an assistant turn so the
 * tool-calling loop can replay them, unmodified, on the next Bedrock request.
 * <p>
 * Amazon Bedrock requires that the assistant turn containing a {@code toolUse} block also
 * replays the preceding signed {@code reasoningContent} block when the matching
 * {@code toolResult} is sent. Keeping the reasoning state on the same assistant message
 * as the tool calls makes that replay invariant explicit: the reasoning is associated
 * with the same turn that is selected and re-emitted during tool calling.
 *
 * @author Jewoo Shin
 * @author Soby Chacko
 * @see BedrockReasoningContent
 */
final class BedrockAssistantMessage extends AssistantMessage {

	private final List<BedrockReasoningContent> reasoningContents;

	private BedrockAssistantMessage(@Nullable String content, List<BedrockReasoningContent> reasoningContents,
			Map<String, Object> properties, List<ToolCall> toolCalls, List<Media> media) {
		super(content, properties, toolCalls, media);
		this.reasoningContents = reasoningContents;
	}

	List<BedrockReasoningContent> getReasoningContents() {
		return this.reasoningContents;
	}

	boolean hasReasoningContents() {
		return !CollectionUtils.isEmpty(this.reasoningContents);
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof BedrockAssistantMessage that)) {
			return false;
		}
		if (!super.equals(o)) {
			return false;
		}
		return Objects.equals(this.reasoningContents, that.reasoningContents);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), this.reasoningContents);
	}

	@Override
	public String toString() {
		return "BedrockAssistantMessage [messageType=" + this.messageType + ", toolCalls=" + super.getToolCalls()
				+ ", textContent=" + this.textContent + ", reasoningContents=" + this.reasoningContents + ", metadata="
				+ this.metadata + "]";
	}

	public static Builder builder() {
		return new Builder();
	}

	static final class Builder extends AssistantMessage.Builder<Builder> {

		private List<BedrockReasoningContent> reasoningContents = List.of();

		private Builder() {
		}

		Builder reasoningContents(List<BedrockReasoningContent> reasoningContents) {
			this.reasoningContents = reasoningContents;
			return self();
		}

		@Override
		public BedrockAssistantMessage build() {
			return new BedrockAssistantMessage(this.content, this.reasoningContents, this.properties, this.toolCalls,
					this.media);
		}

	}

}
