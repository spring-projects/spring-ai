/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.chat.client.advisor;

import org.springframework.ai.chat.client.ChatClient.AdvisorSpec;
import org.springframework.util.Assert;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.*;

/**
 * Provides a way to configure an AdvisorSpec with a conversation ID and retrieval size.
 *
 * @author Jonghoon Park
 */
public class ChatMemoryAdvisorOptions {

	private Object conversationId = DEFAULT_CHAT_MEMORY_CONVERSATION_ID;

	private Integer retrieveSize = DEFAULT_CHAT_MEMORY_RESPONSE_SIZE;

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final ChatMemoryAdvisorOptions options;

		public Builder() {
			this.options = new ChatMemoryAdvisorOptions();
		}

		public Builder conversationId(Object conversationId) {
			this.options.conversationId = conversationId;
			return this;
		}

		public Builder retrieveSize(int retrieveSize) {
			this.options.retrieveSize = retrieveSize;
			return this;
		}

		public ChatMemoryAdvisorOptions build() {
			return this.options;
		}

	}

	public void applyTo(AdvisorSpec advisorSpec) {
		Assert.notNull(advisorSpec, "advisorSpec must not be null");

		advisorSpec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId);
		advisorSpec.param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, retrieveSize);
	}

}
