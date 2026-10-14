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

package org.springframework.ai.mcp;

import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultMcpToolNamePrefixGenerator}.
 *
 * @author WooSeung Kim
 */
class DefaultMcpToolNamePrefixGeneratorTests {

	private final DefaultMcpToolNamePrefixGenerator generator = new DefaultMcpToolNamePrefixGenerator();

	@Test
	void sameConnectionAndToolAlwaysGetsTheSameName() {
		McpConnectionInfo first = connection("first-client", "first-server");
		McpConnectionInfo second = connection("second-client", "second-server");

		assertThat(this.generator.prefixedToolName(first, tool("search", "v1"))).isEqualTo("search");
		assertThat(this.generator.prefixedToolName(second, tool("search", "v1"))).isEqualTo("alt_1_search");

		// Tool callbacks are re-discovered, for example after a tools/list_changed
		// notification
		assertThat(this.generator.prefixedToolName(first, tool("search", "v1"))).isEqualTo("search");
		assertThat(this.generator.prefixedToolName(second, tool("search", "v1"))).isEqualTo("alt_1_search");
	}

	@Test
	void duplicateToolsOfOneConnectionKeepTheirNames() {
		McpConnectionInfo connection = connection("client", "server");
		Tool first = tool("search", "first");
		Tool second = tool("search", "second");

		assertThat(this.generator.prefixedToolName(connection, first)).isEqualTo("search");
		assertThat(this.generator.prefixedToolName(connection, second)).isEqualTo("alt_1_search");

		assertThat(this.generator.prefixedToolName(connection, first)).isEqualTo("search");
		assertThat(this.generator.prefixedToolName(connection, second)).isEqualTo("alt_1_search");
	}

	private static McpConnectionInfo connection(String clientName, String serverName) {
		return McpConnectionInfo.builder()
			.clientCapabilities(new ClientCapabilities(null, null, null, null))
			.clientInfo(new Implementation(clientName, "1.0.0"))
			.initializeResult(new InitializeResult("2025-06-18", ServerCapabilities.builder().build(),
					new Implementation(serverName, "1.0.0"), null))
			.build();
	}

	private static Tool tool(String name, String description) {
		return Tool.builder()
			.name(name)
			.description(description)
			.inputSchema(new JsonSchema("object", null, null, null, null, null))
			.build();
	}

}
