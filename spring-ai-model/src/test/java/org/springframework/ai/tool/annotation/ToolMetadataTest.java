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

package org.springframework.ai.tool.annotation;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.ai.tool.support.ToolUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ToolMetadata} annotation and metadata extraction.
 *
 */
class ToolMetadataTest {

	@Test
	void shouldExtractMetadataFromAnnotation() throws Exception {
		Method method = TestTools.class.getMethod("realTimeAnalysis", String.class);
		Map<String, Object> metadata = ToolUtils.getToolMetadata(method);

		assertThat(metadata).isNotEmpty();
		assertThat(metadata.get("type")).isEqualTo("RealTimeAnalysis");
		assertThat(metadata.get("category")).isEqualTo("market");
		assertThat(metadata.get("priority")).isEqualTo(8);
	}

	@Test
	void shouldExtractTagsFromAnnotation() throws Exception {
		Method method = TestTools.class.getMethod("historicalAnalysis", String.class);
		Map<String, Object> metadata = ToolUtils.getToolMetadata(method);

		assertThat(metadata).isNotEmpty();
		assertThat(metadata.get("tags")).isInstanceOf(List.class);
		@SuppressWarnings("unchecked")
		List<String> tags = (List<String>) metadata.get("tags");
		assertThat(tags).containsExactlyInAnyOrder("historical", "longterm");
	}

	@Test
	void shouldExtractMultipleMetadataFields() throws Exception {
		Method method = TestTools.class.getMethod("customTool");
		Map<String, Object> metadata = ToolUtils.getToolMetadata(method);

		assertThat(metadata).isNotEmpty();
		assertThat(metadata.get("type")).isEqualTo("CustomType");
		assertThat(metadata.get("category")).isEqualTo("custom");
	}

	@Test
	void shouldReturnEmptyMapWhenNoMetadata() throws Exception {
		Method method = TestTools.class.getMethod("noMetadataTool");
		Map<String, Object> metadata = ToolUtils.getToolMetadata(method);

		assertThat(metadata).isEmpty();
	}

	@Test
	void shouldIncludeMetadataInToolDefinition() throws Exception {
		Method method = TestTools.class.getMethod("realTimeAnalysis", String.class);
		ToolDefinition toolDefinition = ToolDefinitions.from(method);

		assertThat(toolDefinition.metadata()).isNotEmpty();
		assertThat(toolDefinition.metadata().get("type")).isEqualTo("RealTimeAnalysis");
		assertThat(toolDefinition.metadata().get("category")).isEqualTo("market");
		assertThat(toolDefinition.metadata().get("priority")).isEqualTo(8);
	}

	@Test
	void shouldHandleDefaultPriority() throws Exception {
		Method method = TestTools.class.getMethod("defaultPriorityTool");
		Map<String, Object> metadata = ToolUtils.getToolMetadata(method);

		assertThat(metadata.get("priority")).isEqualTo(5); // Default priority
	}

	@Test
	void shouldHandlePartialMetadata() throws Exception {
		Method method = TestTools.class.getMethod("partialMetadataTool");
		Map<String, Object> metadata = ToolUtils.getToolMetadata(method);

		assertThat(metadata).isNotEmpty();
		assertThat(metadata.get("type")).isEqualTo("PartialType");
		assertThat(metadata.get("category")).isNull();
		assertThat(metadata.get("priority")).isEqualTo(5); // Default
	}

	// Test tools class
	static class TestTools {

		@Tool(description = "Analyzes real-time market data")
		@ToolMetadata(type = "RealTimeAnalysis", category = "market", priority = 8)
		public String realTimeAnalysis(String symbol) {
			return "Real-time analysis for " + symbol;
		}

		@Tool(description = "Analyzes historical trends")
		@ToolMetadata(type = "HistoricalAnalysis", category = "market", priority = 6,
				tags = { "historical", "longterm" })
		public String historicalAnalysis(String period) {
			return "Historical analysis for " + period;
		}

		@Tool(description = "Custom tool with additional metadata")
		@ToolMetadata(type = "CustomType", category = "custom")
		public String customTool() {
			return "Custom result";
		}

		@Tool(description = "Tool without metadata")
		public String noMetadataTool() {
			return "No metadata";
		}

		@Tool(description = "Tool with default priority")
		@ToolMetadata(category = "test")
		public String defaultPriorityTool() {
			return "Default priority";
		}

		@Tool(description = "Tool with partial metadata")
		@ToolMetadata(type = "PartialType")
		public String partialMetadataTool() {
			return "Partial metadata";
		}

	}

}
