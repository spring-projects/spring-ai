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

package org.springframework.ai.model.wavespeed.autoconfigure;

import java.time.Duration;

import org.springframework.ai.wavespeed.api.WaveSpeedApi;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection properties for the WaveSpeed AI API.
 *
 * @author Zeyi Cheng
 * @since 2.0.1
 */
@ConfigurationProperties(WaveSpeedConnectionProperties.CONFIG_PREFIX)
public class WaveSpeedConnectionProperties extends WaveSpeedParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.wavespeed";

	public static final String DEFAULT_BASE_URL = WaveSpeedApi.DEFAULT_BASE_URL;

	/**
	 * Interval between two prediction result polls.
	 */
	private Duration pollInterval = WaveSpeedApi.DEFAULT_POLL_INTERVAL;

	/**
	 * Maximum time to wait for a prediction to complete.
	 */
	private Duration timeout = WaveSpeedApi.DEFAULT_TIMEOUT;

	public WaveSpeedConnectionProperties() {
		super.setBaseUrl(DEFAULT_BASE_URL);
	}

	public Duration getPollInterval() {
		return this.pollInterval;
	}

	public void setPollInterval(Duration pollInterval) {
		this.pollInterval = pollInterval;
	}

	public Duration getTimeout() {
		return this.timeout;
	}

	public void setTimeout(Duration timeout) {
		this.timeout = timeout;
	}

}
