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

import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultMcpToolNamePrefixGenerator}.
 */
class DefaultMcpToolNamePrefixGeneratorTests {

	@Test
	void prefixedToolNameShouldKeepResolvedNamesStableAcrossCalls() {
		DefaultMcpToolNamePrefixGenerator generator = new DefaultMcpToolNamePrefixGenerator();
		McpConnectionInfo serverA = connection("server-a");
		McpConnectionInfo serverB = connection("server-b");

		String firstA = generator.prefixedToolName(serverA, tool("search"));
		String firstB = generator.prefixedToolName(serverB, tool("search"));

		assertThat(firstA).isEqualTo("search");
		assertThat(firstB).isEqualTo("alt_1_search");

		// A tools/list refresh yields equal tool definitions again
		String secondA = generator.prefixedToolName(serverA, tool("search"));
		String secondB = generator.prefixedToolName(serverB, tool("search"));

		assertThat(secondA).isEqualTo(firstA);
		assertThat(secondB).isEqualTo(firstB);
	}

	private static McpConnectionInfo connection(String clientName) {
		return McpConnectionInfo.builder()
			.clientCapabilities(new ClientCapabilities(null, null, null, null))
			.clientInfo(Implementation.builder(clientName, "1.0.0").build())
			.build();
	}

	private static Tool tool(String name) {
		return Tool.builder(name, Map.of("type", "object")).description("search the corpus").build();
	}

}
