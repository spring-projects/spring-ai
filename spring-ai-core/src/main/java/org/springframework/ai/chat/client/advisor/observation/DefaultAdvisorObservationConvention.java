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

import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.lang.Nullable;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */

public class DefaultAdvisorObservationConvention implements AdvisorObservationConvention {

	private static final String DEFAULT_NAME = "spring.ai.chat.client.advisor";

    private static final String CHAT_CLIENT_ADVISOR_SPRING_AI_KIND = "chat_client_advisor";

	private static final KeyValue ADVISOR_TYPE_NONE = KeyValue.of(LowCardinalityKeyNames.ADVISOR_TYPE, KeyValue.NONE_VALUE);

	private final String name;

	public DefaultAdvisorObservationConvention() {
		this(DEFAULT_NAME);
	}

	public DefaultAdvisorObservationConvention(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	@Nullable
	public String getContextualName(AdvisorObservationContext context) {
		return "%s %s".formatted(CHAT_CLIENT_ADVISOR_SPRING_AI_KIND, context.getModelClassName());
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(AdvisorObservationContext context) {
		return KeyValues.of(springAiKind(), advisorType(context));
	}

	protected KeyValue advisorType(AdvisorObservationContext context) {
		if (context.getType() != null) {
			return KeyValue.of(LowCardinalityKeyNames.ADVISOR_TYPE, context.getType().name());
		}
		return ADVISOR_TYPE_NONE;
	}

    protected KeyValue springAiKind() {
		return KeyValue.of(AdvisorObservationDocumentation.LowCardinalityKeyNames.SPRING_AI_KIND,
				CHAT_CLIENT_ADVISOR_SPRING_AI_KIND);
	}

}