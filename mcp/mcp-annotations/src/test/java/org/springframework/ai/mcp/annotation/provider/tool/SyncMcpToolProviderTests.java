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

package org.springframework.ai.mcp.annotation.provider.tool;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.context.MetaProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link SyncMcpToolProvider}.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Craig Walls
 */
public class SyncMcpToolProviderTests {

	@Test
	void testConstructorWithNullToolObjects() {
		assertThatThrownBy(() -> new SyncMcpToolProvider(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("toolObjects cannot be null");
	}

	@Test
	void testGetToolSpecificationsWithSingleValidTool() {
		// Create a class with only one valid tool method
		class SingleValidTool {

			@McpTool(name = "test-tool", description = "A test tool")
			public String testTool(String input) {
				return "Processed: " + input;
			}

		}

		SingleValidTool toolObject = new SingleValidTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).isNotNull();
		assertThat(toolSpecs).hasSize(1);

		SyncToolSpecification toolSpec = toolSpecs.get(0);
		assertThat(toolSpec.tool().name()).isEqualTo("test-tool");
		assertThat(toolSpec.tool().description()).isEqualTo("A test tool");
		assertThat(toolSpec.tool().inputSchema()).isNotNull();
		assertThat(toolSpec.callHandler()).isNotNull();

		// Test that the handler works
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("test-tool", Map.of("input", "hello"));
		CallToolResult result = toolSpec.callHandler().apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Processed: hello");
	}

	@Test
	void testGetToolSpecificationsWithCustomToolName() {
		class CustomNameTool {

			@McpTool(name = "custom-name", description = "Custom named tool")
			public String methodWithDifferentName(String input) {
				return "Custom: " + input;
			}

		}

		CustomNameTool toolObject = new CustomNameTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		assertThat(toolSpecs.get(0).tool().name()).isEqualTo("custom-name");
		assertThat(toolSpecs.get(0).tool().description()).isEqualTo("Custom named tool");
	}

	@Test
	void testGetToolSpecificationsWithDefaultToolName() {
		class DefaultNameTool {

			@McpTool(description = "Tool with default name")
			public String defaultNameMethod(String input) {
				return "Default: " + input;
			}

		}

		DefaultNameTool toolObject = new DefaultNameTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		assertThat(toolSpecs.get(0).tool().name()).isEqualTo("defaultNameMethod");
		assertThat(toolSpecs.get(0).tool().description()).isEqualTo("Tool with default name");
	}

	@Test
	void testGetToolSpecificationsWithEmptyToolName() {
		class EmptyNameTool {

			@McpTool(name = "", description = "Tool with empty name")
			public String emptyNameMethod(String input) {
				return "Empty: " + input;
			}

		}

		EmptyNameTool toolObject = new EmptyNameTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		assertThat(toolSpecs.get(0).tool().name()).isEqualTo("emptyNameMethod");
		assertThat(toolSpecs.get(0).tool().description()).isEqualTo("Tool with empty name");
	}

	@Test
	void testGetToolSpecificationsFiltersOutMonoReturnTypes() {
		class MonoReturnTool {

			@McpTool(name = "mono-tool", description = "Tool returning Mono")
			public Mono<String> monoTool(String input) {
				return Mono.just("Mono: " + input);
			}

			@McpTool(name = "sync-tool", description = "Synchronous tool")
			public String syncTool(String input) {
				return "Sync: " + input;
			}

		}

		MonoReturnTool toolObject = new MonoReturnTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		assertThat(toolSpecs.get(0).tool().name()).isEqualTo("sync-tool");
		assertThat(toolSpecs.get(0).tool().description()).isEqualTo("Synchronous tool");
	}

	@Test
	void testGetToolSpecificationsWithMultipleToolMethods() {
		class MultipleToolMethods {

			@McpTool(name = "tool1", description = "First tool")
			public String firstTool(String input) {
				return "First: " + input;
			}

			@McpTool(name = "tool2", description = "Second tool")
			public String secondTool(String input) {
				return "Second: " + input;
			}

		}

		MultipleToolMethods toolObject = new MultipleToolMethods();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(2);
		assertThat(toolSpecs.get(0).tool().name()).isIn("tool1", "tool2");
		assertThat(toolSpecs.get(1).tool().name()).isIn("tool1", "tool2");
		assertThat(toolSpecs.get(0).tool().name()).isNotEqualTo(toolSpecs.get(1).tool().name());
	}

	@Test
	void testGetToolSpecificationsWithMultipleToolObjects() {
		class FirstToolObject {

			@McpTool(name = "first-tool", description = "First tool")
			public String firstTool(String input) {
				return "First: " + input;
			}

		}

		class SecondToolObject {

			@McpTool(name = "second-tool", description = "Second tool")
			public String secondTool(String input) {
				return "Second: " + input;
			}

		}

		FirstToolObject firstObject = new FirstToolObject();
		SecondToolObject secondObject = new SecondToolObject();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(firstObject, secondObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(2);
		assertThat(toolSpecs.get(0).tool().name()).isIn("first-tool", "second-tool");
		assertThat(toolSpecs.get(1).tool().name()).isIn("first-tool", "second-tool");
		assertThat(toolSpecs.get(0).tool().name()).isNotEqualTo(toolSpecs.get(1).tool().name());
	}

	@Test
	void testGetToolSpecificationsWithMixedMethods() {
		class MixedMethods {

			@McpTool(name = "valid-tool", description = "Valid tool")
			public String validTool(String input) {
				return "Valid: " + input;
			}

			public String nonAnnotatedMethod(String input) {
				return "Non-annotated: " + input;
			}

			@McpTool(name = "mono-tool", description = "Mono tool")
			public Mono<String> monoTool(String input) {
				return Mono.just("Mono: " + input);
			}

		}

		MixedMethods toolObject = new MixedMethods();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		assertThat(toolSpecs.get(0).tool().name()).isEqualTo("valid-tool");
		assertThat(toolSpecs.get(0).tool().description()).isEqualTo("Valid tool");
	}

	@Test
	void testGetToolSpecificationsWithComplexParameters() {
		class ComplexParameterTool {

			@McpTool(name = "complex-tool", description = "Tool with complex parameters")
			public String complexTool(String name, int age, boolean active, List<String> tags) {
				return String.format("Name: %s, Age: %d, Active: %b, Tags: %s", name, age, active,
						String.join(",", tags));
			}

		}

		ComplexParameterTool toolObject = new ComplexParameterTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		assertThat(toolSpecs.get(0).tool().name()).isEqualTo("complex-tool");
		assertThat(toolSpecs.get(0).tool().description()).isEqualTo("Tool with complex parameters");
		assertThat(toolSpecs.get(0).tool().inputSchema()).isNotNull();

		// Test that the handler works with complex parameters
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("complex-tool",
				Map.of("name", "John", "age", 30, "active", true, "tags", List.of("tag1", "tag2")));
		CallToolResult result = toolSpecs.get(0).callHandler().apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text())
			.isEqualTo("Name: John, Age: 30, Active: true, Tags: tag1,tag2");
	}

	@Test
	void testGetToolSpecificationsWithNoParameters() {
		class NoParameterTool {

			@McpTool(name = "no-param-tool", description = "Tool with no parameters")
			public String noParamTool() {
				return "No parameters needed";
			}

		}

		NoParameterTool toolObject = new NoParameterTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		assertThat(toolSpecs.get(0).tool().name()).isEqualTo("no-param-tool");
		assertThat(toolSpecs.get(0).tool().description()).isEqualTo("Tool with no parameters");

		// Test that the handler works with no parameters
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("no-param-tool", Map.of());
		CallToolResult result = toolSpecs.get(0).callHandler().apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("No parameters needed");
	}

	@Test
	void testGetToolSpecificationsWithCallToolResultReturn() {
		class CallToolResultTool {

			@McpTool(name = "result-tool", description = "Tool returning CallToolResult")
			public CallToolResult resultTool(String message) {
				return CallToolResult.builder().addTextContent("Result: " + message).build();
			}

		}

		CallToolResultTool toolObject = new CallToolResultTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		assertThat(toolSpecs.get(0).tool().name()).isEqualTo("result-tool");
		assertThat(toolSpecs.get(0).tool().description()).isEqualTo("Tool returning CallToolResult");

		// Test that the handler works with CallToolResult return type
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("result-tool", Map.of("message", "test"));
		CallToolResult result = toolSpecs.get(0).callHandler().apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Result: test");
	}

	@Test
	void testGetToolSpecificationsWithPrivateMethod() {
		class PrivateMethodTool {

			@McpTool(name = "private-tool", description = "Private tool method")
			private String privateTool(String input) {
				return "Private: " + input;
			}

		}

		PrivateMethodTool toolObject = new PrivateMethodTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		assertThat(toolSpecs.get(0).tool().name()).isEqualTo("private-tool");
		assertThat(toolSpecs.get(0).tool().description()).isEqualTo("Private tool method");

		// Test that the handler works with private methods
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("private-tool", Map.of("input", "test"));
		CallToolResult result = toolSpecs.get(0).callHandler().apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Private: test");
	}

	@Test
	void testGetToolSpecificationsJsonSchemaGeneration() {
		class SchemaTestTool {

			@McpTool(name = "schema-tool", description = "Tool for schema testing")
			public String schemaTool(String requiredParam, Integer optionalParam) {
				return "Schema test: " + requiredParam + ", " + optionalParam;
			}

		}

		SchemaTestTool toolObject = new SchemaTestTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		SyncToolSpecification toolSpec = toolSpecs.get(0);

		assertThat(toolSpec.tool().name()).isEqualTo("schema-tool");
		assertThat(toolSpec.tool().description()).isEqualTo("Tool for schema testing");
		assertThat(toolSpec.tool().inputSchema()).isNotNull();

		// The input schema should be a valid JSON string containing parameter names
		String schemaString = toolSpec.tool().inputSchema().toString();
		assertThat(schemaString).isNotEmpty();
		assertThat(schemaString).contains("requiredParam");
		assertThat(schemaString).contains("optionalParam");
	}

	@Test
	void testToolWithTitle() {
		class TitleTool {

			@McpTool(name = "title-tool", description = "Tool with title", title = "Custom Title")
			public String titleTool(String input) {
				return "Title: " + input;
			}

		}

		TitleTool toolObject = new TitleTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		assertThat(toolSpecs.get(0).tool().name()).isEqualTo("title-tool");
		assertThat(toolSpecs.get(0).tool().title()).isEqualTo("Custom Title");
	}

	@Test
	void testToolTitlePrecedence() {
		// Test that title attribute takes precedence over annotations.title
		class TitlePrecedenceTool {

			@McpTool(name = "precedence-tool", description = "Tool with title precedence", title = "Title Attribute",
					annotations = @McpTool.McpAnnotations(title = "Annotations Title"))
			public String precedenceTool(String input) {
				return "Precedence: " + input;
			}

		}

		TitlePrecedenceTool toolObject = new TitlePrecedenceTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		// According to the implementation, title attribute takes precedence over
		// annotations.title
		assertThat(toolSpecs.get(0).tool().title()).isEqualTo("Title Attribute");
	}

	@Test
	void testToolAnnotationsTitleUsedWhenNoTitleAttribute() {
		// Test that annotations.title is used when title attribute is not provided
		class AnnotationsTitleTool {

			@McpTool(name = "annotations-title-tool", description = "Tool with only annotations title",
					annotations = @McpTool.McpAnnotations(title = "Annotations Title Only"))
			public String annotationsTitleTool(String input) {
				return "Annotations title: " + input;
			}

		}

		AnnotationsTitleTool toolObject = new AnnotationsTitleTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		// When no title attribute is provided, annotations.title should be used
		assertThat(toolSpecs.get(0).tool().title()).isEqualTo("Annotations Title Only");
	}

	@Test
	void testToolWithoutTitleUsesName() {
		class NoTitleTool {

			@McpTool(name = "no-title-tool", description = "Tool without title")
			public String noTitleTool(String input) {
				return "No title: " + input;
			}

		}

		NoTitleTool toolObject = new NoTitleTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		// When no title is provided, the name should be used
		assertThat(toolSpecs.get(0).tool().title()).isEqualTo("no-title-tool");
	}

	@Test
	void testToolWithAnnotations() {
		class AnnotatedTool {

			@McpTool(name = "annotated-tool", description = "Tool with annotations",
					annotations = @McpTool.McpAnnotations(title = "Annotated Tool", readOnlyHint = true,
							destructiveHint = false, idempotentHint = true, openWorldHint = false))
			public String annotatedTool(String input) {
				return "Annotated: " + input;
			}

		}

		AnnotatedTool toolObject = new AnnotatedTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		SyncToolSpecification toolSpec = toolSpecs.get(0);

		assertThat(toolSpec.tool().name()).isEqualTo("annotated-tool");
		assertThat(toolSpec.tool().title()).isEqualTo("Annotated Tool");

		ToolAnnotations annotations = toolSpec.tool().annotations();
		assertThat(annotations).isNotNull();
		assertThat(annotations.title()).isEqualTo("Annotated Tool");
		assertThat(annotations.readOnlyHint()).isTrue();
		assertThat(annotations.destructiveHint()).isFalse();
		assertThat(annotations.idempotentHint()).isTrue();
		assertThat(annotations.openWorldHint()).isFalse();
	}

	@Test
	void testToolWithDefaultAnnotations() {
		class DefaultAnnotationsTool {

			@McpTool(name = "default-annotations-tool", description = "Tool with default annotations")
			public String defaultAnnotationsTool(String input) {
				return "Default annotations: " + input;
			}

		}

		DefaultAnnotationsTool toolObject = new DefaultAnnotationsTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		SyncToolSpecification toolSpec = toolSpecs.get(0);

		// With default annotations, the annotations object should still be created
		ToolAnnotations annotations = toolSpec.tool().annotations();
		assertThat(annotations).isNotNull();
		// Check default values
		assertThat(annotations.readOnlyHint()).isFalse();
		assertThat(annotations.destructiveHint()).isTrue();
		assertThat(annotations.idempotentHint()).isFalse();
		assertThat(annotations.openWorldHint()).isTrue();
	}

	@Test
	void testToolWithOutputSchemaGeneration() {

		// Define a custom result class
		record CustomResult(
				@JsonPropertyDescription("customResultMessage") @JsonProperty(required = false) String message,
				@JsonProperty(required = true) int count) {
		}

		class OutputSchemaTool {

			@McpTool(name = "output-schema-tool", description = "Tool with output schema", generateOutputSchema = true)
			public List<CustomResult> outputSchemaTool(String input) {
				return List.of(new CustomResult("Processed: " + input, input.length()));
			}

		}

		OutputSchemaTool toolObject = new OutputSchemaTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		SyncToolSpecification toolSpec = toolSpecs.get(0);

		assertThat(toolSpec.tool().name()).isEqualTo("output-schema-tool");
		// Output schema should be generated for complex types
		assertThat(toolSpec.tool().outputSchema()).isNotNull();
		String outputSchemaString = toolSpec.tool().outputSchema().toString();
		assertThat(outputSchemaString).contains("message");
		assertThat(outputSchemaString).contains("count");
		assertThat(outputSchemaString).isEqualTo(
				"{$schema=https://json-schema.org/draft/2020-12/schema, type=array, items={type=object, properties={count={type=integer, format=int32}, message={type=string, description=customResultMessage}}, required=[count]}}");
	}

	@Test
	void testToolWithDisabledOutputSchemaGeneration() {
		class CustomResult {

			public String message;

			CustomResult(String message) {
				this.message = message;
			}

		}

		class NoOutputSchemaTool {

			@McpTool(name = "no-output-schema-tool", description = "Tool without output schema",
					generateOutputSchema = false)
			public CustomResult noOutputSchemaTool(String input) {
				return new CustomResult("Processed: " + input);
			}

		}

		NoOutputSchemaTool toolObject = new NoOutputSchemaTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		SyncToolSpecification toolSpec = toolSpecs.get(0);

		assertThat(toolSpec.tool().name()).isEqualTo("no-output-schema-tool");
		// Output schema should not be generated when disabled
		assertThat(toolSpec.tool().outputSchema()).isNull();
	}

	@Test
	void testToolWithListReturnType() {

		record CustomResult(String message) {
		}

		class ListResponseTool {

			@McpTool(name = "list-response", description = "Tool List response")
			public List<CustomResult> listResponseTool(String input) {
				return List.of(new CustomResult("Processed: " + input));
			}

		}

		ListResponseTool toolObject = new ListResponseTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		SyncToolSpecification toolSpec = toolSpecs.get(0);

		assertThat(toolSpec.tool().name()).isEqualTo("list-response");
		assertThat(toolSpec.tool().outputSchema()).isNull();

		BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> callHandler = toolSpec
			.callHandler();

		McpSchema.CallToolResult result = callHandler.apply(mock(McpSyncServerExchange.class),
				new CallToolRequest("list-response", Map.of("input", "test")));
		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);

		String jsonText = ((TextContent) result.content().get(0)).text();
		JsonAssertions.assertThatJson(jsonText).when(Option.IGNORING_ARRAY_ORDER).isArray().hasSize(1);
		JsonAssertions.assertThatJson(jsonText).when(Option.IGNORING_ARRAY_ORDER).isEqualTo(JsonAssertions.json("""
				[{"message":"Processed: test"}]"""));
	}

