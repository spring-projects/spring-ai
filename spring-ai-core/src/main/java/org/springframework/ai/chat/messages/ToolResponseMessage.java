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

import java.util.Map;
import java.util.Objects;

/**
 * The FunctionMessage class represents a message with a function content in a chat
 * application.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class ToolResponseMessage extends AbstractMessage {

	private final String id;

	private final String name;

	public ToolResponseMessage(String id, String name, String content) {
		this(id, name, content, Map.of());
	}

	public ToolResponseMessage(String id, String name, String content, Map<String, Object> metadata) {
		super(MessageType.TOOL, content, metadata);
		this.id = id;
		this.name = name;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.name, getContent(), this.metadata, this.messageType);
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
		return Objects.equals(id, other.id) && Objects.equals(this.name, other.name)
				&& Objects.equals(getContent(), other.getContent()) && Objects.equals(this.metadata, other.metadata)
				&& this.messageType == other.messageType;
	}

	@Override
	public String toString() {
		return "FunctionMessage [id=" + id + ", name=" + name + ", messageType=" + messageType + ", textContent="
				+ textContent + "]";
	}

}
