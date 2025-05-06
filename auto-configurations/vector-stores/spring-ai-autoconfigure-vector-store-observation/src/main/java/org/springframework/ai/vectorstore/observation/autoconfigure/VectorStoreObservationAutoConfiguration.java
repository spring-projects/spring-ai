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

package org.springframework.ai.vectorstore.observation.autoconfigure;

import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.observation.TracingAwareLoggingObservationHandler;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreQueryResponseObservationHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Spring AI vector store observations.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 * @since 1.0.0
 */
@AutoConfiguration(
		afterName = { "org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration" })
@ConditionalOnClass(VectorStore.class)
@EnableConfigurationProperties({ VectorStoreObservationProperties.class })
public class VectorStoreObservationAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(VectorStoreObservationAutoConfiguration.class);

	private static void logQueryResponseContentWarning() {
		logger.warn(
				"You have enabled logging out of the query response content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(Tracer.class)
	@ConditionalOnBean(Tracer.class)
	static class TracerPresentObservationConfiguration {

		@Bean
		@ConditionalOnMissingBean(value = VectorStoreQueryResponseObservationHandler.class,
				name = "vectorStoreQueryResponseObservationHandler")
		@ConditionalOnProperty(prefix = VectorStoreObservationProperties.CONFIG_PREFIX, name = "log-query-response",
				havingValue = "true")
		TracingAwareLoggingObservationHandler<VectorStoreObservationContext> vectorStoreQueryResponseObservationHandler(
				Tracer tracer) {
			logQueryResponseContentWarning();
			return new TracingAwareLoggingObservationHandler<>(new VectorStoreQueryResponseObservationHandler(),
					tracer);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingClass("io.micrometer.tracing.Tracer")
	static class TracerNotPresentObservationConfiguration {

		@Bean
		@ConditionalOnMissingBean
		@ConditionalOnProperty(prefix = VectorStoreObservationProperties.CONFIG_PREFIX, name = "log-query-response",
				havingValue = "true")
		VectorStoreQueryResponseObservationHandler vectorStoreQueryResponseObservationHandler() {
			logQueryResponseContentWarning();
			return new VectorStoreQueryResponseObservationHandler();
		}

	}

}
