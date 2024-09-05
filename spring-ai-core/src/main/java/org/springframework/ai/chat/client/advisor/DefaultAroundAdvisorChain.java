package org.springframework.ai.chat.client.advisor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationContext;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationConvention;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationDocumentation;
import org.springframework.ai.chat.client.advisor.observation.DefaultAdvisorObservationConvention;
import org.springframework.core.OrderComparator;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import reactor.core.publisher.Flux;

public class DefaultAroundAdvisorChain implements AroundAdvisorChain {

	public static final AdvisorObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultAdvisorObservationConvention();

	private final Deque<CallAroundAdvisor> callAroundAdvisors;

	private final Deque<StreamAroundAdvisor> streamAroundAdvisors;

	private final ObservationRegistry observationRegistry;

	public DefaultAroundAdvisorChain(ObservationRegistry observationRegistry) {
		this(observationRegistry, new ArrayDeque<CallAroundAdvisor>(), new ArrayDeque<StreamAroundAdvisor>());
	}

	public DefaultAroundAdvisorChain(ObservationRegistry observationRegistry,
			Deque<CallAroundAdvisor> callAroundAdvisors, Deque<StreamAroundAdvisor> streamAroundAdvisors) {
		Assert.notNull(callAroundAdvisors, "the callAroundAdvisors must be non-null");
		this.observationRegistry = observationRegistry;
		this.callAroundAdvisors = callAroundAdvisors;
		this.streamAroundAdvisors = streamAroundAdvisors;
	}

	public DefaultAroundAdvisorChain(ObservationRegistry observationRegistry, List<Advisor> advisors) {
		this(observationRegistry);
		Assert.notNull(advisors, "the advisors must be non-null");
		this.pushAll(advisors);
	}

	public void pushAll(List<? extends Advisor> advisors) {
		Assert.notNull(advisors, "the advisors must be non-null");
		if (!CollectionUtils.isEmpty(advisors)) {

			List<CallAroundAdvisor> callAroundAdvisors = advisors.stream()
				.filter(a -> a instanceof CallAroundAdvisor)
				.map(a -> (CallAroundAdvisor) a)
				.toList();

			if (!CollectionUtils.isEmpty(callAroundAdvisors)) {
				callAroundAdvisors.stream().forEach(this.callAroundAdvisors::push);
			}

			List<StreamAroundAdvisor> streamAroundAdvisors = advisors.stream()
				.filter(a -> a instanceof StreamAroundAdvisor)
				.map(a -> (StreamAroundAdvisor) a)
				.toList();

			if (!CollectionUtils.isEmpty(streamAroundAdvisors)) {
				streamAroundAdvisors.stream().forEach(this.streamAroundAdvisors::push);
			}

			this.reOrder();
		}
	}

	public void reOrder() {
		// Order the advisors in priority order based on their Ordered attribute.

		ArrayList<CallAroundAdvisor> temp = new ArrayList<>(this.callAroundAdvisors);
		OrderComparator.sort(temp);
		this.callAroundAdvisors.clear();
		temp.stream().forEach(this.callAroundAdvisors::addLast);

		ArrayList<StreamAroundAdvisor> temp2 = new ArrayList<>(this.streamAroundAdvisors);
		OrderComparator.sort(temp2);
		this.streamAroundAdvisors.clear();
		temp2.stream().forEach(this.streamAroundAdvisors::addLast);
	}

	@Override
	public AdvisedResponse nextAroundCall(AdvisedRequest advisedRequest) {

		if (this.callAroundAdvisors.isEmpty()) {
			throw new IllegalStateException("No AroundAdvisor available to execute");
		}

		var advisor = this.callAroundAdvisors.pop();

		var observationContext = AdvisorObservationContext.builder()
			.withAdvisorName(advisor.getName())
			.withAdvisorType(AdvisorObservationContext.Type.AROUND)
			.withAdvisedRequest(advisedRequest)
			.withAdvisorRequestContext(advisedRequest.adviseContext())
			.build();

		return AdvisorObservationDocumentation.AI_ADVISOR
			.observation(null, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry)
			.observe(() -> advisor.aroundCall(advisedRequest, this));
	}

	@Override
	public Flux<AdvisedResponse> nextAroundStream(AdvisedRequest advisedRequest) {

		return Flux.deferContextual(contextView -> {

			if (this.streamAroundAdvisors.isEmpty()) {
				return Flux.error(new IllegalStateException("No AroundAdvisor available to execute"));
			}

			var advisor = this.streamAroundAdvisors.pop();

			AdvisorObservationContext observationContext = AdvisorObservationContext.builder()
				.withAdvisorName(advisor.getName())
				.withAdvisorType(AdvisorObservationContext.Type.AROUND)
				.withAdvisedRequest(advisedRequest)
				.withAdvisorRequestContext(advisedRequest.adviseContext())
				.build();

			var observation = AdvisorObservationDocumentation.AI_ADVISOR.observation(null,
					DEFAULT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			// @formatter:off
			return Flux.defer(() -> advisor.aroundStream(advisedRequest, this))
				.doOnError(observation::error)
				.doFinally(s -> { observation.stop();
			}).contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
			// @formatter:on
		});
	}

	public static Builder builder(ObservationRegistry observationRegistry) {
		return new Builder(observationRegistry);
	}

	public static class Builder {

		private final DefaultAroundAdvisorChain aroundAdvisorChain;

		private final List<Advisor> aroundAdvisors = new ArrayList<>();

		public Builder(ObservationRegistry observationRegistry) {
			this.aroundAdvisorChain = new DefaultAroundAdvisorChain(observationRegistry);
		}

		public Builder push(Advisor aroundAdvisor) {
			Assert.notNull(aroundAdvisor, "the aroundAdvisor must be non-null");
			this.aroundAdvisors.add(aroundAdvisor);
			return this;
		}

		public Builder pushAll(List<Advisor> aroundAdvisors) {
			Assert.notNull(aroundAdvisors, "the aroundAdvisors must be non-null");
			this.aroundAdvisors.addAll(aroundAdvisors);
			return this;
		}

		public DefaultAroundAdvisorChain build() {
			this.aroundAdvisorChain.pushAll(this.aroundAdvisors);
			return this.aroundAdvisorChain;
		}

	}

}