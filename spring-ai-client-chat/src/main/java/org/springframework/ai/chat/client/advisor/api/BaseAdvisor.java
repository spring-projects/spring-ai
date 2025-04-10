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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.util.Assert;

/**
 * Base advisor that implements common aspects of the {@link CallAroundAdvisor} and
 * {@link StreamAroundAdvisor}, reducing the boilerplate code needed to implement an
 * advisor. It provides default implementations for the
 * {@link #aroundCall(AdvisedRequest, CallAroundAdvisorChain)} and
 * {@link #aroundStream(AdvisedRequest, StreamAroundAdvisorChain)} methods, delegating the
 * actual logic to the {@link #before(AdvisedRequest)} and {@link #after(AdvisedResponse)}
 * methods.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface BaseAdvisor extends CallAroundAdvisor, StreamAroundAdvisor {

	Scheduler DEFAULT_SCHEDULER = Schedulers.boundedElastic();

	@Override
	default AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
		Assert.notNull(advisedRequest, "advisedRequest cannot be null");
		Assert.notNull(chain, "chain cannot be null");

		AdvisedRequest processedAdvisedRequest = before(advisedRequest);
		AdvisedResponse advisedResponse = chain.nextAroundCall(processedAdvisedRequest);
		return after(advisedResponse);
	}

	@Override
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
	AdvisedRequest before(AdvisedRequest request);

	/**
	 * Logic to be executed after the rest of the advisor chain is called.
	 */
	AdvisedResponse after(AdvisedResponse advisedResponse);

	/**
	 * Scheduler used for processing the advisor logic when streaming.
	 */
	default Scheduler getScheduler() {
		return DEFAULT_SCHEDULER;
	}

}
