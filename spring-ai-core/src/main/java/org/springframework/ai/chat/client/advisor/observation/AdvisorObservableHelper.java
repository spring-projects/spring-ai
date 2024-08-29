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
import org.springframework.ai.chat.client.RequestResponseAdvisor.StreamResponseMode;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.util.StringUtils;

import io.micrometer.observation.Observation;
import reactor.core.publisher.Flux;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public abstract class AdvisorObservableHelper {

	private static final AdvisorObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultAdvisorObservationConvention();

	public static AdvisedRequest adviseRequest(Observation parentObservation, RequestResponseAdvisor advisor,
			AdvisedRequest advisedRequest, Map<String, Object> advisorContext) {

		var observationContext = AdvisorObservationContext.builder()
			.withAdvisorName(advisor.getName())
			.withAdvisorType(AdvisorObservationContext.Type.BEFORE)
			.withAdvisedRequest(advisedRequest)
			.withAdvisorRequestContext(advisorContext)
			.build();

		return AdvisorObservationDocumentation.AI_ADVISOR
			.observation(null, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					parentObservation.getObservationRegistry())
			.parentObservation(parentObservation)
			.observe(() -> advisor.adviseRequest(advisedRequest, advisorContext));
	}

	public static ChatResponse adviseResponse(Observation parentObservation, RequestResponseAdvisor advisor,
			ChatResponse response, Map<String, Object> advisorContext) {

		var observationContext = AdvisorObservationContext.builder()
			.withAdvisorName(advisor.getName())
			.withAdvisorType(AdvisorObservationContext.Type.AFTER)
			.withAdvisorRequestContext(advisorContext)
			.build();

		return AdvisorObservationDocumentation.AI_ADVISOR
			.observation(null, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					parentObservation.getObservationRegistry())
			.parentObservation(parentObservation)
			.observe(() -> advisor.adviseResponse(response, advisorContext));
	}

	public static Flux<ChatResponse> adviseResponse(Observation parentObservation, RequestResponseAdvisor advisor,
			Flux<ChatResponse> fluxResponse, Map<String, Object> advisorContext) {

		if (advisor.getStreamResponseMode() == StreamResponseMode.PER_CHUNK) {
			return fluxResponse
				.map(chatResponse -> adviseResponse(parentObservation, advisor, chatResponse, advisorContext));
		}
		else if (advisor.getStreamResponseMode() == StreamResponseMode.AGGREGATE) {
			return new MessageAggregator().aggregate(fluxResponse, chatResponse -> {
				adviseResponse(parentObservation, advisor, chatResponse, advisorContext);
			});
		}
		else if (advisor.getStreamResponseMode() == StreamResponseMode.ON_FINISH_REASON) {
			return fluxResponse.map(chatResponse -> {
				boolean withFinishReason = chatResponse.getResults()
					.stream()
					.filter(result -> result != null && result.getMetadata() != null
							&& StringUtils.hasText(result.getMetadata().getFinishReason()))
					.findFirst()
					.isPresent();

				if (withFinishReason) {
					return adviseResponse(parentObservation, advisor, chatResponse, advisorContext);
				}
				return chatResponse;
			});
		}

		return advisor.adviseResponse(fluxResponse, advisorContext);
	}

}
