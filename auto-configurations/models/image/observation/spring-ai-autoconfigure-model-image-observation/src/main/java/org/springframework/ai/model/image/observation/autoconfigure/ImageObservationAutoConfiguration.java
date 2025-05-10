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

package org.springframework.ai.model.image.observation.autoconfigure;

import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.image.observation.ImageModelPromptContentObservationHandler;
import org.springframework.ai.observation.TracingAwareLoggingObservationHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Spring AI image model observations.
 *
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 * @since 1.0.0
 */
@AutoConfiguration(
		afterName = "org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration.class")
@ConditionalOnClass(ImageModel.class)
@EnableConfigurationProperties({ ImageObservationProperties.class })
public class ImageObservationAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(ImageObservationAutoConfiguration.class);

	private static void logPromptContentWarning() {
		logger.warn(
				"You have enabled logging out the image prompt content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(Tracer.class)
	@ConditionalOnBean(Tracer.class)
	static class TracerPresentObservationConfiguration {

		@Bean
		@ConditionalOnMissingBean(value = ImageModelPromptContentObservationHandler.class,
				name = "imageModelPromptContentObservationHandler")
		@ConditionalOnProperty(prefix = ImageObservationProperties.CONFIG_PREFIX, name = "log-prompt",
				havingValue = "true")
		TracingAwareLoggingObservationHandler<ImageModelObservationContext> imageModelPromptContentObservationHandler(
				Tracer tracer) {
			logPromptContentWarning();
			return new TracingAwareLoggingObservationHandler<>(new ImageModelPromptContentObservationHandler(), tracer);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingClass("io.micrometer.tracing.Tracer")
	static class TracerNotPresentObservationConfiguration {

		@Bean
		@ConditionalOnMissingBean
		@ConditionalOnProperty(prefix = ImageObservationProperties.CONFIG_PREFIX, name = "log-prompt",
				havingValue = "true")
		ImageModelPromptContentObservationHandler imageModelPromptContentObservationHandler() {
			logPromptContentWarning();
			return new ImageModelPromptContentObservationHandler();
		}

	}

}
