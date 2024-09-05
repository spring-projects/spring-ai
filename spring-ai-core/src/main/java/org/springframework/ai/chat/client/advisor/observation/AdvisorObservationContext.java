/*
* Copyright 2024 - 2024 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springframework.ai.chat.client.advisor.observation;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.Assert;

import io.micrometer.observation.Observation;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */

public class AdvisorObservationContext extends Observation.Context {

	public enum Type {

		BEFORE, AFTER, AROUND

	}

	private String advisorName;

	private Type advisorType;

	/**
	 * The {@link AdvisedRequest} data to be advised. Represents the row
	 * {@link ChatClient.ChatClientRequestSpec} data before sealed into a {@link Prompt}.
	 */
	private AdvisedRequest advisorRequest;

	/**
	 * The shared data between the advisors in the chain. It is shared between all request
	 * and response advising points of all advisors in the chain.
	 */
	private Map<String, Object> advisorRequestContext;

	/**
	 * the shared data between the advisors in the chain. It is shared between all request
	 * and response advising points of all advisors in the chain.
	 */
	private Map<String, Object> advisorResponseContext;

	/**
	 * The order of the advisor in the advisor chain.
	 */
	private int order;

	public void setAdvisorName(String advisorName) {
		this.advisorName = advisorName;
	}

	public String getAdvisorName() {
		return this.advisorName;
	}

	public Type getAdvisorType() {
		return this.advisorType;
	}

	public void setAdvisorType(Type type) {
		this.advisorType = type;
	}

	public AdvisedRequest getAdvisedRequest() {
		return this.advisorRequest;
	}

	public void setAdvisedRequest(AdvisedRequest advisedRequest) {
		this.advisorRequest = advisedRequest;
	}

	public Map<String, Object> getAdvisorRequestContext() {
		return this.advisorRequestContext;
	}

	public void setAdvisorRequestContext(Map<String, Object> advisorRequestContext) {
		this.advisorRequestContext = advisorRequestContext;
	}

	public Map<String, Object> getAdvisorResponseContext() {
		return this.advisorResponseContext;
	}

	public void setAdvisorResponseContext(Map<String, Object> advisorResponseContext) {
		this.advisorResponseContext = advisorResponseContext;
	}

	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final AdvisorObservationContext context = new AdvisorObservationContext();

		public Builder withAdvisorName(String advisorName) {
			this.context.setAdvisorName(advisorName);
			return this;
		}

		public Builder withAdvisorType(Type advisorType) {
			this.context.setAdvisorType(advisorType);
			return this;
		}

		public Builder withAdvisedRequest(AdvisedRequest advisedRequest) {
			this.context.setAdvisedRequest(advisedRequest);
			return this;
		}

		public Builder withAdvisorRequestContext(Map<String, Object> advisorRequestContext) {
			this.context.setAdvisorRequestContext(advisorRequestContext);
			return this;
		}

		public Builder withAdvisorResponseContext(Map<String, Object> advisorResponseContext) {
			this.context.setAdvisorResponseContext(advisorResponseContext);
			return this;
		}

		public Builder withOrder(int order) {
			this.context.setOrder(order);
			return this;
		}

		public AdvisorObservationContext build() {
			Assert.hasText(this.context.advisorName, "The advisorName must not be empty!");
			Assert.notNull(this.context.advisorType, "The advisorType must not be null!");
			return this.context;
		}

	}

}