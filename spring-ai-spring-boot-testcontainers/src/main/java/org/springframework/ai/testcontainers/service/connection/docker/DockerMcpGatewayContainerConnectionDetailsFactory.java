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

package org.springframework.ai.testcontainers.service.connection.docker;

import java.util.Map;

import org.testcontainers.containers.DockerMcpGatewayContainer;

import org.springframework.ai.mcp.client.common.autoconfigure.McpSseClientConnectionDetails;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpSseClientProperties;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource;

/**
 * @author Eddú Meléndez
 */
class DockerMcpGatewayContainerConnectionDetailsFactory
		extends ContainerConnectionDetailsFactory<DockerMcpGatewayContainer, McpSseClientConnectionDetails> {

	@Override
	public McpSseClientConnectionDetails getContainerConnectionDetails(
			ContainerConnectionSource<DockerMcpGatewayContainer> source) {
		return new DockerMcpGatewayContainerConnectionDetails(source);
	}

	/**
	 * {@link McpSseClientConnectionDetails} backed by a
	 * {@link ContainerConnectionSource}.
	 */
	private static final class DockerMcpGatewayContainerConnectionDetails
			extends ContainerConnectionDetails<DockerMcpGatewayContainer> implements McpSseClientConnectionDetails {

		private DockerMcpGatewayContainerConnectionDetails(
				ContainerConnectionSource<DockerMcpGatewayContainer> source) {
			super(source);
		}

		@Override
		public Map<String, McpSseClientProperties.SseParameters> getConnections() {
			return Map.of("gateway", new McpSseClientProperties.SseParameters(getContainer().getEndpoint(), "/sse"));
		}

	}

}
