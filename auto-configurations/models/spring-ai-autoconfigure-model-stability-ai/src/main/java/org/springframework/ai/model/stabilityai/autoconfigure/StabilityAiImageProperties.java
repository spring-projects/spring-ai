/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.model.stabilityai.autoconfigure;

import org.springframework.ai.stabilityai.api.StabilityAiImageOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Stability AI image model.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @since 0.8.0
 */
@ConfigurationProperties(StabilityAiImageProperties.CONFIG_PREFIX)
public class StabilityAiImageProperties extends StabilityAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.stabilityai.image";

	@NestedConfigurationProperty
	private final StabilityAiImageOptions options = StabilityAiImageOptions.builder().build(); // stable-diffusion-xl-1024-v1-0

	public StabilityAiImageOptions getOptions() {
		return this.options;
	}

}
