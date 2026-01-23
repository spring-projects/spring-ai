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

package org.springframework.ai.chat.client.advisor.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.observation.conventions.SpringAiKind;
import org.springframework.ai.util.ParsingUtils;
import org.springframework.util.Assert;

/**
 * Default implementation of the {@link AdvisorObservationConvention}.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class DefaultAdvisorObservationConvention implements AdvisorObservationConvention {

	public static final String DEFAULT_NAME = "spring.ai.advisor";

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
	public String getContextualName(AdvisorObservationContext context) {
		Assert.notNull(context, "context cannot be null");
		return ParsingUtils.reConcatenateCamelCase(context.getAdvisorName(), "_")
			.replace("_around_advisor", "")
			.replace("_advisor", "");
	}

	// ------------------------
	// Low cardinality keys
	// ------------------------

	@Override
	public KeyValues getLowCardinalityKeyValues(AdvisorObservationContext context) {
		Assert.notNull(context, "context cannot be null");
		return KeyValues.of(aiOperationType(context), aiProvider(context), springAiKind(), advisorName(context));
	}

	protected KeyValue aiOperationType(AdvisorObservationContext context) {
		return KeyValue.of(LowCardinalityKeyNames.AI_OPERATION_TYPE, AiOperationType.FRAMEWORK.value());
	}

	protected KeyValue aiProvider(AdvisorObservationContext context) {
		return KeyValue.of(LowCardinalityKeyNames.AI_PROVIDER, AiProvider.SPRING_AI.value());
	}

	protected KeyValue springAiKind() {
		return KeyValue.of(LowCardinalityKeyNames.SPRING_AI_KIND, SpringAiKind.ADVISOR.value());
	}

	protected KeyValue advisorName(AdvisorObservationContext context) {
		return KeyValue.of(LowCardinalityKeyNames.ADVISOR_NAME, context.getAdvisorName());
	}

	// ------------------------
	// High Cardinality keys
	// ------------------------

	@Override
	public KeyValues getHighCardinalityKeyValues(AdvisorObservationContext context) {
		Assert.notNull(context, "context cannot be null");
		return KeyValues.of(advisorOrder(context));
	}

	protected KeyValue advisorOrder(AdvisorObservationContext context) {
		return KeyValue.of(HighCardinalityKeyNames.ADVISOR_ORDER, "" + context.getOrder());
	}

}
