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

package org.springframework.ai.model.systemone.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.systemone.SystemOneDecisionModel;
import org.springframework.ai.systemone.SystemOneDecisionOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Properties of the System One decision model: the model and the provider name recorded
 * in observations.
 *
 * <p>
 * The model has no default here. Left unset, the decision model asks for the default
 * model of the client it is given, so that a client the application defines keeps the
 * model it was built with.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@ConfigurationProperties(SystemOneDecisionProperties.CONFIG_PREFIX)
public class SystemOneDecisionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.systemone.decision";

	/**
	 * The model name or alias. Unset, the default model of the client applies: jev-latest
	 * for the auto-configured client.
	 */
	private @Nullable String model;

	/**
	 * The provider name recorded in observations, such as {@code laya} or {@code llmman}
	 * when the base URL points at one of them.
	 */
	private String provider = SystemOneDecisionModel.DEFAULT_PROVIDER;

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public String getProvider() {
		return this.provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	/**
	 * The options of the decision model. They name a model only when one is configured,
	 * which leaves the decision model to fall back on the default model of its client.
	 * @return the options
	 */
	public SystemOneDecisionOptions toOptions() {
		return SystemOneDecisionOptions.builder().model(StringUtils.hasText(this.model) ? this.model : null).build();
	}

}
