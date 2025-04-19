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

import java.util.Map;

import io.micrometer.observation.Observation;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

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

	@Nullable
	private ChatClientResponse chatClientResponse;

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
	 * @deprecated use the builder instead
	 */
	@Deprecated
	public AdvisorObservationContext(String advisorName, Type advisorType, @Nullable AdvisedRequest advisorRequest,
			@Nullable Map<String, Object> advisorRequestContext, @Nullable Map<String, Object> advisorResponseContext,
			int order) {
		Assert.hasText(advisorName, "advisorName cannot be null or empty");

		this.advisorName = advisorName;
		this.chatClientRequest = advisorRequest != null ? advisorRequest.toChatClientRequest()
				: ChatClientRequest.builder().prompt(new Prompt()).build();
		if (!CollectionUtils.isEmpty(advisorRequestContext)) {
			this.chatClientRequest.context().putAll(advisorRequestContext);
		}
		if (!CollectionUtils.isEmpty(advisorResponseContext)) {
			this.chatClientResponse = ChatClientResponse.builder().context(advisorResponseContext).build();
		}
		this.order = order;
	}

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

	@Nullable
	public ChatClientResponse getChatClientResponse() {
		return this.chatClientResponse;
	}

	public void setChatClientResponse(@Nullable ChatClientResponse chatClientResponse) {
		this.chatClientResponse = chatClientResponse;
	}

	/**
	 * The type of the advisor.
	 * @return the type of the advisor
	 * @deprecated advisors don't have types anymore, they're all "around"
	 */
	@Deprecated
	public Type getAdvisorType() {
		return Type.AROUND;
	}

	/**
	 * The order of the advisor in the advisor chain.
	 * @return the order of the advisor in the advisor chain
	 * @deprecated not used anymore
	 */
	@Deprecated
	public AdvisedRequest getAdvisedRequest() {
		return AdvisedRequest.from(this.chatClientRequest);
	}

	/**
	 * Set the {@link AdvisedRequest} data to be advised. Represents the row
	 * {@link ChatClient.ChatClientRequestSpec} data before sealed into a {@link Prompt}.
	 * @param advisedRequest the advised request
	 * @deprecated immutable object, use the builder instead to create a new instance
	 */
	@Deprecated
	public void setAdvisedRequest(@Nullable AdvisedRequest advisedRequest) {
		throw new IllegalStateException(
				"The AdvisedRequest is immutable. Build a new AdvisorObservationContext instead.");
	}

	/**
	 * Get the shared data between the advisors in the chain. It is shared between all
	 * request and response advising points of all advisors in the chain.
	 * @return the shared data between the advisors in the chain
	 * @deprecated use {@link #getChatClientRequest()} instead
	 */
	@Deprecated
	public Map<String, Object> getAdvisorRequestContext() {
		return this.chatClientRequest.context();
	}

	/**
	 * Set the shared data between the advisors in the chain. It is shared between all
	 * request and response advising points of all advisors in the chain.
	 * @param advisorRequestContext the shared data between the advisors in the chain
	 * @deprecated not supported anymore, use {@link #getChatClientRequest()} instead
	 */
	@Deprecated
	public void setAdvisorRequestContext(@Nullable Map<String, Object> advisorRequestContext) {
		if (!CollectionUtils.isEmpty(advisorRequestContext)) {
			this.chatClientRequest.context().putAll(advisorRequestContext);
		}
	}

	/**
	 * Get the shared data between the advisors in the chain. It is shared between all
	 * request and response advising points of all advisors in the chain.
	 * @return the shared data between the advisors in the chain
	 * @deprecated use {@link #getChatClientResponse()} instead
	 */
	@Nullable
	@Deprecated
	public Map<String, Object> getAdvisorResponseContext() {
		if (this.chatClientResponse != null) {
			return this.chatClientResponse.context();
		}
		return null;
	}

	/**
	 * Set the shared data between the advisors in the chain. It is shared between all
	 * request and response advising points of all advisors in the chain.
	 * @param advisorResponseContext the shared data between the advisors in the chain
	 * @deprecated use {@link #setChatClientResponse(ChatClientResponse)} instead
	 */
	@Deprecated
	public void setAdvisorResponseContext(@Nullable Map<String, Object> advisorResponseContext) {
		this.advisorResponseContext = advisorResponseContext;
	}

	/**
	 * The type of the advisor.
	 *
	 * @deprecated advisors don't have types anymore, they're all "around"
	 */
	@Deprecated
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

		private ChatClientRequest chatClientRequest;

		private int order = 0;

		private AdvisedRequest advisorRequest;

		private Map<String, Object> advisorRequestContext;

		private Map<String, Object> advisorResponseContext;

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

		/**
		 * Set the advisor type.
		 * @param advisorType the advisor type
		 * @return the builder
		 * @deprecated advisors don't have types anymore, they're all "around"
		 */
		@Deprecated
		public Builder advisorType(Type advisorType) {
			return this;
		}

		/**
		 * Set the advised request.
		 * @param advisedRequest the advised request
		 * @return the builder
		 * @deprecated use {@link #chatClientRequest(ChatClientRequest)} instead
		 */
		@Deprecated
		public Builder advisedRequest(AdvisedRequest advisedRequest) {
			this.advisorRequest = advisedRequest;
			return this;
		}

		/**
		 * Set the advisor request context.
		 * @param advisorRequestContext the advisor request context
		 * @return the builder
		 * @deprecated use {@link #chatClientRequest(ChatClientRequest)} instead
		 */
		@Deprecated
		public Builder advisorRequestContext(Map<String, Object> advisorRequestContext) {
			this.advisorRequestContext = advisorRequestContext;
			return this;
		}

		/**
		 * Set the advisor response context.
		 * @param advisorResponseContext the advisor response context
		 * @return the builder
		 * @deprecated use {@link #setChatClientResponse(ChatClientResponse)} instead
		 */
		@Deprecated
		public Builder advisorResponseContext(Map<String, Object> advisorResponseContext) {
			this.advisorResponseContext = advisorResponseContext;
			return this;
		}

		public AdvisorObservationContext build() {
			if (chatClientRequest != null && advisorRequest != null) {
				throw new IllegalArgumentException(
						"ChatClientRequest and AdvisedRequest cannot be set at the same time");
			}
			else if (chatClientRequest != null) {
				return new AdvisorObservationContext(this.advisorName, this.chatClientRequest, this.order);
			}
			else {
				return new AdvisorObservationContext(this.advisorName, Type.AROUND, this.advisorRequest,
						this.advisorRequestContext, this.advisorResponseContext, this.order);
			}
		}

	}

}
