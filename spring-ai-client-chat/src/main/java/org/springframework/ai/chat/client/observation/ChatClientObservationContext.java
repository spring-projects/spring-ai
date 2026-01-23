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

package org.springframework.ai.chat.client.observation;

import java.util.List;

import io.micrometer.observation.Observation;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Context used to store metadata for chat client workflows.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 * @since 1.0.0
 */
public class ChatClientObservationContext extends Observation.Context {

	private final ChatClientRequest request;

	private @Nullable ChatClientResponse response;

	private final AiOperationMetadata operationMetadata = new AiOperationMetadata(AiOperationType.FRAMEWORK.value(),
			AiProvider.SPRING_AI.value());

	private final List<? extends Advisor> advisors;

	private final boolean stream;

	ChatClientObservationContext(ChatClientRequest chatClientRequest, List<? extends Advisor> advisors,
			boolean isStream) {
		Assert.notNull(chatClientRequest, "chatClientRequest cannot be null");
		Assert.notNull(advisors, "advisors cannot be null");
		Assert.noNullElements(advisors, "advisors cannot contain null elements");
		this.request = chatClientRequest;
		this.advisors = advisors;
		this.stream = isStream;
	}

	public static Builder builder() {
		return new Builder();
	}

	public ChatClientRequest getRequest() {
		return this.request;
	}

	public AiOperationMetadata getOperationMetadata() {
		return this.operationMetadata;
	}

	public List<? extends Advisor> getAdvisors() {
		return this.advisors;
	}

	public boolean isStream() {
		return this.stream;
	}

	public @Nullable String getFormat() {
		if (this.request.context().get(ChatClientAttributes.OUTPUT_FORMAT.getKey()) instanceof String format) {
			return format;
		}
		return null;
	}

	/**
	 * @return Chat client response
	 * @since 1.1.0
	 */
	public @Nullable ChatClientResponse getResponse() {
		return this.response;
	}

	/**
	 * @param response Chat client response to record.
	 * @since 1.1.0
	 */
	public void setResponse(ChatClientResponse response) {
		this.response = response;
	}

	public static final class Builder {

		private @Nullable ChatClientRequest chatClientRequest;

		private List<? extends Advisor> advisors = List.of();

		private @Nullable String format;

		private boolean isStream = false;

		private Builder() {
		}

		public Builder request(ChatClientRequest chatClientRequest) {
			this.chatClientRequest = chatClientRequest;
			return this;
		}

		public Builder format(@Nullable String format) {
			this.format = format;
			return this;
		}

		public Builder advisors(List<? extends Advisor> advisors) {
			this.advisors = advisors;
			return this;
		}

		public Builder stream(boolean isStream) {
			this.isStream = isStream;
			return this;
		}

		public ChatClientObservationContext build() {
			Assert.state(this.chatClientRequest != null, "chatClientRequest cannot be null");
			if (StringUtils.hasText(this.format)) {
				this.chatClientRequest.context().put(ChatClientAttributes.OUTPUT_FORMAT.getKey(), this.format);
			}
			return new ChatClientObservationContext(this.chatClientRequest, this.advisors, this.isStream);
		}

	}

}