	@Test
	void testToolWithStructuredListReturnType() {

		record CustomResult(String message) {
		}

		class ListResponseTool {

			@McpTool(name = "list-response", description = "Tool List response", generateOutputSchema = true)
			public List<CustomResult> listResponseTool(String input) {
				return List.of(new CustomResult("Processed: " + input));
			}

		}

		ListResponseTool toolObject = new ListResponseTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		SyncToolSpecification toolSpec = toolSpecs.get(0);

		assertThat(toolSpec.tool().name()).isEqualTo("list-response");
		assertThat(toolSpec.tool().outputSchema()).isNotNull();

		BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> callHandler = toolSpec
			.callHandler();

		McpSchema.CallToolResult result = callHandler.apply(mock(McpSyncServerExchange.class),
				new CallToolRequest("list-response", Map.of("input", "test")));

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();

		assertThat(result.structuredContent()).isInstanceOf(List.class);
		assertThat((List<?>) result.structuredContent()).hasSize(1);
		Map<String, Object> firstEntry = ((List<Map<String, Object>>) result.structuredContent()).get(0);
		assertThat(firstEntry).containsEntry("message", "Processed: test");
	}

	@Test
	void testToolWithPrimitiveReturnTypeNoOutputSchema() {
		class PrimitiveTool {

			@McpTool(name = "primitive-tool", description = "Tool with primitive return")
			public int primitiveTool(String input) {
				return input.length();
			}

		}

		PrimitiveTool toolObject = new PrimitiveTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		SyncToolSpecification toolSpec = toolSpecs.get(0);

		assertThat(toolSpec.tool().name()).isEqualTo("primitive-tool");
		// Output schema should not be generated for primitive types
		assertThat(toolSpec.tool().outputSchema()).isNull();
	}

