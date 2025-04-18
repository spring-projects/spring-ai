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

import io.micrometer.observation.Observation;

import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Context used to store metadata for chat client workflows.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class ChatClientObservationContext extends Observation.Context {

	private final ChatClientRequest request;

	private final AiOperationMetadata operationMetadata = new AiOperationMetadata(AiOperationType.FRAMEWORK.value(),
			AiProvider.SPRING_AI.value());

	private final boolean stream;

	ChatClientObservationContext(ChatClientRequest chatClientRequest, boolean isStream) {
		Assert.notNull(chatClientRequest, "chatClientRequest cannot be null");
		this.request = chatClientRequest;
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

	public boolean isStream() {
		return this.stream;
	}

	/**
	 * @deprecated not used anymore. The format instructions are already included in the
	 * ChatModelObservationContext.
	 */
	@Nullable
	@Deprecated
	public String getFormat() {
		if (this.request.context().get(ChatClientAttributes.OUTPUT_FORMAT.getKey()) instanceof String format) {
			return format;
		}
		return null;
	}

	/**
	 * @deprecated not used anymore. The format instructions are already included in the
	 * ChatModelObservationContext.
	 */
	@Deprecated
	public void setFormat(@Nullable String format) {
		this.request.context().put(ChatClientAttributes.OUTPUT_FORMAT.getKey(), format);
	}

	public static final class Builder {

		private ChatClientRequest chatClientRequest;

		private String format;

		private boolean isStream = false;

		private Builder() {
		}

		public Builder request(ChatClientRequest chatClientRequest) {
			this.chatClientRequest = chatClientRequest;
			return this;
		}

		@Deprecated // use request(ChatClientRequest chatClientRequest)
		public Builder withRequest(ChatClientRequest chatClientRequest) {
			return request(chatClientRequest);
		}

		/**
		 * @deprecated not used anymore. The format instructions are already included in
		 * the ChatModelObservationContext.
		 */
		@Deprecated
		public Builder withFormat(String format) {
			this.format = format;
			return this;
		}

		public Builder stream(boolean isStream) {
			this.isStream = isStream;
			return this;
		}

		@Deprecated // use stream(boolean isStream)
		public Builder withStream(boolean isStream) {
			return stream(isStream);
		}

		public ChatClientObservationContext build() {
			if (StringUtils.hasText(format)) {
				this.chatClientRequest.context().put(ChatClientAttributes.OUTPUT_FORMAT.getKey(), format);
			}
			return new ChatClientObservationContext(this.chatClientRequest, this.isStream);
		}

	}

}
