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

package org.springframework.ai.mcp.server.common.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpAsyncListFilter;
import io.modelcontextprotocol.server.McpSyncListFilter;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.McpServerToolFilter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link McpServerToolFilterAdapter}.
 *
 * @author ZhangDT-sky
 */
class McpServerToolFilterAdapterTests {

	private static final McpSchema.Tool TOOL = McpSchema.Tool.builder("myTool", Map.of()).build();

	private static final McpTransportContext CONTEXT = McpTransportContext.create(Map.of("tenant", "acme"));

	@Test
	void advertisesEveryToolWhenNoFilterIsRegistered() {
		McpSyncListFilter<McpSchema.Tool> filter = McpServerToolFilterAdapter.toSyncFilter(List.of());

		assertThat(filter.isVisible(CONTEXT, TOOL)).isTrue();
	}

	@Test
	void hidesToolWhenSingleFilterRejectsIt() {
		McpSyncListFilter<McpSchema.Tool> filter = McpServerToolFilterAdapter
			.toSyncFilter(List.of((transportContext, tool) -> false));

		assertThat(filter.isVisible(CONTEXT, TOOL)).isFalse();
	}

	@Test
	void combinesFiltersWithAndSemantics() {
		McpSyncListFilter<McpSchema.Tool> filter = McpServerToolFilterAdapter.toSyncFilter(List
			.of((transportContext, tool) -> true, (transportContext, tool) -> false, (transportContext, tool) -> true));

		assertThat(filter.isVisible(CONTEXT, TOOL)).isFalse();
	}

	@Test
	void consultsFiltersInOrderAndStopsAtTheFirstRejection() {
		List<String> consulted = new ArrayList<>();
		McpSyncListFilter<McpSchema.Tool> filter = McpServerToolFilterAdapter
			.toSyncFilter(List.of(recording(consulted, "first", false), recording(consulted, "second", true)));

		assertThat(filter.isVisible(CONTEXT, TOOL)).isFalse();
		assertThat(consulted).containsExactly("first");
	}

	@Test
	void passesTheTransportContextAndToolToEveryFilter() {
		AtomicReference<McpTransportContext> seenContext = new AtomicReference<>();
		AtomicReference<McpSchema.Tool> seenTool = new AtomicReference<>();
		McpSyncListFilter<McpSchema.Tool> filter = McpServerToolFilterAdapter
			.toSyncFilter(List.of((transportContext, tool) -> {
				seenContext.set(transportContext);
				seenTool.set(tool);
				return true;
			}));

		assertThat(filter.isVisible(CONTEXT, TOOL)).isTrue();
		assertThat(seenContext.get()).isSameAs(CONTEXT);
		assertThat(seenTool.get()).isSameAs(TOOL);
		assertThat(seenContext.get().get("tenant")).isEqualTo("acme");
	}

	@Test
	void asyncFilterMirrorsTheSyncDecision() {
		McpAsyncListFilter<McpSchema.Tool> hiding = McpServerToolFilterAdapter
			.toAsyncFilter(List.of((transportContext, tool) -> false));
		McpAsyncListFilter<McpSchema.Tool> advertising = McpServerToolFilterAdapter
			.toAsyncFilter(List.of((transportContext, tool) -> true));

		assertThat(hiding.isVisible(CONTEXT, TOOL).block()).isFalse();
		assertThat(advertising.isVisible(CONTEXT, TOOL).block()).isTrue();
	}

	private static McpServerToolFilter recording(List<String> consulted, String name, boolean result) {
		return new McpServerToolFilter() {

			@Override
			public boolean isVisible(McpTransportContext transportContext, McpSchema.Tool tool) {
				consulted.add(name);
				return result;
			}

		};
	}

}
