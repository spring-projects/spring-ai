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

package org.springframework.ai.mcp.client.common.autoconfigure;

import org.springframework.web.reactive.function.client.WebClient;

/**
 * Factory interface for creating {@link WebClient.Builder} instances per connection name.
 *
 * <p>
 * This factory allows customization of WebClient configuration on a per-connection basis,
 * enabling fine-grained control over HTTP client settings such as timeouts, SSL
 * configurations, and base URLs for each MCP server connection.
 *
 * <p>
 * The default implementation returns a standard {@link WebClient.Builder}. Custom
 * implementations can provide connection-specific configurations based on the connection
 * name.
 *
 * @author limch02
 * @since 1.0.0
 */
public interface WebClientFactory {

	/**
	 * Creates a {@link WebClient.Builder} for the given connection name.
	 * <p>
	 * The default implementation returns a standard {@link WebClient.Builder}. Custom
	 * implementations can override this method to provide connection-specific
	 * configurations.
	 * @param connectionName the name of the MCP server connection
	 * @return a WebClient.Builder instance configured for the connection
	 */
	default WebClient.Builder create(String connectionName) {
		return WebClient.builder();
	}

}
