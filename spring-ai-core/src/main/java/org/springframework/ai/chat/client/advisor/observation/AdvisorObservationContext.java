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

	public static Builder builder() {
		return new Builder();
	}

	public String getAdvisorName() {
		return this.advisorName;
	}

	public Type getAdvisorType() {
		return this.advisorType;
	}

	@Nullable
	public AdvisedRequest getAdvisedRequest() {
		return this.advisorRequest;
	}

	public void setAdvisedRequest(@Nullable AdvisedRequest advisedRequest) {
		this.advisorRequest = advisedRequest;
	}

	@Nullable
	public Map<String, Object> getAdvisorRequestContext() {
		return this.advisorRequestContext;
	}

	public void setAdvisorRequestContext(@Nullable Map<String, Object> advisorRequestContext) {
		this.advisorRequestContext = advisorRequestContext;
	}

	@Nullable
	public Map<String, Object> getAdvisorResponseContext() {
		return this.advisorResponseContext;
	}

	public void setAdvisorResponseContext(@Nullable Map<String, Object> advisorResponseContext) {
		this.advisorResponseContext = advisorResponseContext;
	}

	public int getOrder() {
		return this.order;
	}

	public enum Type {

		BEFORE, AFTER, AROUND

	}

	public static class Builder {

		private String advisorName;

		private Type advisorType;

		private AdvisedRequest advisorRequest;

		private Map<String, Object> advisorRequestContext;

		private Map<String, Object> advisorResponseContext;

		private int order = 0;

		public Builder withAdvisorName(String advisorName) {
			this.advisorName = advisorName;
			return this;
		}

		public Builder withAdvisorType(Type advisorType) {
			this.advisorType = advisorType;
			return this;
		}

		public Builder withAdvisedRequest(AdvisedRequest advisedRequest) {
			this.advisorRequest = advisedRequest;
			return this;
		}

		public Builder withAdvisorRequestContext(Map<String, Object> advisorRequestContext) {
			this.advisorRequestContext = advisorRequestContext;
			return this;
		}

		public Builder withAdvisorResponseContext(Map<String, Object> advisorResponseContext) {
			this.advisorResponseContext = advisorResponseContext;
			return this;
		}

		public Builder withOrder(int order) {
			this.order = order;
			return this;
		}

		public AdvisorObservationContext build() {
			return new AdvisorObservationContext(this.advisorName, this.advisorType, this.advisorRequest,
					this.advisorRequestContext, this.advisorResponseContext, this.order);
		}

	}

}
