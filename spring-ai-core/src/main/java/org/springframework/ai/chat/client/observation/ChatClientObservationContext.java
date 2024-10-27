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

package org.springframework.ai.chat.client.observation;

import io.micrometer.observation.Observation;

import org.springframework.ai.chat.client.DefaultChatClient.DefaultChatClientRequestSpec;
import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Context used to store metadata for chat client workflows.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class ChatClientObservationContext extends Observation.Context {

	private final DefaultChatClientRequestSpec request;

	private final AiOperationMetadata operationMetadata = new AiOperationMetadata(AiOperationType.FRAMEWORK.value(),
			AiProvider.SPRING_AI.value());

	private final boolean stream;

	@Nullable
	private String format;

	ChatClientObservationContext(DefaultChatClientRequestSpec requestSpec, String format, boolean isStream) {
		Assert.notNull(requestSpec, "requestSpec cannot be null");
		this.request = requestSpec;
		this.format = format;
		this.stream = isStream;
	}

	public static Builder builder() {
		return new Builder();
	}

	public DefaultChatClientRequestSpec getRequest() {
		return this.request;
	}

	public AiOperationMetadata getOperationMetadata() {
		return this.operationMetadata;
	}

	public boolean isStream() {
		return this.stream;
	}

	@Nullable
	public String getFormat() {
		return this.format;
	}

	public void setFormat(@Nullable String format) {
		this.format = format;
	}

	public static final class Builder {

		private DefaultChatClientRequestSpec request;

		private String format;

		private boolean isStream = false;

		private Builder() {
		}

		public Builder withRequest(DefaultChatClientRequestSpec request) {
			this.request = request;
			return this;
		}

		public Builder withFormat(String format) {
			this.format = format;
			return this;
		}

		public Builder withStream(boolean isStream) {
			this.isStream = isStream;
			return this;
		}

		public ChatClientObservationContext build() {
			return new ChatClientObservationContext(this.request, this.format, this.isStream);
		}

	}

}
