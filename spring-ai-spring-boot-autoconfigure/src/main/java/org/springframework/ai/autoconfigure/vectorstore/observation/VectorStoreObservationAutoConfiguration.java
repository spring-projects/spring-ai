/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.ai.autoconfigure.vectorstore.observation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreQueryResponseObservationFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Spring AI vector store observations.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
@AutoConfiguration(
		afterName = { "org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration" })
@ConditionalOnClass(VectorStore.class)
@EnableConfigurationProperties({ VectorStoreObservationProperties.class })
public class VectorStoreObservationAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(VectorStoreObservationAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = VectorStoreObservationProperties.CONFIG_PREFIX, name = "include-query-response",
			havingValue = "true")
	VectorStoreQueryResponseObservationFilter vectorStoreQueryResponseContentObservationFilter() {
		logger.warn(
				"You have enabled the inclusion of the query response content in the observations, with the risk of exposing sensitive or private information. Please, be careful!");
		return new VectorStoreQueryResponseObservationFilter();
	}

}
