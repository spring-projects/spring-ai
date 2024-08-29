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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.RequestAdvisor;
import org.springframework.ai.chat.client.advisor.api.ResponseAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.CollectionUtils;

import io.micrometer.observation.Observation;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public abstract class AdvisorObservableHelper {

	public static final AdvisorObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultAdvisorObservationConvention();

	public static AdvisedRequest adviseRequest(Observation parentObservation, RequestAdvisor advisor,
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

	public static ChatResponse adviseResponse(Observation parentObservation, ResponseAdvisor advisor,
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

	public static List<RequestAdvisor> extractRequestAdvisors(List<Advisor> advisors) {
		return advisors.stream()
			.filter(advisor -> advisor instanceof RequestAdvisor)
			.map(a -> (RequestAdvisor) a)
			.toList();
	}

	/**
	 * Extracts the {@link ResponseAdvisor} instances from the given list of advisors and
	 * returns them in reverse order.
	 * @param advisors list of all registered advisor types.
	 * @return the list of {@link ResponseAdvisor} instances in reverse order.
	 */
	public static List<ResponseAdvisor> extractResponseAdvisors(List<Advisor> advisors) {

		var list = advisors.stream()
			.filter(advisor -> advisor instanceof ResponseAdvisor)
			.map(a -> (ResponseAdvisor) a)
			.toList();

		// reverse the list
		if (CollectionUtils.isEmpty(list)) {
			return list;
		}

		var reversedList = new ArrayList<>(list);
		Collections.reverse(reversedList);
		return Collections.unmodifiableList(reversedList);
	}

}
