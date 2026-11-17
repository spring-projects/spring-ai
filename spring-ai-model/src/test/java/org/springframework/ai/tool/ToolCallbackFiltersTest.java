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

package org.springframework.ai.tool;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ToolCallbackFilters}.
 *
 */
class ToolCallbackFiltersTest {

	private List<ToolCallback> allTools;

	@BeforeEach
	void setUp() throws Exception {
		TestToolsService service = new TestToolsService();

		// Create tool callbacks for test methods
		ToolCallback realTimeAnalysis = MethodToolCallback.builder()
			.toolDefinition(ToolDefinitions.from(TestToolsService.class.getMethod("realTimeAnalysis", String.class)))
			.toolMethod(TestToolsService.class.getMethod("realTimeAnalysis", String.class))
			.toolObject(service)
			.build();

		ToolCallback historicalAnalysis = MethodToolCallback.builder()
			.toolDefinition(ToolDefinitions.from(TestToolsService.class.getMethod("historicalAnalysis", String.class)))
			.toolMethod(TestToolsService.class.getMethod("historicalAnalysis", String.class))
			.toolObject(service)
			.build();

		ToolCallback reportGeneration = MethodToolCallback.builder()
			.toolDefinition(ToolDefinitions.from(TestToolsService.class.getMethod("reportGeneration", String.class)))
			.toolMethod(TestToolsService.class.getMethod("reportGeneration", String.class))
			.toolObject(service)
			.build();

		ToolCallback dataValidation = MethodToolCallback.builder()
			.toolDefinition(ToolDefinitions.from(TestToolsService.class.getMethod("dataValidation", String.class)))
			.toolMethod(TestToolsService.class.getMethod("dataValidation", String.class))
			.toolObject(service)
			.build();

		this.allTools = List.of(realTimeAnalysis, historicalAnalysis, reportGeneration, dataValidation);
	}

	@Test
	void shouldFilterByType() {
		List<ToolCallback> filtered = ToolCallbackFilters.filterByType(this.allTools, "RealTimeAnalysis");

		assertThat(filtered).hasSize(1);
		assertThat(filtered.get(0).getToolDefinition().name()).isEqualTo("realTimeAnalysis");
	}

	@Test
	void shouldFilterByCategory() {
		List<ToolCallback> filtered = ToolCallbackFilters.filterByCategory(this.allTools, "analytics");

		assertThat(filtered).hasSize(2);
		assertThat(filtered).extracting(tc -> tc.getToolDefinition().name())
			.containsExactlyInAnyOrder("realTimeAnalysis", "historicalAnalysis");
	}

	@Test
	void shouldFilterByMinPriority() {
		List<ToolCallback> filtered = ToolCallbackFilters.filterByMinPriority(this.allTools, 7);

		assertThat(filtered).hasSize(2);
		assertThat(filtered).extracting(tc -> tc.getToolDefinition().name())
			.containsExactlyInAnyOrder("realTimeAnalysis", "reportGeneration");
	}

	@Test
	void shouldFilterByTags() {
		List<ToolCallback> filtered = ToolCallbackFilters.filterByTags(this.allTools, "critical");

		assertThat(filtered).hasSize(2);
		assertThat(filtered).extracting(tc -> tc.getToolDefinition().name())
			.containsExactlyInAnyOrder("realTimeAnalysis", "dataValidation");
	}

	@Test
	void shouldFilterByCustomMetadata() {
		List<ToolCallback> filtered = this.allTools.stream()
			.filter(ToolCallbackFilters.byMetadata("environment", "production"))
			.toList();

		assertThat(filtered).hasSize(1);
		assertThat(filtered.get(0).getToolDefinition().name()).isEqualTo("realTimeAnalysis");
	}

	@Test
	void shouldCombineMultipleFilters() {
		List<ToolCallback> filtered = this.allTools.stream()
			.filter(ToolCallbackFilters.byCategory("analytics").and(ToolCallbackFilters.byMinPriority(7)))
			.toList();

		assertThat(filtered).hasSize(1);
		assertThat(filtered.get(0).getToolDefinition().name()).isEqualTo("realTimeAnalysis");
	}

	@Test
	void shouldReturnEmptyListWhenNoMatch() {
		List<ToolCallback> filtered = ToolCallbackFilters.filterByType(this.allTools, "NonExistentType");

		assertThat(filtered).isEmpty();
	}

	@Test
	void shouldFilterArrayByType() {
		ToolCallback[] array = this.allTools.toArray(new ToolCallback[0]);
		ToolCallback[] filtered = ToolCallbackFilters.filterByType(array, "RealTimeAnalysis");

		assertThat(filtered).hasSize(1);
		assertThat(filtered[0].getToolDefinition().name()).isEqualTo("realTimeAnalysis");
	}

	// Test tools service
	static class TestToolsService {

		@Tool(description = "Analyzes real-time market data")
		@ToolMetadata(type = "RealTimeAnalysis", category = "analytics", priority = 8,
				tags = { "critical", "realtime" }, value = { "environment=production" })
		public String realTimeAnalysis(String symbol) {
			return "Real-time analysis for " + symbol;
		}

		@Tool(description = "Analyzes historical trends")
		@ToolMetadata(type = "HistoricalAnalysis", category = "analytics", priority = 6, tags = { "historical" })
		public String historicalAnalysis(String period) {
			return "Historical analysis for " + period;
		}

		@Tool(description = "Generates reports")
		@ToolMetadata(type = "Reporting", category = "reporting", priority = 7, tags = { "reporting" })
		public String reportGeneration(String type) {
			return "Report: " + type;
		}

		@Tool(description = "Validates data")
		@ToolMetadata(type = "Validation", category = "quality", priority = 5, tags = { "critical", "validation" })
		public String dataValidation(String data) {
			return "Validation result for " + data;
		}

	}

}
