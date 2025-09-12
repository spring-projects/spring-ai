/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.docker.compose.service.connection.docker;

import java.util.Map;

import org.springframework.ai.mcp.client.common.autoconfigure.McpSseClientConnectionDetails;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpSseClientProperties;
import org.springframework.boot.docker.compose.core.RunningService;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionDetailsFactory;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource;

/**
 * A {@link DockerComposeConnectionDetailsFactory} implementation that creates
 * {@link McpSseClientConnectionDetails} for a Docker MCP Gateway instance running in a
 * Docker container.
 *
 * @author Eddú Meléndez
 */
class DockerMcpGatewayDockerComposeConnectionDetailsFactory
		extends DockerComposeConnectionDetailsFactory<McpSseClientConnectionDetails> {

	private static final int GATEWAY_PORT = 8811;

	protected DockerMcpGatewayDockerComposeConnectionDetailsFactory() {
		super("docker/mcp-gateway");
	}

	@Override
	protected McpSseClientConnectionDetails getDockerComposeConnectionDetails(DockerComposeConnectionSource source) {
		return new DockerAgentsGatewayContainerConnectionDetails(source.getRunningService());
	}

	/**
	 * {@link McpSseClientConnectionDetails} backed by a {@code Docker MCP Gateway}
	 * {@link RunningService}.
	 */
	static class DockerAgentsGatewayContainerConnectionDetails extends DockerComposeConnectionDetails
			implements McpSseClientConnectionDetails {

		private final String url;

		DockerAgentsGatewayContainerConnectionDetails(RunningService service) {
			super(service);
			this.url = String.format("http://%s:%d", service.host(), service.ports().get(GATEWAY_PORT));
		}

		@Override
		public Map<String, McpSseClientProperties.SseParameters> getConnections() {
			return Map.of("gateway", new McpSseClientProperties.SseParameters(this.url, "/sse"));
		}

	}

}
