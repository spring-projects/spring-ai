/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.model.decision.observation.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.observation.DecisionModelMeterObservationHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Spring AI decision model observations.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
@AutoConfiguration(
		afterName = "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration")
@ConditionalOnClass(DecisionModel.class)
public class DecisionObservationAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(MeterRegistry.class)
	DecisionModelMeterObservationHandler decisionModelMeterObservationHandler(
			ObjectProvider<MeterRegistry> meterRegistry) {
		return new DecisionModelMeterObservationHandler(meterRegistry.getObject());
	}

}
