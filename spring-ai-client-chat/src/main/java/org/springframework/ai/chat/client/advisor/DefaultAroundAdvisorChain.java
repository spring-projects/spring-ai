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
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationContext;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationConvention;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationDocumentation;
import org.springframework.ai.chat.client.advisor.observation.DefaultAdvisorObservationConvention;
import org.springframework.core.OrderComparator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Default implementation for the {@link BaseAdvisorChain}. Used by the {@link ChatClient}
 * to delegate the call to the next {@link CallAdvisor} or {@link StreamAdvisor} in the
 * chain.
 *
 * @author Christian Tzolov
 * @author Dariusz Jedrzejczyk
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class DefaultAroundAdvisorChain implements BaseAdvisorChain {

	public static final AdvisorObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultAdvisorObservationConvention();

	private static final ChatClientMessageAggregator CHAT_CLIENT_MESSAGE_AGGREGATOR = new ChatClientMessageAggregator();

	private final List<CallAdvisor> originalCallAdvisors;

	private final List<StreamAdvisor> originalStreamAdvisors;

	private final Deque<CallAdvisor> callAdvisors;

	private final Deque<StreamAdvisor> streamAdvisors;

	private final ObservationRegistry observationRegistry;

	private final AdvisorObservationConvention observationConvention;

	DefaultAroundAdvisorChain(ObservationRegistry observationRegistry, Deque<CallAdvisor> callAdvisors,
			Deque<StreamAdvisor> streamAdvisors, @Nullable AdvisorObservationConvention observationConvention) {

		Assert.notNull(observationRegistry, "the observationRegistry must be non-null");
		Assert.notNull(callAdvisors, "the callAdvisors must be non-null");
		Assert.notNull(streamAdvisors, "the streamAdvisors must be non-null");

		this.observationRegistry = observationRegistry;
		this.callAdvisors = callAdvisors;
		this.streamAdvisors = streamAdvisors;
		this.originalCallAdvisors = List.copyOf(callAdvisors);
		this.originalStreamAdvisors = List.copyOf(streamAdvisors);
		this.observationConvention = observationConvention != null ? observationConvention
				: DEFAULT_OBSERVATION_CONVENTION;
	}

	public static Builder builder(ObservationRegistry observationRegistry) {
		return new Builder(observationRegistry);
	}

	@Override
	public ChatClientResponse nextCall(ChatClientRequest chatClientRequest) {
		Assert.notNull(chatClientRequest, "the chatClientRequest cannot be null");

		if (this.callAdvisors.isEmpty()) {
			throw new IllegalStateException("No CallAdvisors available to execute");
		}

		var advisor = this.callAdvisors.pop();

		var observationContext = AdvisorObservationContext.builder()
			.advisorName(advisor.getName())
			.chatClientRequest(chatClientRequest)
			.order(advisor.getOrder())
			.build();

		return AdvisorObservationDocumentation.AI_ADVISOR
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				var chatClientResponse = advisor.adviseCall(chatClientRequest, this);
				observationContext.setChatClientResponse(chatClientResponse);
				return chatClientResponse;
			});
	}

	@Override
	public Flux<ChatClientResponse> nextStream(ChatClientRequest chatClientRequest) {
		Assert.notNull(chatClientRequest, "the chatClientRequest cannot be null");

		return Flux.deferContextual(contextView -> {
			if (this.streamAdvisors.isEmpty()) {
				return Flux.error(new IllegalStateException("No StreamAdvisors available to execute"));
			}

			var advisor = this.streamAdvisors.pop();

			AdvisorObservationContext observationContext = AdvisorObservationContext.builder()
				.advisorName(advisor.getName())
				.chatClientRequest(chatClientRequest)
				.order(advisor.getOrder())
				.build();

			var observation = AdvisorObservationDocumentation.AI_ADVISOR.observation(this.observationConvention,
					DEFAULT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			// @formatter:off
			Flux<ChatClientResponse> chatClientResponse = Flux.defer(() -> advisor.adviseStream(chatClientRequest, this)
						.doOnError(observation::error)
						.doFinally(s -> observation.stop())
						.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation)));
			// @formatter:on
			return CHAT_CLIENT_MESSAGE_AGGREGATOR.aggregateChatClientResponse(chatClientResponse,
					observationContext::setChatClientResponse);
		});
	}

	@Override
	public CallAdvisorChain copy(CallAdvisor after) {
		return this.copyAdvisorsAfter(this.getCallAdvisors(), after);
	}

	@Override
	public StreamAdvisorChain copy(StreamAdvisor after) {
		return this.copyAdvisorsAfter(this.getStreamAdvisors(), after);
	}

	private DefaultAroundAdvisorChain copyAdvisorsAfter(List<? extends Advisor> advisors, Advisor after) {

		Assert.notNull(after, "The after advisor must not be null");
		Assert.notNull(advisors, "The advisors must not be null");

		int afterAdvisorIndex = advisors.indexOf(after);

		if (afterAdvisorIndex < 0) {
			throw new IllegalArgumentException("The specified advisor is not part of the chain: " + after.getName());
		}

		var remainingStreamAdvisors = advisors.subList(afterAdvisorIndex + 1, advisors.size());

		return DefaultAroundAdvisorChain.builder(this.getObservationRegistry())
			.pushAll(remainingStreamAdvisors)
			.build();
	}

	@Override
	public List<CallAdvisor> getCallAdvisors() {
		return this.originalCallAdvisors;
	}

	@Override
	public List<StreamAdvisor> getStreamAdvisors() {
		return this.originalStreamAdvisors;
	}

	@Override
	public ObservationRegistry getObservationRegistry() {
		return this.observationRegistry;
	}

	public static final class Builder {

		private final ObservationRegistry observationRegistry;

		private final Deque<CallAdvisor> callAdvisors;

		private final Deque<StreamAdvisor> streamAdvisors;

		@Nullable
		private AdvisorObservationConvention observationConvention;

		public Builder(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			this.callAdvisors = new ConcurrentLinkedDeque<>();
			this.streamAdvisors = new ConcurrentLinkedDeque<>();
		}

		public Builder observationConvention(@Nullable AdvisorObservationConvention observationConvention) {
			this.observationConvention = observationConvention;
			return this;
		}

		public Builder push(Advisor advisor) {
			Assert.notNull(advisor, "the advisor must be non-null");
			return this.pushAll(List.of(advisor));
		}

		public Builder pushAll(List<? extends Advisor> advisors) {
			Assert.notNull(advisors, "the advisors must be non-null");
			Assert.noNullElements(advisors, "the advisors must not contain null elements");
			if (!CollectionUtils.isEmpty(advisors)) {
				List<CallAdvisor> callAroundAdvisorList = advisors.stream()
					.filter(a -> a instanceof CallAdvisor)
					.map(a -> (CallAdvisor) a)
					.toList();

				if (!CollectionUtils.isEmpty(callAroundAdvisorList)) {
					callAroundAdvisorList.forEach(this.callAdvisors::push);
				}

				List<StreamAdvisor> streamAroundAdvisorList = advisors.stream()
					.filter(a -> a instanceof StreamAdvisor)
					.map(a -> (StreamAdvisor) a)
					.toList();

				if (!CollectionUtils.isEmpty(streamAroundAdvisorList)) {
					streamAroundAdvisorList.forEach(this.streamAdvisors::push);
				}

				this.reOrder();
			}
			return this;
		}

		/**
		 * (Re)orders the advisors in priority order based on their Ordered attribute.
		 */
		private void reOrder() {
			ArrayList<CallAdvisor> callAdvisors = new ArrayList<>(this.callAdvisors);
			OrderComparator.sort(callAdvisors);
			this.callAdvisors.clear();
			callAdvisors.forEach(this.callAdvisors::addLast);

			ArrayList<StreamAdvisor> streamAdvisors = new ArrayList<>(this.streamAdvisors);
			OrderComparator.sort(streamAdvisors);
			this.streamAdvisors.clear();
			streamAdvisors.forEach(this.streamAdvisors::addLast);
		}

		public DefaultAroundAdvisorChain build() {
			return new DefaultAroundAdvisorChain(this.observationRegistry, this.callAdvisors, this.streamAdvisors,
					this.observationConvention);
		}

	}

}