	@Test
	void testToolWithVoidReturnTypeNoOutputSchema() {
		class VoidTool {

			@McpTool(name = "void-tool", description = "Tool with void return")
			public void voidTool(String input) {
				// Do nothing
			}

		}

		VoidTool toolObject = new VoidTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		SyncToolSpecification toolSpec = toolSpecs.get(0);

		assertThat(toolSpec.tool().name()).isEqualTo("void-tool");
		// Output schema should not be generated for void return type
		assertThat(toolSpec.tool().outputSchema()).isNull();
	}

	@Test
	void testToolWithStringReturnTypeNoOutputSchema() {
		class StringTool {

			@McpTool(name = "string-tool", description = "Tool with String return")
			public String stringTool(String input) {
				return "Result: " + input;
			}

		}

		StringTool toolObject = new StringTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		SyncToolSpecification toolSpec = toolSpecs.get(0);

		assertThat(toolSpec.tool().name()).isEqualTo("string-tool");
		// Output schema should not be generated for simple value types like String
		assertThat(toolSpec.tool().outputSchema()).isNull();
	}

	@Test
	void testToolWithMeta() {
		class MetaTool {

			@McpTool(name = "ui-tool", description = "Tool with meta", metaProvider = UiMetaProvider.class)
			public String uiTool(String input) {
				return "result: " + input;
			}

		}

		MetaTool toolObject = new MetaTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		McpSchema.Tool tool = toolSpecs.get(0).tool();
		assertThat(tool.name()).isEqualTo("ui-tool");
		assertThat(tool.meta()).isNotNull();
		assertThat(tool.meta()).containsKey("ui");
		assertThat(tool.meta()).containsKey("ui/resourceUri");
		assertThat(tool.meta().get("ui/resourceUri")).isEqualTo("ui://test/view.html");

		@SuppressWarnings("unchecked")
		Map<String, Object> ui = (Map<String, Object>) tool.meta().get("ui");
		assertThat(ui.get("resourceUri")).isEqualTo("ui://test/view.html");
	}

