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

package org.springframework.ai.bedrock.converse;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configures the AWS SDK {@code sdk.ua.appId} system property so that all SDK clients
 * automatically include the spring-ai identifier in the User-Agent header. Preserves any
 * user-provided value by appending rather than replacing.
 *
 * @author Matt Meckes
 * @since 2.0.0
 */
final class UserAgentProvider {

	private static final Logger logger = LoggerFactory.getLogger(UserAgentProvider.class);

	private static final String SDK_USER_AGENT_APP_ID = "sdk.ua.appId";

	private static final String APP_ID;

	static {
		@Nullable String version = UserAgentProvider.class.getPackage().getImplementationVersion();
		if (version == null) {
			version = "unknown";
		}
		APP_ID = "spring-ai/" + version;
	}

	private UserAgentProvider() {
	}

	static void configure() {
		try {
			String existing = System.getProperty(SDK_USER_AGENT_APP_ID);
			if (existing != null && existing.contains(APP_ID)) {
				return;
			}
			String newValue = (existing == null || existing.isEmpty()) ? APP_ID : existing + "/" + APP_ID;
			System.setProperty(SDK_USER_AGENT_APP_ID, newValue);
			logger.debug("Set {} = {}", SDK_USER_AGENT_APP_ID, newValue);
		}
		catch (Exception ex) {
			logger.warn("Unable to configure user agent system property", ex);
		}
	}

	static String appId() {
		return APP_ID;
	}

}
