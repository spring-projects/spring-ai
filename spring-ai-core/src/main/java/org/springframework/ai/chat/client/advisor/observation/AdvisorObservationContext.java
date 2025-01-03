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

package org.springframework.ai.chat.client.advisor.observation;

import java.util.Map;

import io.micrometer.observation.Observation;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.lang.Nullable;
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

	private final Type advisorType;

	/**
	 * The order of the advisor in the advisor chain.
	 */
	private final int order;

	/**
	 * The {@link AdvisedRequest} data to be advised. Represents the row
	 * {@link ChatClient.ChatClientRequestSpec} data before sealed into a {@link Prompt}.
	 */
	@Nullable
	private AdvisedRequest advisorRequest;

	/**
	 * The shared data between the advisors in the chain. It is shared between all request
	 * and response advising points of all advisors in the chain.
	 */
	@Nullable
	private Map<String, Object> advisorRequestContext;

	/**
	 * the shared data between the advisors in the chain. It is shared between all request
	 * and response advising points of all advisors in the chain.
	 */
	@Nullable
	private Map<String, Object> advisorResponseContext;

	/**
	 * Create a new {@link AdvisorObservationContext}.
	 * @param advisorName the advisor name
	 * @param advisorType the advisor type
	 * @param advisorRequest the advised request
	 * @param advisorRequestContext the shared data between the advisors in the chain
	 * @param advisorResponseContext the shared data between the advisors in the chain
	 * @param order the order of the advisor in the advisor chain
	 */
	public AdvisorObservationContext(String advisorName, Type advisorType, @Nullable AdvisedRequest advisorRequest,
			@Nullable Map<String, Object> advisorRequestContext, @Nullable Map<String, Object> advisorResponseContext,
			int order) {
		Assert.hasText(advisorName, "advisorName must not be null or empty");
		Assert.notNull(advisorType, "advisorType must not be null");

		this.advisorName = advisorName;
		this.advisorType = advisorType;
		this.advisorRequest = advisorRequest;
		this.advisorRequestContext = advisorRequestContext;
		this.advisorResponseContext = advisorResponseContext;
		this.order = order;
	}

	/**
	 * Create a new {@link Builder} instance.
	 * @return the builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * The advisor name.
	 * @return the advisor name
	 */
	public String getAdvisorName() {
		return this.advisorName;
	}

	/**
	 * The type of the advisor.
	 * @return the type of the advisor
	 */
	public Type getAdvisorType() {
		return this.advisorType;
	}

	/**
	 * The order of the advisor in the advisor chain.
	 * @return the order of the advisor in the advisor chain
	 */
	@Nullable
	public AdvisedRequest getAdvisedRequest() {
		return this.advisorRequest;
	}

	/**
	 * Set the {@link AdvisedRequest} data to be advised. Represents the row
	 * {@link ChatClient.ChatClientRequestSpec} data before sealed into a {@link Prompt}.
	 * @param advisedRequest the advised request
	 */
	public void setAdvisedRequest(@Nullable AdvisedRequest advisedRequest) {
		this.advisorRequest = advisedRequest;
	}

	/**
	 * Get the shared data between the advisors in the chain. It is shared between all
	 * request and response advising points of all advisors in the chain.
	 * @return the shared data between the advisors in the chain
	 */
	@Nullable
	public Map<String, Object> getAdvisorRequestContext() {
		return this.advisorRequestContext;
	}

	/**
	 * Set the shared data between the advisors in the chain. It is shared between all
	 * request and response advising points of all advisors in the chain.
	 * @param advisorRequestContext the shared data between the advisors in the chain
	 */
	public void setAdvisorRequestContext(@Nullable Map<String, Object> advisorRequestContext) {
		this.advisorRequestContext = advisorRequestContext;
	}

	/**
	 * Get the shared data between the advisors in the chain. It is shared between all
	 * request and response advising points of all advisors in the chain.
	 * @return the shared data between the advisors in the chain
	 */
	@Nullable
	public Map<String, Object> getAdvisorResponseContext() {
		return this.advisorResponseContext;
	}

	/**
	 * Set the shared data between the advisors in the chain. It is shared between all
	 * request and response advising points of all advisors in the chain.
	 * @param advisorResponseContext the shared data between the advisors in the chain
	 */
	public void setAdvisorResponseContext(@Nullable Map<String, Object> advisorResponseContext) {
		this.advisorResponseContext = advisorResponseContext;
	}

	/**
	 * The order of the advisor in the advisor chain.
	 * @return the order of the advisor in the advisor chain
	 */
	public int getOrder() {
		return this.order;
	}

	/**
	 * The type of the advisor.
	 */
	public enum Type {

		/**
		 * The advisor is called before the advised request is executed.
		 */
		BEFORE,
		/**
		 * The advisor is called after the advised request is executed.
		 */
		AFTER,
		/**
		 * The advisor is called around the advised request.
		 */
		AROUND

	}

	/**
	 * Builder for {@link AdvisorObservationContext}.
	 */
	public static final class Builder {

		private String advisorName;

		private Type advisorType;

		private AdvisedRequest advisorRequest;

		private Map<String, Object> advisorRequestContext;

		private Map<String, Object> advisorResponseContext;

		private int order = 0;

		private Builder() {
		}

		/**
		 * Set the advisor name.
		 * @param advisorName the advisor name
		 * @return the builder
		 */
		public Builder advisorName(String advisorName) {
			this.advisorName = advisorName;
			return this;
		}

		/**
		 * Set the advisor type.
		 * @param advisorType the advisor type
		 * @return the builder
		 */
		public Builder advisorType(Type advisorType) {
			this.advisorType = advisorType;
			return this;
		}

		/**
		 * Set the advised request.
		 * @param advisedRequest the advised request
		 * @return the builder
		 */
		public Builder advisedRequest(AdvisedRequest advisedRequest) {
			this.advisorRequest = advisedRequest;
			return this;
		}

		/**
		 * Set the advisor request context.
		 * @param advisorRequestContext the advisor request context
		 * @return the builder
		 */
		public Builder advisorRequestContext(Map<String, Object> advisorRequestContext) {
			this.advisorRequestContext = advisorRequestContext;
			return this;
		}

		/**
		 * Set the advisor response context.
		 * @param advisorResponseContext the advisor response context
		 * @return the builder
		 */
		public Builder advisorResponseContext(Map<String, Object> advisorResponseContext) {
			this.advisorResponseContext = advisorResponseContext;
			return this;
		}

		/**
		 * Set the order of the advisor in the advisor chain.
		 * @param order the order of the advisor in the advisor chain
		 * @return the builder
		 */
		public Builder order(int order) {
			this.order = order;
			return this;
		}

		/**
		 * Build the {@link AdvisorObservationContext}.
		 * @return the {@link AdvisorObservationContext}
		 */
		public AdvisorObservationContext build() {
			return new AdvisorObservationContext(this.advisorName, this.advisorType, this.advisorRequest,
					this.advisorRequestContext, this.advisorResponseContext, this.order);
		}

	}

}
