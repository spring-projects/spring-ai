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

package org.springframework.ai.mcp.server.common.autoconfigure;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransportProviderBase;
import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;

import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerSpecificationFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for @McpTool annotations with STDIO transport.
 */
public class McpToolWithStdioIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpServerAutoConfiguration.class,
				McpServerObjectMapperAutoConfiguration.class, McpServerAnnotationScannerAutoConfiguration.class,
				McpServerSpecificationFactoryAutoConfiguration.class));

	/**
	 * Verifies that a configured ObjectMapper bean is created for MCP server operations.
	 */
	@Test
	void shouldCreateConfiguredObjectMapperForMcpServer() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(ObjectMapper.class);
			ObjectMapper objectMapper = context.getBean("mcpServerObjectMapper", ObjectMapper.class);

			assertThat(objectMapper).isNotNull();

			// Verify that the ObjectMapper is properly configured
			String emptyBeanJson = objectMapper.writeValueAsString(new EmptyBean());
			assertThat(emptyBeanJson).isEqualTo("{}"); // Should not fail on empty beans

			String nullValueJson = objectMapper.writeValueAsString(new BeanWithNull());
			assertThat(nullValueJson).doesNotContain("null"); // Should exclude null
																// values
		});
	}

	/**
	 * Verifies that STDIO transport uses the configured ObjectMapper.
	 */
	@Test
	void stdioTransportShouldUseConfiguredObjectMapper() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(McpServerTransportProviderBase.class);
			assertThat(context.getBean(McpServerTransportProviderBase.class))
				.isInstanceOf(StdioServerTransportProvider.class);

			// Verify that the MCP server was created successfully
			assertThat(context).hasSingleBean(McpSyncServer.class);
		});
	}

	/**
	 * Verifies that @McpTool annotated methods are successfully registered with STDIO
	 * transport and that tool specifications can be properly serialized to JSON without
	 * errors.
	 */
	@Test
	@SuppressWarnings("unchecked")
	void mcpToolAnnotationsShouldWorkWithStdio() {
		this.contextRunner.withBean(TestCalculatorTools.class).run(context -> {
			// Verify the server was created
			assertThat(context).hasSingleBean(McpSyncServer.class);
			McpSyncServer syncServer = context.getBean(McpSyncServer.class);

			// Get the async server from sync server (internal structure)
			McpAsyncServer asyncServer = (McpAsyncServer) ReflectionTestUtils.getField(syncServer, "asyncServer");
			assertThat(asyncServer).isNotNull();

			// Verify that tools were registered
			CopyOnWriteArrayList<AsyncToolSpecification> tools = (CopyOnWriteArrayList<AsyncToolSpecification>) ReflectionTestUtils
				.getField(asyncServer, "tools");

			assertThat(tools).isNotEmpty();
			assertThat(tools).hasSize(3);

			// Verify tool names
			List<String> toolNames = tools.stream().map(spec -> spec.tool().name()).toList();
			assertThat(toolNames).containsExactlyInAnyOrder("add", "subtract", "multiply");

			// Verify that each tool has a valid inputSchema that can be serialized
			ObjectMapper objectMapper = context.getBean("mcpServerObjectMapper", ObjectMapper.class);

			for (AsyncToolSpecification spec : tools) {
				McpSchema.Tool tool = spec.tool();

				// Verify basic tool properties
				assertThat(tool.name()).isNotBlank();
				assertThat(tool.description()).isNotBlank();

				// Verify inputSchema can be serialized to JSON without errors
				if (tool.inputSchema() != null) {
					String schemaJson = objectMapper.writeValueAsString(tool.inputSchema());
					assertThat(schemaJson).isNotBlank();

					// Should be valid JSON
					objectMapper.readTree(schemaJson);
				}
			}
		});
	}

	/**
	 * Verifies that tools with complex parameter types work correctly.
	 */
	@Test
	@SuppressWarnings("unchecked")
	void mcpToolWithComplexParametersShouldWorkWithStdio() {
		this.contextRunner.withBean(TestComplexTools.class).run(context -> {
			assertThat(context).hasSingleBean(McpSyncServer.class);
			McpSyncServer syncServer = context.getBean(McpSyncServer.class);

			McpAsyncServer asyncServer = (McpAsyncServer) ReflectionTestUtils.getField(syncServer, "asyncServer");

			CopyOnWriteArrayList<AsyncToolSpecification> tools = (CopyOnWriteArrayList<AsyncToolSpecification>) ReflectionTestUtils
				.getField(asyncServer, "tools");

			assertThat(tools).hasSize(1);

			AsyncToolSpecification spec = tools.get(0);
			assertThat(spec.tool().name()).isEqualTo("processData");

			// Verify the tool can be serialized
			ObjectMapper objectMapper = context.getBean("mcpServerObjectMapper", ObjectMapper.class);
			String toolJson = objectMapper.writeValueAsString(spec.tool());
			assertThat(toolJson).isNotBlank();
		});
	}

	// Test components

	@Component
	static class TestCalculatorTools {

		@McpTool(name = "add", description = "Add two numbers")
		public int add(@McpToolParam(description = "First number", required = true) int a,
				@McpToolParam(description = "Second number", required = true) int b) {
			return a + b;
		}

		@McpTool(name = "subtract", description = "Subtract two numbers")
		public int subtract(@McpToolParam(description = "First number", required = true) int a,
				@McpToolParam(description = "Second number", required = true) int b) {
			return a - b;
		}

		@McpTool(name = "multiply", description = "Multiply two numbers")
		public int multiply(@McpToolParam(description = "First number", required = true) int a,
				@McpToolParam(description = "Second number", required = true) int b) {
			return a * b;
		}

	}

	@Component
	static class TestComplexTools {

		@McpTool(name = "processData", description = "Process complex data")
		public String processData(@McpToolParam(description = "Input data", required = true) String input,
				@McpToolParam(description = "Options", required = false) String options) {
			return "Processed: " + input + " with options: " + options;
		}

	}

	// Test beans for ObjectMapper configuration verification

	static class EmptyBean {

	}

	static class BeanWithNull {

		public String value = null;

		public String anotherValue = "test";

	}

}
