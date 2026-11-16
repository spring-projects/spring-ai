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

package org.springframework.ai.mcp.modern.client;

import java.util.Map;

import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link McpModernToolNamePrefixGenerator}.
 *
 * @author Christian Tzolov
 */
class McpModernToolNamePrefixGeneratorTests {

	private static final String LONG_TOOL_NAME = "search_the_tickets_of_all_projects_by_owner_and_state_and_label_and_date";

	private final McpModernToolNamePrefixGenerator generator = McpModernToolNamePrefixGenerator.connectionPrefix();

	@Test
	void toolIsNamedAfterItsConnection() {
		assertThat(this.generator.prefixedToolName(info("tickets"), tool("search"))).isEqualTo("tickets_search");
		// Characters that model providers reject are removed, hyphens become underscores.
		assertThat(this.generator.prefixedToolName(info("crm-eu"), tool("find.user"))).isEqualTo("crm_eu_finduser");
	}

	@Test
	void longNameKeepsTheConnectionAndStaysWithinTheLimit() {
		String name = this.generator.prefixedToolName(info("crm-eu"), tool(LONG_TOOL_NAME));

		assertThat(name).hasSize(McpModernToolNamePrefixGenerator.MAX_TOOL_NAME_LENGTH)
			.startsWith("crm_eu_search_the_tickets")
			.matches("[a-zA-Z0-9_]+");
		// The name depends on the connection and the tool only.
		assertThat(this.generator.prefixedToolName(info("crm-eu"), tool(LONG_TOOL_NAME))).isEqualTo(name);
	}

	@Test
	void longNamesOfDifferentConnectionsOrToolsDoNotBecomeTheSameName() {
		String ofEu = this.generator.prefixedToolName(info("crm-eu"), tool(LONG_TOOL_NAME));
		String ofUs = this.generator.prefixedToolName(info("crm-us"), tool(LONG_TOOL_NAME));
		// Two tools of one connection whose names only differ after the limit.
		String ofEuToo = this.generator.prefixedToolName(info("crm-eu"), tool(LONG_TOOL_NAME + "_sorted"));

		assertThat(ofEu).isNotEqualTo(ofUs).isNotEqualTo(ofEuToo);
		assertThat(ofUs).startsWith("crm_us_");
	}

	private static McpModernConnectionInfo info(String name) {
		return new McpModernConnectionInfo(name, Implementation.builder("test-client", "1").build());
	}

	private static Tool tool(String name) {
		return Tool.builder(name, Map.of("type", "object")).build();
	}

}
