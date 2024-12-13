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

package org.springframework.ai.chat.client.advisor;

import java.util.List;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * A {@link CallAroundAdvisor} and {@link StreamAroundAdvisor} that filters out the
 * response if the user input contains any of the sensitive words.
 *
 * @author Christian Tzolov
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0
 */
public class SafeGuardAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

	private static final String DEFAULT_FAILURE_RESPONSE = "I'm unable to respond to that due to sensitive content. Could we rephrase or discuss something else?";

	private static final int DEFAULT_ORDER = 0;

	private final String failureResponse;

	private final List<String> sensitiveWords;

	private final int order;

	public SafeGuardAdvisor(List<String> sensitiveWords) {
		this(sensitiveWords, DEFAULT_FAILURE_RESPONSE, DEFAULT_ORDER);
	}

	public SafeGuardAdvisor(List<String> sensitiveWords, String failureResponse, int order) {
		Assert.notNull(sensitiveWords, "Sensitive words must not be null!");
		Assert.notNull(failureResponse, "Failure response must not be null!");
		this.sensitiveWords = sensitiveWords;
		this.failureResponse = failureResponse;
		this.order = order;
	}

	public static Builder builder() {
		return new Builder();
	}

	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {

		if (!CollectionUtils.isEmpty(this.sensitiveWords)
				&& this.sensitiveWords.stream().anyMatch(w -> advisedRequest.userText().contains(w))) {

			return createFailureResponse(advisedRequest);
		}

		return chain.nextAroundCall(advisedRequest);
	}

	@Override
	public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {

		if (!CollectionUtils.isEmpty(this.sensitiveWords)
				&& this.sensitiveWords.stream().anyMatch(w -> advisedRequest.userText().contains(w))) {
			return Flux.just(createFailureResponse(advisedRequest));
		}

		return chain.nextAroundStream(advisedRequest);
	}

	private AdvisedResponse createFailureResponse(AdvisedRequest advisedRequest) {
		return new AdvisedResponse(ChatResponse.builder()
			.withGenerations(List.of(new Generation(new AssistantMessage(this.failureResponse))))
			.build(), advisedRequest.adviseContext());
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public static final class Builder {

		private List<String> sensitiveWords;

		private String failureResponse = DEFAULT_FAILURE_RESPONSE;

		private int order = DEFAULT_ORDER;

		private Builder() {
		}

		public Builder sensitiveWords(List<String> sensitiveWords) {
			this.sensitiveWords = sensitiveWords;
			return this;
		}

		public Builder failureResponse(String failureResponse) {
			this.failureResponse = failureResponse;
			return this;
		}

		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public SafeGuardAdvisor build() {
			return new SafeGuardAdvisor(this.sensitiveWords, this.failureResponse, this.order);
		}

	}

}
