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

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationContext;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationConvention;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationDocumentation;
import org.springframework.ai.chat.client.advisor.observation.DefaultAdvisorObservationConvention;
import org.springframework.core.OrderComparator;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Default implementation for the {@link BaseAdvisorChain}. Used by the
 * {@link org.springframework.ai.chat.client.ChatClient} to delegate the call to the next
 * {@link CallAdvisor} or {@link StreamAdvisor} in the chain.
 *
 * @author Christian Tzolov
 * @author Dariusz Jedrzejczyk
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class DefaultAroundAdvisorChain implements BaseAdvisorChain {

	public static final AdvisorObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultAdvisorObservationConvention();

	private final Deque<CallAroundAdvisor> callAroundAdvisors;

	private final Deque<StreamAroundAdvisor> streamAroundAdvisors;

	private final ObservationRegistry observationRegistry;

	DefaultAroundAdvisorChain(ObservationRegistry observationRegistry, Deque<CallAroundAdvisor> callAroundAdvisors,
			Deque<StreamAroundAdvisor> streamAroundAdvisors) {

		Assert.notNull(observationRegistry, "the observationRegistry must be non-null");
		Assert.notNull(callAroundAdvisors, "the callAroundAdvisors must be non-null");
		Assert.notNull(streamAroundAdvisors, "the streamAroundAdvisors must be non-null");

		this.observationRegistry = observationRegistry;
		this.callAroundAdvisors = callAroundAdvisors;
		this.streamAroundAdvisors = streamAroundAdvisors;
	}

	public static Builder builder(ObservationRegistry observationRegistry) {
		return new Builder(observationRegistry);
	}

	@Override
	public ChatClientResponse nextCall(ChatClientRequest chatClientRequest) {
		Assert.notNull(chatClientRequest, "the chatClientRequest cannot be null");

		if (this.callAroundAdvisors.isEmpty()) {
			throw new IllegalStateException("No CallAdvisors available to execute");
		}

		var advisor = this.callAroundAdvisors.pop();

		var observationContext = AdvisorObservationContext.builder()
			.advisorName(advisor.getName())
			.chatClientRequest(chatClientRequest)
			.order(advisor.getOrder())
			.build();

		return AdvisorObservationDocumentation.AI_ADVISOR
			.observation(null, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry)
			.observe(() -> {
				// Supports both deprecated and new API.
				if (advisor instanceof CallAdvisor callAdvisor) {
					return callAdvisor.adviseCall(chatClientRequest, this);
				}
				AdvisedResponse advisedResponse = advisor.aroundCall(AdvisedRequest.from(chatClientRequest), this);
				ChatClientResponse chatClientResponse = advisedResponse.toChatClientResponse();
				observationContext.setChatClientResponse(chatClientResponse);
				return chatClientResponse;
			});
	}

	/**
	 * @deprecated Use {@link #nextCall(ChatClientRequest)} instead
	 */
	@Override
	@Deprecated
	public AdvisedResponse nextAroundCall(AdvisedRequest advisedRequest) {
		Assert.notNull(advisedRequest, "the advisedRequest cannot be null");

		if (this.callAroundAdvisors.isEmpty()) {
			throw new IllegalStateException("No AroundAdvisor available to execute");
		}

		var advisor = this.callAroundAdvisors.pop();

		var observationContext = AdvisorObservationContext.builder()
			.advisorName(advisor.getName())
			.chatClientRequest(advisedRequest.toChatClientRequest())
			.order(advisor.getOrder())
			.build();

		return AdvisorObservationDocumentation.AI_ADVISOR
			.observation(null, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry)
			.observe(() -> {
				// Supports both deprecated and new API.
				if (advisor instanceof CallAdvisor callAdvisor) {
					ChatClientResponse chatClientResponse = callAdvisor.adviseCall(advisedRequest.toChatClientRequest(),
							this);
					return AdvisedResponse.from(chatClientResponse);
				}
				AdvisedResponse advisedResponse = advisor.aroundCall(advisedRequest, this);
				ChatClientResponse chatClientResponse = advisedResponse.toChatClientResponse();
				observationContext.setChatClientResponse(chatClientResponse);
				return advisedResponse;
			});
	}

	@Override
	public Flux<ChatClientResponse> nextStream(ChatClientRequest chatClientRequest) {
		Assert.notNull(chatClientRequest, "the chatClientRequest cannot be null");

		return Flux.deferContextual(contextView -> {
			if (this.streamAroundAdvisors.isEmpty()) {
				return Flux.error(new IllegalStateException("No StreamAdvisors available to execute"));
			}

			var advisor = this.streamAroundAdvisors.pop();

			AdvisorObservationContext observationContext = AdvisorObservationContext.builder()
				.advisorName(advisor.getName())
				.chatClientRequest(chatClientRequest)
				.order(advisor.getOrder())
				.build();

			var observation = AdvisorObservationDocumentation.AI_ADVISOR.observation(null,
					DEFAULT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			// @formatter:off
			return Flux.defer(() -> {
				// Supports both deprecated and new API.
				if (advisor instanceof StreamAdvisor streamAdvisor) {
					return streamAdvisor.adviseStream(chatClientRequest, this)
							.doOnError(observation::error)
							.doFinally(s -> observation.stop())
							.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
				}
				return advisor.aroundStream(AdvisedRequest.from(chatClientRequest), this)
						.doOnError(observation::error)
						.doFinally(s -> observation.stop())
						.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation))
						.map(AdvisedResponse::toChatClientResponse);
			});
			// @formatter:on
		});
	}

	/**
	 * @deprecated Use {@link #nextStream(ChatClientRequest)} instead.
	 */
	@Override
	@Deprecated
	public Flux<AdvisedResponse> nextAroundStream(AdvisedRequest advisedRequest) {
		Assert.notNull(advisedRequest, "the advisedRequest cannot be null");

		return Flux.deferContextual(contextView -> {
			if (this.streamAroundAdvisors.isEmpty()) {
				return Flux.error(new IllegalStateException("No AroundAdvisor available to execute"));
			}

			var advisor = this.streamAroundAdvisors.pop();

			AdvisorObservationContext observationContext = AdvisorObservationContext.builder()
				.advisorName(advisor.getName())
				.chatClientRequest(advisedRequest.toChatClientRequest())
				.order(advisor.getOrder())
				.build();

			var observation = AdvisorObservationDocumentation.AI_ADVISOR.observation(null,
					DEFAULT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			// @formatter:off
			return Flux.defer(() -> {
				// Supports both deprecated and new API.
				if (advisor instanceof StreamAdvisor streamAdvisor) {
					return streamAdvisor.adviseStream(advisedRequest.toChatClientRequest(), this)
							.doOnError(observation::error)
							.doFinally(s -> observation.stop())
							.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation))
							.map(AdvisedResponse::from);
				}

				return advisor.aroundStream(advisedRequest, this)
						.doOnError(observation::error)
						.doFinally(s -> observation.stop())
						.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
			});
			// @formatter:on
		});
	}

	public static class Builder {

		private final ObservationRegistry observationRegistry;

		private final Deque<CallAroundAdvisor> callAroundAdvisors;

		private final Deque<StreamAroundAdvisor> streamAroundAdvisors;

		public Builder(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			this.callAroundAdvisors = new ConcurrentLinkedDeque<>();
			this.streamAroundAdvisors = new ConcurrentLinkedDeque<>();
		}

		public Builder push(Advisor aroundAdvisor) {
			Assert.notNull(aroundAdvisor, "the aroundAdvisor must be non-null");
			return this.pushAll(List.of(aroundAdvisor));
		}

		public Builder pushAll(List<? extends Advisor> advisors) {
			Assert.notNull(advisors, "the advisors must be non-null");
			if (!CollectionUtils.isEmpty(advisors)) {
				List<CallAroundAdvisor> callAroundAdvisorList = advisors.stream()
					.filter(a -> a instanceof CallAroundAdvisor)
					.map(a -> (CallAroundAdvisor) a)
					.toList();

				if (!CollectionUtils.isEmpty(callAroundAdvisorList)) {
					callAroundAdvisorList.forEach(this.callAroundAdvisors::push);
				}

				List<StreamAroundAdvisor> streamAroundAdvisorList = advisors.stream()
					.filter(a -> a instanceof StreamAroundAdvisor)
					.map(a -> (StreamAroundAdvisor) a)
					.toList();

				if (!CollectionUtils.isEmpty(streamAroundAdvisorList)) {
					streamAroundAdvisorList.forEach(this.streamAroundAdvisors::push);
				}

				this.reOrder();
			}
			return this;
		}

		/**
		 * (Re)orders the advisors in priority order based on their Ordered attribute.
		 */
		private void reOrder() {
			ArrayList<CallAroundAdvisor> callAdvisors = new ArrayList<>(this.callAroundAdvisors);
			OrderComparator.sort(callAdvisors);
			this.callAroundAdvisors.clear();
			callAdvisors.forEach(this.callAroundAdvisors::addLast);

			ArrayList<StreamAroundAdvisor> streamAdvisors = new ArrayList<>(this.streamAroundAdvisors);
			OrderComparator.sort(streamAdvisors);
			this.streamAroundAdvisors.clear();
			streamAdvisors.forEach(this.streamAroundAdvisors::addLast);
		}

		public DefaultAroundAdvisorChain build() {
			return new DefaultAroundAdvisorChain(this.observationRegistry, this.callAroundAdvisors,
					this.streamAroundAdvisors);
		}

	}

}
