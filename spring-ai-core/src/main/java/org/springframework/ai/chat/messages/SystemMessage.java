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

import java.util.Map;
import java.util.Objects;

import org.springframework.core.io.Resource;

/**
 * A message of the type 'system' passed as input. The system message gives high level
 * instructions for the conversation. This role typically provides high-level instructions
 * for the conversation. For example, you might use a system message to instruct the
 * generative to behave like a certain character or to provide answers in a specific
 * format.
 */
public class SystemMessage extends AbstractMessage {

	public SystemMessage(String textContent) {
		super(MessageType.SYSTEM, textContent, Map.of());
	}

	public SystemMessage(Resource resource) {
		super(MessageType.SYSTEM, resource, Map.of());
	}

	@Override
	public String getText() {
		return this.textContent;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof SystemMessage that)) {
			return false;
		}
		if (!super.equals(o)) {
			return false;
		}
		return Objects.equals(this.textContent, that.textContent);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), this.textContent);
	}

	@Override
	public String toString() {
		return "SystemMessage{" + "textContent='" + this.textContent + '\'' + ", messageType=" + this.messageType
				+ ", metadata=" + this.metadata + '}';
	}

}
