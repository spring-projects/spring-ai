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

package org.springframework.ai.model.oci.genai.autoconfigure;

import org.springframework.ai.oci.cohere.OCICohereChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for OCI Cohere chat model.
 *
 * @author Anders Swanson
 */
@ConfigurationProperties(OCICohereChatModelProperties.CONFIG_PREFIX)
public class OCICohereChatModelProperties {

	public static final String CONFIG_PREFIX = "spring.ai.oci.genai.cohere.chat";

	private static final String DEFAULT_SERVING_MODE = ServingMode.ON_DEMAND.getMode();

	@NestedConfigurationProperty
	private final OCICohereChatOptions options = OCICohereChatOptions.builder()
		.servingMode(DEFAULT_SERVING_MODE)
		.build();

	public OCICohereChatOptions getOptions() {
		return this.options;
	}

}
