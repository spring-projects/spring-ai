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

package org.springframework.ai.mcp.client.common.autoconfigure.configurer;

import java.util.List;

import io.modelcontextprotocol.client.McpClient;

import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.util.Assert;

/**
 * Configurer class for customizing MCP synchronous clients.
 *
 * <p>
 * This class manages a collection of {@link McpSyncClientCustomizer} instances that can
 * be applied to customize the configuration of MCP synchronous clients during their
 * creation.
 *
 * <p>
 * The configurer applies customizations in the order they are registered, allowing for
 * sequential modifications to the client specifications.
 *
 * @see McpSyncClientCustomizer
 * @see McpClient.SyncSpec
 */
public class McpSyncClientConfigurer {

	private List<McpSyncClientCustomizer> customizers;

	public McpSyncClientConfigurer(List<McpSyncClientCustomizer> customizers) {
		Assert.notNull(customizers, "customizers must not be null");
		this.customizers = customizers;
	}

	/**
	 * Configures an MCP sync client specification by applying all registered customizers.
	 * @param name the name of the client being configured
	 * @param spec the specification to customize
	 * @return the customized specification
	 */
	public McpClient.SyncSpec configure(String name, McpClient.SyncSpec spec) {
		applyCustomizers(name, spec);
		return spec;
	}

	/**
	 * Applies all registered customizers to the given specification.
	 *
	 * <p>
	 * Customizers are applied in the order they were registered. If no customizers are
	 * registered, this method has no effect.
	 * @param name the name of the client being customized
	 * @param spec the specification to customize
	 */
	private void applyCustomizers(String name, McpClient.SyncSpec spec) {
		if (this.customizers != null) {
			for (McpSyncClientCustomizer customizer : this.customizers) {
				customizer.customize(name, spec);
			}
		}
	}

}
