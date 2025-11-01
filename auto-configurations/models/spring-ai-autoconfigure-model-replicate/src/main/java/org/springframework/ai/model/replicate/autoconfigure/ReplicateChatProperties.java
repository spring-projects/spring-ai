/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.replicate.autoconfigure;

import org.springframework.ai.replicate.ReplicateChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Chat properties for Replicate AI.
 *
 * @author Rene Maierhofer
 * @since 1.1.0
 */
@ConfigurationProperties(ReplicateChatProperties.CONFIG_PREFIX)
public class ReplicateChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.replicate.chat";

	@NestedConfigurationProperty
	private ReplicateChatOptions options = ReplicateChatOptions.builder().build();

	public ReplicateChatOptions getOptions() {
		return this.options;
	}

	public void setOptions(ReplicateChatOptions options) {
		this.options = options;
	}

}
