/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.chat.messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The ToolResponseMessage class represents a message with a function content in a chat
 * application.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class ToolResponseMessage extends AbstractMessage {

	public record ToolResponse(String id, String name, String responseData) {
	};

	private List<ToolResponse> responses = new ArrayList<>();

	public ToolResponseMessage(List<ToolResponse> responses) {
		this(responses, Map.of());
	}

	public ToolResponseMessage(List<ToolResponse> responses, Map<String, Object> metadata) {
		super(MessageType.TOOL, "", metadata);
		this.responses = responses;
	}

	public List<ToolResponse> getResponses() {
		return this.responses;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.responses, getContent(), this.metadata, this.messageType);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		ToolResponseMessage other = (ToolResponseMessage) obj;
		return Objects.equals(this.responses, other.responses) && Objects.equals(getContent(), other.getContent())
				&& Objects.equals(this.metadata, other.metadata) && this.messageType == other.messageType;
	}

	@Override
	public String toString() {
		return "ToolResponseMessage [responses=" + responses + ", messageType=" + messageType + ", metadata=" + metadata
				+ "]";
	}

}
