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

import org.springframework.ai.chat.client.AdvisedRequest;
import org.springframework.ai.chat.client.RequestResponseAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import reactor.core.publisher.Flux;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class ObservableRequestResponseAdvisor implements RequestResponseAdvisor {

	private static final AdvisorObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultAdvisorObservationConvention();

	private final RequestResponseAdvisor targetAdvisor;

	private final ObservationRegistry observationRegistry;

	private final AdvisorObservationConvention customObservationConvention;

	public ObservableRequestResponseAdvisor(RequestResponseAdvisor targetAdvisor,
			ObservationRegistry observationRegistry,
			@Nullable AdvisorObservationConvention customObservationConvention) {

		Assert.notNull(targetAdvisor, "TargetAdvisor must not be null");
		Assert.notNull(observationRegistry, "ObservationRegistry must not be null");

		this.targetAdvisor = targetAdvisor;
		this.observationRegistry = observationRegistry;
		this.customObservationConvention = customObservationConvention;
	}

	@Override
	public AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> advisorRequestContext) {

		var observationContext = this.doCreateObservationContextBuilder(AdvisorObservationContext.Type.BEFORE)
			.withAdvisedRequest(request)
			.withAdvisorRequestContext(advisorRequestContext)
			.build();

		return AdvisorObservationDocumentation.AI_ADVISOR
			.observation(this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> this.targetAdvisor.adviseRequest(request, advisorRequestContext));
	}

	@Override
	public ChatResponse adviseResponse(ChatResponse response, Map<String, Object> advisorResponseContext) {

		var observationContext = this.doCreateObservationContextBuilder(AdvisorObservationContext.Type.AFTER)
			.withAdvisorRequestContext(advisorResponseContext)
			.build();

		return AdvisorObservationDocumentation.AI_ADVISOR
			.observation(this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> this.targetAdvisor.adviseResponse(response, advisorResponseContext));
	}

	@Override
	public Flux<ChatResponse> adviseResponse(Flux<ChatResponse> fluxResponse, Map<String, Object> context) {

		return Flux.deferContextual(contextView -> {
			var observationContext = this.doCreateObservationContextBuilder(AdvisorObservationContext.Type.AFTER)
				.withAdvisorResponseContext(context)
				.build();

			Observation observation = AdvisorObservationDocumentation.AI_ADVISOR.observation(
					this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			// @formatter:off
			return this.targetAdvisor.adviseResponse(fluxResponse, context)
			.doOnError(observation::error)
			.doFinally(s -> observation.stop())
			.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
			// @formatter:on
		});
	}

	/**
	 * Create the AdvisorObservationContext.Builder for the given advisorType. Can be
	 * overridden by the concrete advisor to provide additional context information.
	 * @param advisorType the advisor type.
	 * @return the AdvisorObservationContext.Builder instance.
	 */
	public AdvisorObservationContext.Builder doCreateObservationContextBuilder(
			AdvisorObservationContext.Type advisorType) {

		return AdvisorObservationContext.builder()
			.withAdvisorName(this.targetAdvisor.getName())
			.withAdvisorType(advisorType);
	}

}
