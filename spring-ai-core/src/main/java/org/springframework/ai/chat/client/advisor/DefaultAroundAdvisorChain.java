package org.springframework.ai.chat.client.advisor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservableHelper;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationContext;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationDocumentation;
import org.springframework.util.Assert;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import reactor.core.publisher.Flux;

public class DefaultAroundAdvisorChain implements AroundAdvisorChain {

	private final Deque<CallAroundAdvisor> callAroundAdvisors;

	private final Deque<StreamAroundAdvisor> streamAroundAdvisors;

	private final ObservationRegistry observationRegistry;

	public DefaultAroundAdvisorChain(ObservationRegistry observationRegistry) {
		this(observationRegistry, new ArrayDeque<CallAroundAdvisor>(), new ArrayDeque<StreamAroundAdvisor>());
	}

	public DefaultAroundAdvisorChain(CallAroundAdvisor aroundAdvisor, ObservationRegistry observationRegistry) {
		this(observationRegistry, new ArrayDeque<CallAroundAdvisor>(), new ArrayDeque<StreamAroundAdvisor>());
		this.push(aroundAdvisor);
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
		advisors.forEach(this::push);
	}

	public void pushAll(List<? extends Advisor> advisors) {
		Assert.notNull(advisors, "the advisors must be non-null");
		advisors.forEach(this::push);
	}

	public void push(Advisor aroundAdvisor) {

		Assert.notNull(aroundAdvisor, "the aroundAdvisor must be non-null");

		if (aroundAdvisor instanceof CallAroundAdvisor callAroundAdvisor) {
			this.callAroundAdvisors.push(callAroundAdvisor);
		}
		// Note: the advisor can implement both the CallAroundAdvisor and
		// StreamAroundAdvisor.
		if (aroundAdvisor instanceof StreamAroundAdvisor streamAroundAdvisor) {
			this.streamAroundAdvisors.push(streamAroundAdvisor);
		}
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
			.observation(null, AdvisorObservableHelper.DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
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
					AdvisorObservableHelper.DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			return advisor.aroundStream(advisedRequest, this).doOnError(observation::error).doFinally(s -> {
				observation.stop();
			}).contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
		});
	}

	public static Builder builder(ObservationRegistry observationRegistry) {
		return new Builder(observationRegistry);
	}

	public static class Builder {

		private final DefaultAroundAdvisorChain aroundAdvisorChain;

		public Builder(ObservationRegistry observationRegistry) {
			this.aroundAdvisorChain = new DefaultAroundAdvisorChain(observationRegistry);
		}

		public Builder push(Advisor aroundAdvisor) {
			Assert.notNull(aroundAdvisor, "the aroundAdvisor must be non-null");
			this.aroundAdvisorChain.push(aroundAdvisor);
			return this;
		}

		public Builder pushAll(List<Advisor> aroundAdvisors) {
			Assert.notNull(aroundAdvisors, "the aroundAdvisors must be non-null");
			this.aroundAdvisorChain.pushAll(aroundAdvisors);
			return this;
		}

		public DefaultAroundAdvisorChain build() {
			return this.aroundAdvisorChain;
		}

	}

}