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

package org.springframework.ai.chat.client.advisor.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * The data of the chat client response that can be modified before the call returns.
 *
 * @param response the chat response
 * @param adviseContext the context to advise the response
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0
 */
public record AdvisedResponse(@Nullable ChatResponse response, Map<String, Object> adviseContext) {

	/**
	 * Create a new {@link AdvisedResponse} instance.
	 * @param response the chat response
	 * @param adviseContext the context to advise the response
	 */
	public AdvisedResponse {
		Assert.notNull(adviseContext, "adviseContext cannot be null");
		Assert.noNullElements(adviseContext.keySet(), "adviseContext keys cannot be null");
		Assert.noNullElements(adviseContext.values(), "adviseContext values cannot be null");
	}

	/**
	 * Create a new {@link Builder} instance.
	 * @return a new {@link Builder} instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Create a new {@link Builder} instance from the provided {@link AdvisedResponse}.
	 * @param advisedResponse the advised response to copy
	 * @return a new {@link Builder} instance
	 */
	public static Builder from(AdvisedResponse advisedResponse) {
		Assert.notNull(advisedResponse, "advisedResponse cannot be null");
		return new Builder().response(advisedResponse.response).adviseContext(advisedResponse.adviseContext);
	}

	/**
	 * Update the context of the advised response.
	 * @param contextTransform the function to transform the context
	 * @return the updated advised response
	 */
	public AdvisedResponse updateContext(Function<Map<String, Object>, Map<String, Object>> contextTransform) {
		Assert.notNull(contextTransform, "contextTransform cannot be null");
		return new AdvisedResponse(this.response,
				Collections.unmodifiableMap(contextTransform.apply(new HashMap<>(this.adviseContext))));
	}

	/**
	 * Builder for {@link AdvisedResponse}.
	 */
	public static final class Builder {

		@Nullable
		private ChatResponse response;

		private Map<String, Object> adviseContext;

		private Builder() {
		}

		/**
		 * Set the chat response.
		 * @param response the chat response
		 * @return the builder
		 */
		public Builder response(@Nullable ChatResponse response) {
			this.response = response;
			return this;
		}

		/**
		 * Set the context to advise the response.
		 * @param adviseContext the context to advise the response
		 * @return the builder
		 */
		public Builder adviseContext(Map<String, Object> adviseContext) {
			this.adviseContext = adviseContext;
			return this;
		}

		/**
		 * @deprecated use {@link #response(ChatResponse)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withResponse(@Nullable ChatResponse response) {
			this.response = response;
			return this;
		}

		/**
		 * @deprecated use {@link #adviseContext(Map)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withAdviseContext(Map<String, Object> adviseContext) {
			this.adviseContext = adviseContext;
			return this;
		}

		/**
		 * Build the {@link AdvisedResponse}.
		 * @return the advised response
		 */
		public AdvisedResponse build() {
			return new AdvisedResponse(this.response, this.adviseContext);
		}

	}

}
