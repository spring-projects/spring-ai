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

package org.springframework.ai.mcp.modern;

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
class McpTransportContextsTests {

	@Test
	void defaultExtractorShapeIsLowerCasedWithoutProtocolHeaders() {
		McpTransportContext context = McpTransportContexts.fromHeaders(each -> {
			each.accept("X-Role", List.of("admin"));
			each.accept("x-role", List.of("auditor"));
			each.accept("Mcp-Method", List.of("tools/list"));
			each.accept("Authorization", List.of("Bearer t"));
		});

		assertThat(McpTransportContexts.headers(context)).containsOnlyKeys("x-role", "authorization");
		assertThat(McpTransportContexts.headers(context).get("x-role")).containsExactly("admin", "auditor");
		assertThat(McpTransportContexts.header(context, "X-ROLE")).isEqualTo("admin");
		assertThat(McpTransportContexts.header(context, "mcp-method")).isNull();
		assertThat(McpTransportContexts.header(context, "missing")).isNull();
	}

	@Test
	void customExtractorShapesAreRead() {
		McpTransportContext mixed = McpTransportContext.create(Map.of(McpTransportContexts.HEADERS,
				Map.of("X-Tenant", "acme", "x-roles", List.of("a", "b"), "ignored", 42)));

		assertThat(McpTransportContexts.header(mixed, "x-tenant")).isEqualTo("acme");
		assertThat(McpTransportContexts.header(mixed, "X-Roles")).isEqualTo("a");
		assertThat(McpTransportContexts.header(mixed, "ignored")).isNull();
		assertThat(McpTransportContexts.headers(mixed)).containsOnlyKeys("x-tenant", "x-roles");
	}

	@Test
	void contextWithoutHeadersIsEmpty() {
		assertThat(McpTransportContexts.headers(McpTransportContext.EMPTY)).isEmpty();
		assertThat(McpTransportContexts.header(McpTransportContext.EMPTY, "x-role")).isNull();
	}

}