	@Test
	void testToolWithEmptyMeta() {
		class NoMetaTool {

			@McpTool(name = "plain-tool", description = "Tool without meta")
			public String plainTool(String input) {
				return "result: " + input;
			}

		}

		NoMetaTool toolObject = new NoMetaTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		assertThat(toolSpecs.get(0).tool().meta()).isNull();
	}

	@Test
	void testToolWithCallToolRequestParameter() {
		class CallToolRequestParamTool {

			@McpTool(name = "request-param-tool", description = "Tool with CallToolRequest parameter")
			public String requestParamTool(CallToolRequest request, String additionalParam) {
				return "Request tool: " + request.name() + ", param: " + additionalParam;
			}

		}

		CallToolRequestParamTool toolObject = new CallToolRequestParamTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		SyncToolSpecification toolSpec = toolSpecs.get(0);

		assertThat(toolSpec.tool().name()).isEqualTo("request-param-tool");
		assertThat(toolSpec.tool().description()).isEqualTo("Tool with CallToolRequest parameter");

		// The input schema should still be generated but should handle CallToolRequest
		// specially
		assertThat(toolSpec.tool().inputSchema()).isNotNull();
		String schemaString = toolSpec.tool().inputSchema().toString();
		// Should contain the additional parameter but not the CallToolRequest
		assertThat(schemaString).contains("additionalParam");
	}

