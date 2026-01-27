/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.chat.messages;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The ToolResponseMessage class represents a message with a function content in a chat
 * application.
 *
 * @author Christian Tzolov
 * @author Eric Bottard
 * @since 1.0.0
 */
public class ToolResponseMessage extends AbstractMessage {

	protected final List<ToolResponse> responses;

	protected ToolResponseMessage(List<ToolResponse> responses, Map<String, Object> metadata) {
		super(MessageType.TOOL, "", metadata);
		this.responses = responses;
	}

	public static Builder builder() {
		return new Builder();
	}

	public List<ToolResponse> getResponses() {
		return this.responses;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ToolResponseMessage that)) {
			return false;
		}
		if (!super.equals(o)) {
			return false;
		}
		return Objects.equals(this.responses, that.responses);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), this.responses);
	}

	@Override
	public String toString() {
		return "ToolResponseMessage{" + "responses=" + this.responses + ", messageType=" + this.messageType
				+ ", metadata=" + this.metadata + '}';
	}

	public record ToolResponse(String id, String name, String responseData, Map<String, Object> metadata) {

		public ToolResponse {
			metadata = Map.copyOf(metadata);
		}

		public ToolResponse(String id, String name, String responseData) {
			this(id, name, responseData, Map.of());
		}

	}

	public static final class Builder {

		private List<ToolResponse> responses = List.of();

		private Map<String, Object> metadata = Map.of();

		private Builder() {
		}

		public Builder responses(List<ToolResponse> responses) {
			this.responses = responses;
			return this;
		}

		public Builder metadata(Map<String, Object> metadata) {
			this.metadata = metadata;
			return this;
		}

		public ToolResponseMessage build() {
			return new ToolResponseMessage(this.responses, this.metadata);
		}

	}

}
