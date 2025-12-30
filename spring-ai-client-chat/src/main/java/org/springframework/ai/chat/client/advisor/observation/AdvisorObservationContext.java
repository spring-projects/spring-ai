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

package org.springframework.ai.chat.client.advisor.observation;

import io.micrometer.observation.Observation;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.util.Assert;

/**
 * Context used to store metadata for chat client advisors.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class AdvisorObservationContext extends Observation.Context {

	private final String advisorName;

	private final ChatClientRequest chatClientRequest;

	private final int order;

	private @Nullable ChatClientResponse chatClientResponse;

	AdvisorObservationContext(String advisorName, ChatClientRequest chatClientRequest, int order) {
		Assert.hasText(advisorName, "advisorName cannot be null or empty");
		Assert.notNull(chatClientRequest, "chatClientRequest cannot be null");

		this.advisorName = advisorName;
		this.chatClientRequest = chatClientRequest;
		this.order = order;
	}

	/**
	 * Create a new {@link Builder} instance.
	 * @return the builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	public String getAdvisorName() {
		return this.advisorName;
	}

	public ChatClientRequest getChatClientRequest() {
		return this.chatClientRequest;
	}

	public int getOrder() {
		return this.order;
	}

	public @Nullable ChatClientResponse getChatClientResponse() {
		return this.chatClientResponse;
	}

	public void setChatClientResponse(@Nullable ChatClientResponse chatClientResponse) {
		this.chatClientResponse = chatClientResponse;
	}

	/**
	 * Builder for {@link AdvisorObservationContext}.
	 */
	public static final class Builder {

		private @Nullable String advisorName;

		private @Nullable ChatClientRequest chatClientRequest;

		private int order = 0;

		private Builder() {
		}

		public Builder advisorName(String advisorName) {
			this.advisorName = advisorName;
			return this;
		}

		public Builder chatClientRequest(ChatClientRequest chatClientRequest) {
			this.chatClientRequest = chatClientRequest;
			return this;
		}

		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public AdvisorObservationContext build() {
			Assert.hasText(this.advisorName, "advisorName cannot be null or empty");
			Assert.notNull(this.chatClientRequest, "chatClientRequest cannot be null");
			return new AdvisorObservationContext(this.advisorName, this.chatClientRequest, this.order);
		}

	}

}
