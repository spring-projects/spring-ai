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

	protected final List<ToolResponse> responses;

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
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ToolResponseMessage that))
			return false;
		if (!super.equals(o))
			return false;
		return Objects.equals(responses, that.responses);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), responses);
	}

	@Override
	public String toString() {
		return "ToolResponseMessage{" + "responses=" + responses + ", messageType=" + messageType + ", metadata="
				+ metadata + '}';
	}

}
