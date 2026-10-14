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

package org.springframework.ai.model.session.autoconfigure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The properties of the auto-configured
 * {@link org.springframework.ai.session.SessionService}, under {@code spring.ai.session}.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@ConfigurationProperties(SessionServiceProperties.CONFIG_PREFIX)
public class SessionServiceProperties {

	public static final String CONFIG_PREFIX = "spring.ai.session";

	/** The time to live of a session created without one. */
	private Duration timeToLive = Duration.ofDays(60);

	/**
	 * Whether a system message may be stored in a session; disabled by default, as a
	 * system prompt is configuration, best supplied on every request.
	 */
	private boolean allowSystemMessages = false;

	public Duration getTimeToLive() {
		return this.timeToLive;
	}

	public void setTimeToLive(Duration timeToLive) {
		this.timeToLive = timeToLive;
	}

	public boolean isAllowSystemMessages() {
		return this.allowSystemMessages;
	}

	public void setAllowSystemMessages(boolean allowSystemMessages) {
		this.allowSystemMessages = allowSystemMessages;
	}

}
