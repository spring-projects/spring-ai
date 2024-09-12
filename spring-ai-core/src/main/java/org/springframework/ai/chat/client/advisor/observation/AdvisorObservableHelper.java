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

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.BeforeAdvisor;
import org.springframework.ai.chat.client.advisor.api.AfterAdvisor;
import org.springframework.util.CollectionUtils;

import io.micrometer.observation.Observation;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public abstract class AdvisorObservableHelper {

	public static final AdvisorObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultAdvisorObservationConvention();

	public static AdvisedRequest adviseRequest(Observation parentObservation, BeforeAdvisor advisor,
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
			.observe(() -> advisor.before(advisedRequest));
	}

	public static AdvisedResponse adviseResponse(Observation parentObservation, AfterAdvisor advisor,
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
			.observe(() -> advisor.afterCall(advisedResponse));
	}

	public static List<BeforeAdvisor> requestAdvisors(List<Advisor> advisors) {
		if (CollectionUtils.isEmpty(advisors)) {
			return Collections.emptyList();
		}
		return advisors.stream()
			.filter(advisor -> advisor instanceof BeforeAdvisor)
			.map(a -> (BeforeAdvisor) a)
			.toList();
	}

	/**
	 * Extracts the {@link AfterAdvisor} instances from the given list of advisors and
	 * returns them in reverse order.
	 * @param advisors list of all registered advisor types.
	 * @return the list of {@link AfterAdvisor} instances in reverse order.
	 */
	public static List<AfterAdvisor> responseAdvisors(List<Advisor> advisors) {
		if (CollectionUtils.isEmpty(advisors)) {
			return Collections.emptyList();
		}
		var list = advisors.stream()
			.filter(advisor -> advisor instanceof AfterAdvisor)
			.map(a -> (AfterAdvisor) a)
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
