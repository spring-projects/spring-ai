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

import java.net.URI;
import java.util.Objects;

import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties.ConnectionParameters;

/**
 * Resolves configured Streamable HTTP client connection parameters into transport
 * configuration values.
 *
 * @author Jewoo Shin
 */
public final class McpStreamableHttpClientConnectionResolver {

	private static final String DEFAULT_ENDPOINT = "/mcp";

	private McpStreamableHttpClientConnectionResolver() {
	}

	/**
	 * Resolve the configured connection parameters into the base URL and endpoint values
	 * consumed by Streamable HTTP transports.
	 * @param connectionName the configured connection name
	 * @param connectionParameters the configured connection parameters
	 * @return resolved transport connection values
	 */
	public static ResolvedConnection resolve(String connectionName, ConnectionParameters connectionParameters) {
		Objects.requireNonNull(connectionParameters, "Connection parameters must not be null");
		String url = Objects.requireNonNull(connectionParameters.url(),
				"Missing url for server named " + connectionName);
		String endpoint = connectionParameters.endpoint();
		if (endpoint != null) {
			return new ResolvedConnection(url, endpoint);
		}

		URI uri = URI.create(url);
		return new ResolvedConnection(baseUrl(uri, url), endpoint(uri));
	}

	private static String baseUrl(URI uri, String url) {
		if (uri.getScheme() == null || uri.getRawAuthority() == null) {
			return url;
		}
		return uri.getScheme() + "://" + uri.getRawAuthority();
	}

	private static String endpoint(URI uri) {
		String path = uri.getRawPath();
		if (path == null || path.isEmpty() || path.equals("/")) {
			path = DEFAULT_ENDPOINT;
		}
		String query = uri.getRawQuery();
		if (query == null) {
			return path;
		}
		return path + "?" + query;
	}

	/**
	 * Resolved base URL and endpoint for a Streamable HTTP client connection.
	 *
	 * @param baseUrl the base URL for the transport
	 * @param endpoint the endpoint path and optional query string for the transport
	 */
	public record ResolvedConnection(String baseUrl, String endpoint) {
	}

}
