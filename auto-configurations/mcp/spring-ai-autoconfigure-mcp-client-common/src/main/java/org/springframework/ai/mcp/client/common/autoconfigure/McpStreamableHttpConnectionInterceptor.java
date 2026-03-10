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

package org.springframework.ai.mcp.client.common.autoconfigure;

import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties.ConnectionParameters;

/**
 * Interceptor for modifying Streamable HTTP connection parameters before transport
 * creation.
 *
 * <p>
 * This functional interface allows users to intercept and modify Streamable HTTP
 * connection parameters (such as URL and endpoint) before the transport is created. This
 * is particularly useful for service discovery scenarios where service names need to be
 * resolved to actual host:port addresses at runtime.
 *
 * <p>
 * Multiple interceptors can be registered as Spring beans and will be applied in order
 * determined by {@link org.springframework.core.annotation.Order @Order} annotation.
 *
 * @author Haotian Zhang
 * @see ConnectionParameters
 * @since 1.0.0
 */
@FunctionalInterface
public interface McpStreamableHttpConnectionInterceptor {

	/**
	 * Intercept and optionally modify the Streamable HTTP connection parameters.
	 *
	 * @param connectionName the name of the connection being created
	 * @param originalParams the original connection parameters from configuration
	 * @return the (potentially modified) connection parameters to use for transport
	 * creation
	 */
	ConnectionParameters intercept(String connectionName, ConnectionParameters originalParams);

}
