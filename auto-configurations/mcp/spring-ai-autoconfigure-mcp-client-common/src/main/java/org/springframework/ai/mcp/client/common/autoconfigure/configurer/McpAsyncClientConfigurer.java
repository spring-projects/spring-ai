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

import org.springframework.ai.mcp.customizer.McpAsyncClientCustomizer;
import org.springframework.util.Assert;

public class McpAsyncClientConfigurer {

	private List<McpAsyncClientCustomizer> customizers;

	public McpAsyncClientConfigurer(List<McpAsyncClientCustomizer> customizers) {
		Assert.notNull(customizers, "customizers must not be null");
		this.customizers = customizers;
	}

	public McpClient.AsyncSpec configure(String name, McpClient.AsyncSpec spec) {
		applyCustomizers(name, spec);
		return spec;
	}

	private void applyCustomizers(String name, McpClient.AsyncSpec spec) {
		if (this.customizers != null) {
			for (McpAsyncClientCustomizer customizer : this.customizers) {
				customizer.customize(name, spec);
			}
		}
	}

}
