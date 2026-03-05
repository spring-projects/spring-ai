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

package org.springframework.ai.chat.client.advisor;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * An advisor that blocks the call to the model provider if the user input contains any of
 * the sensitive words.
 *
 * @author Christian Tzolov
 * @author Ilayaperumal Gopinathan
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class SafeGuardAdvisor implements CallAdvisor, StreamAdvisor {

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
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		if (!CollectionUtils.isEmpty(this.sensitiveWords)
				&& this.sensitiveWords.stream().anyMatch(w -> chatClientRequest.prompt().getContents().contains(w))) {
			return createFailureResponse(chatClientRequest);
		}

		return callAdvisorChain.nextCall(chatClientRequest);
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {
		if (!CollectionUtils.isEmpty(this.sensitiveWords)
				&& this.sensitiveWords.stream().anyMatch(w -> chatClientRequest.prompt().getContents().contains(w))) {
			return Flux.just(createFailureResponse(chatClientRequest));
		}

		return streamAdvisorChain.nextStream(chatClientRequest);
	}

	private ChatClientResponse createFailureResponse(ChatClientRequest chatClientRequest) {
		return ChatClientResponse.builder()
			.chatResponse(ChatResponse.builder()
				.generations(List.of(new Generation(new AssistantMessage(this.failureResponse))))
				.build())
			.context(Map.copyOf(chatClientRequest.context()))
			.build();
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public static final class Builder {

		private @Nullable List<String> sensitiveWords;

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
			Assert.state(this.sensitiveWords != null, "Sensitive words must not be null!");
			return new SafeGuardAdvisor(this.sensitiveWords, this.failureResponse, this.order);
		}

	}

}
