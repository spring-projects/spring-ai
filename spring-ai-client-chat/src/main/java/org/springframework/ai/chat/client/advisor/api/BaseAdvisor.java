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

package org.springframework.ai.chat.client.advisor.api;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.AdvisorUtils;
import org.springframework.util.Assert;

/**
 * Base advisor that implements common aspects of the {@link CallAdvisor} and
 * {@link StreamAdvisor}, reducing the boilerplate code needed to implement an advisor.
 * <p>
 * It provides default implementations for the
 * {@link #adviseCall(ChatClientRequest, CallAroundAdvisorChain)} and
 * {@link #adviseStream(ChatClientRequest, StreamAroundAdvisorChain)} methods, delegating
 * the actual logic to the {@link #before(ChatClientRequest, AdvisorChain advisorChain)}
 * and {@link #after(ChatClientResponse, AdvisorChain advisorChain)} methods.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface BaseAdvisor extends CallAdvisor, StreamAdvisor {

	Scheduler DEFAULT_SCHEDULER = Schedulers.boundedElastic();

	@Override
	default ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAroundAdvisorChain chain) {
		Assert.notNull(chatClientRequest, "chatClientRequest cannot be null");
		Assert.notNull(chain, "chain cannot be null");

		ChatClientRequest processedChatClientRequest = before(chatClientRequest, chain);
		ChatClientResponse chatClientResponse;
		if (chain instanceof CallAdvisorChain callAdvisorChain) {
			chatClientResponse = callAdvisorChain.nextCall(processedChatClientRequest);
		}
		else {
			chatClientResponse = chain.nextAroundCall(AdvisedRequest.from(processedChatClientRequest))
				.toChatClientResponse();
		}
		return after(chatClientResponse, chain);
	}

	@Override
	@Deprecated
	default AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
		Assert.notNull(advisedRequest, "advisedRequest cannot be null");
		Assert.notNull(chain, "chain cannot be null");

		AdvisedRequest processedAdvisedRequest = before(advisedRequest);
		AdvisedResponse advisedResponse = chain.nextAroundCall(processedAdvisedRequest);
		return after(advisedResponse);
	}

	@Override
	default Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAroundAdvisorChain chain) {
		Assert.notNull(chatClientRequest, "chatClientRequest cannot be null");
		Assert.notNull(chain, "chain cannot be null");
		Assert.notNull(getScheduler(), "scheduler cannot be null");

		Flux<ChatClientResponse> chatClientResponseFlux;
		if (chain instanceof StreamAdvisorChain streamAdvisorChain) {
			chatClientResponseFlux = Mono.just(chatClientRequest)
				.publishOn(getScheduler())
				.map(request -> this.before(request, streamAdvisorChain))
				.flatMapMany(streamAdvisorChain::nextStream);
		}
		else {
			chatClientResponseFlux = Mono.just(AdvisedRequest.from(chatClientRequest))
				.publishOn(getScheduler())
				.map(this::before)
				.flatMapMany(chain::nextAroundStream)
				.map(AdvisedResponse::toChatClientResponse);
		}

		return chatClientResponseFlux.map(response -> {
			if (AdvisorUtils.onFinishReason().test(response)) {
				response = after(response, chain);
			}
			return response;
		}).onErrorResume(error -> Flux.error(new IllegalStateException("Stream processing failed", error)));
	}

	@Override
	@Deprecated
	default Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
		Assert.notNull(advisedRequest, "advisedRequest cannot be null");
		Assert.notNull(chain, "chain cannot be null");
		Assert.notNull(getScheduler(), "scheduler cannot be null");

		Flux<AdvisedResponse> advisedResponses = Mono.just(advisedRequest)
			.publishOn(getScheduler())
			.map(this::before)
			.flatMapMany(chain::nextAroundStream);

		return advisedResponses.map(ar -> {
			if (AdvisedResponseStreamUtils.onFinishReason().test(ar)) {
				ar = after(ar);
			}
			return ar;
		}).onErrorResume(error -> Flux.error(new IllegalStateException("Stream processing failed", error)));
	}

	@Override
	default String getName() {
		return this.getClass().getSimpleName();
	}

	/**
	 * Logic to be executed before the rest of the advisor chain is called.
	 */
	default ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
		Assert.notNull(chatClientRequest, "chatClientRequest cannot be null");
		return before(AdvisedRequest.from(chatClientRequest)).toChatClientRequest();
	}

	/**
	 * Logic to be executed after the rest of the advisor chain is called.
	 */
	default ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
		Assert.notNull(chatClientResponse, "chatClientResponse cannot be null");
		return after(AdvisedResponse.from(chatClientResponse)).toChatClientResponse();
	}

	/**
	 * Logic to be executed before the rest of the advisor chain is called.
	 * @deprecated in favor of {@link #before(ChatClientRequest,AdvisorChain)}
	 */
	@Deprecated
	AdvisedRequest before(AdvisedRequest request);

	/**
	 * Logic to be executed after the rest of the advisor chain is called.
	 * @deprecated in favor of {@link #after(ChatClientResponse,AdvisorChain)}
	 */
	@Deprecated
	AdvisedResponse after(AdvisedResponse advisedResponse);

	/**
	 * Scheduler used for processing the advisor logic when streaming.
	 */
	default Scheduler getScheduler() {
		return DEFAULT_SCHEDULER;
	}

}