	@Test
	void testToolWithOnlyCallToolRequestParameter() {

		class OnlyCallToolRequestTool {

			@McpTool(name = "only-request-tool", description = "Tool with only CallToolRequest parameter")
			public String onlyRequestTool(CallToolRequest request) {
				return "Only request tool: " + request.name();
			}

		}

		OnlyCallToolRequestTool toolObject = new OnlyCallToolRequestTool();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		SyncToolSpecification toolSpec = toolSpecs.get(0);

		assertThat(toolSpec.tool().name()).isEqualTo("only-request-tool");
		assertThat(toolSpec.tool().description()).isEqualTo("Tool with only CallToolRequest parameter");

		// The input schema should be minimal when only CallToolRequest is present
		assertThat(toolSpec.tool().inputSchema()).isNotNull();
	}

	@Test
	void testSetExceptionHandler_customHandlerIsInvoked() {
		class FailingTool {

			@McpTool(name = "failing-tool", description = "Tool that always fails")
			public String failingTool(String input) {
				throw new RuntimeException("tool failure: " + input);
			}

		}

		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(new FailingTool()));
		provider.setExceptionHandler((toolName, ex) -> CallToolResult.builder()
			.isError(true)
			.addTextContent("CUSTOM[" + toolName + "]: " + ex.getMessage())
			.build());

		CallToolResult result = provider.getToolSpecifications()
			.get(0)
			.callHandler()
			.apply(mock(McpSyncServerExchange.class), new CallToolRequest("failing-tool", Map.of("input", "test")));

		assertThat(result.isError()).isTrue();
		assertThat(((TextContent) result.content().get(0)).text())
			.isEqualTo("CUSTOM[failing-tool]: tool failure: test");
	}

	public static class UiMetaProvider implements MetaProvider {

		@Override
		public Map<String, Object> getMeta() {
			return Map.of("ui", Map.of("resourceUri", "ui://test/view.html", "visibility", List.of("model", "app")),
					"ui/resourceUri", "ui://test/view.html");
		}

	}

}
