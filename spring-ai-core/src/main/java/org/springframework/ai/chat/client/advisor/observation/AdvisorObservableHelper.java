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

import org.springframework.ai.chat.client.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.RequestAdvisor;
import org.springframework.ai.chat.client.advisor.api.ResponseAdvisor;
import org.springframework.util.CollectionUtils;

import io.micrometer.observation.Observation;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public abstract class AdvisorObservableHelper {

	public static final AdvisorObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultAdvisorObservationConvention();

	public static AdvisedRequest adviseRequest(Observation parentObservation, RequestAdvisor advisor,
			AdvisedRequest advisedRequest) {

		var observationContext = AdvisorObservationContext.builder()
			.withAdvisorName(advisor.getName())
			.withAdvisorType(AdvisorObservationContext.Type.BEFORE)
			.withAdvisedRequest(advisedRequest)
			.withAdvisorRequestContext(advisedRequest.adviseContext())
			.build();

		return AdvisorObservationDocumentation.AI_ADVISOR
			.observation(null, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					parentObservation.getObservationRegistry())
			.parentObservation(parentObservation)
			.observe(() -> advisor.adviseRequest(advisedRequest));
	}

	public static AdvisedResponse adviseResponse(Observation parentObservation, ResponseAdvisor advisor,
			AdvisedResponse advisedResponse) {

		var observationContext = AdvisorObservationContext.builder()
			.withAdvisorName(advisor.getName())
			.withAdvisorType(AdvisorObservationContext.Type.AFTER)
			.withAdvisorRequestContext(advisedResponse.adviseContext())
			.build();

		return AdvisorObservationDocumentation.AI_ADVISOR
			.observation(null, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					parentObservation.getObservationRegistry())
			.parentObservation(parentObservation)
			.observe(() -> advisor.adviseResponse(advisedResponse));
	}

	public static List<RequestAdvisor> requestAdvisors(List<Advisor> advisors) {
		if (CollectionUtils.isEmpty(advisors)) {
			return Collections.emptyList();
		}
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
	public static List<ResponseAdvisor> responseAdvisors(List<Advisor> advisors) {
		if (CollectionUtils.isEmpty(advisors)) {
			return Collections.emptyList();
		}
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
