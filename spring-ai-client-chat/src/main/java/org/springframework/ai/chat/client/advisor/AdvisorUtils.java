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

import java.util.List;
import java.util.function.Predicate;

import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Utilities to work with advisors.
 *
 * @author Christian Tzolov
 */
public final class AdvisorUtils {

	private AdvisorUtils() {
	}

	/**
	 * Checks whether the provided {@link ChatClientResponse} contains a
	 * {@link ChatResponse} with at least one result having a non-empty finish reason in
	 * its metadata.
	 */
	public static Predicate<ChatClientResponse> onFinishReason() {
		return chatClientResponse -> {
			ChatResponse chatResponse = chatClientResponse.chatResponse();
			return chatResponse != null && chatResponse.getResults() != null
					&& chatResponse.getResults()
						.stream()
						.anyMatch(result -> result != null && result.getMetadata() != null
								&& StringUtils.hasText(result.getMetadata().getFinishReason()));
		};
	}

	/**
	 * Creates a new CallAdvisorChain copy that contains all advisors after the specified
	 * advisor.
	 * @param callAdvisorChain the original CallAdvisorChain
	 * @param after the CallAdvisor after which to copy the chain
	 * @return a new CallAdvisorChain containing all advisors after the specified advisor
	 * @throws IllegalArgumentException if the specified advisor is not part of the chain
	 */
	public static CallAdvisorChain copyChainAfterAdvisor(CallAdvisorChain callAdvisorChain, CallAdvisor after) {

		Assert.notNull(callAdvisorChain, "callAdvisorChain must not be null");
		Assert.notNull(after, "The after call advisor must not be null");

		List<CallAdvisor> callAdvisors = callAdvisorChain.getCallAdvisors();
		int afterAdvisorIndex = callAdvisors.indexOf(after);

		if (afterAdvisorIndex < 0) {
			throw new IllegalArgumentException("The specified advisor is not part of the chain: " + after.getName());
		}

		var remainingCallAdvisors = callAdvisors.subList(afterAdvisorIndex + 1, callAdvisors.size());

		return DefaultAroundAdvisorChain.builder(callAdvisorChain.getObservationRegistry())
			.pushAll(remainingCallAdvisors)
			.build();
	}

}
