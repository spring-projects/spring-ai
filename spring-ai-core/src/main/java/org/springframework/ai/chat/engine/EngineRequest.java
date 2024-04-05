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

package org.springframework.ai.chat.engine;

import org.springframework.ai.chat.prompt.Prompt;

/**
 * @author Christian Tzolov
 */
public class EngineRequest {

	private final String conversationId;

	private final Prompt prompt;

	public EngineRequest(String conversationId, Prompt prompt) {
		this.conversationId = conversationId;
		this.prompt = prompt;
	}

	public String getConversationId() {
		return conversationId;
	}

	public Prompt getPrompt() {
		return prompt;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((conversationId == null) ? 0 : conversationId.hashCode());
		result = prime * result + ((prompt == null) ? 0 : prompt.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		EngineRequest other = (EngineRequest) obj;
		if (conversationId == null) {
			if (other.conversationId != null)
				return false;
		}
		else if (!conversationId.equals(other.conversationId))
			return false;
		if (prompt == null) {
			if (other.prompt != null)
				return false;
		}
		else if (!prompt.equals(other.prompt))
			return false;
		return true;
	}

}
