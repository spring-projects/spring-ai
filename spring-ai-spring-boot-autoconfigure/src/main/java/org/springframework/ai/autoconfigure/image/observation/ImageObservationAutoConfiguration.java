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
package org.springframework.ai.autoconfigure.image.observation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.observation.ImageModelPromptContentObservationFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Spring AI image model observations.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
@AutoConfiguration(
		afterName = "org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration.class")
@ConditionalOnClass(ImageModel.class)
@EnableConfigurationProperties({ ImageObservationProperties.class })
public class ImageObservationAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(ImageObservationAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = ImageObservationProperties.CONFIG_PREFIX, name = "include-prompt",
			havingValue = "true")
	ImageModelPromptContentObservationFilter imageModelPromptObservationFilter() {
		logger.warn(
				"You have enabled the inclusion of the image prompt content in the observations, with the risk of exposing sensitive or private information. Please, be careful!");
		return new ImageModelPromptContentObservationFilter();
	}

}
