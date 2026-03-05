/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.model.embedding.observation.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.observation.EmbeddingModelMeterObservationHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Spring AI embedding model observations.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
@AutoConfiguration(
		afterName = { "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration",
				"org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration" })
@ConditionalOnClass(EmbeddingModel.class)
public class EmbeddingObservationAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(MeterRegistry.class)
	EmbeddingModelMeterObservationHandler embeddingModelMeterObservationHandler(
			ObjectProvider<MeterRegistry> meterRegistry) {
		return new EmbeddingModelMeterObservationHandler(meterRegistry.getObject());
	}

}
