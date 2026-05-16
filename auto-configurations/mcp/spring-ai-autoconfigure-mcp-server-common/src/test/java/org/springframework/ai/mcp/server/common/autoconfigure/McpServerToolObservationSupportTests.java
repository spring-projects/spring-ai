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

import java.util.List;
import java.util.Map;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.server.common.autoconfigure.observation.DefaultMcpServerToolObservationConvention;
import org.springframework.ai.mcp.server.common.autoconfigure.observation.McpServerToolContentObservationFilter;
import org.springframework.ai.mcp.server.common.autoconfigure.observation.McpServerToolObservationContext;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.observation.DefaultToolCallingObservationConvention;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.ai.tool.observation.ToolCallingObservationDocumentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpServerToolObservationSupportTests {

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	private final McpServerToolObservationSupport observationSupport = new McpServerToolObservationSupport(
			this.observationRegistry, null);

	@Test
	void statefulSyncToolCallEmitsObservation() {
		SyncToolSpecification toolSpecification = new SyncToolSpecification(tool(), (exchange, request) -> result());

		SyncToolSpecification wrapped = this.observationSupport.wrapStatefulSync(List.of(toolSpecification)).get(0);

		wrapped.callHandler().apply(null, request());

		assertObservation("stateful", "sync").doesNotHaveError();
	}

	@Test
	void statefulAsyncToolCallEmitsObservation() {
		AsyncToolSpecification toolSpecification = new AsyncToolSpecification(tool(),
				(exchange, request) -> Mono.just(result()));

		AsyncToolSpecification wrapped = this.observationSupport.wrapStatefulAsync(List.of(toolSpecification)).get(0);

		wrapped.callHandler().apply(null, request()).block();

		assertObservation("stateful", "async").doesNotHaveError();
	}

	@Test
	void statelessSyncToolCallEmitsObservation() {
		var toolSpecification = new io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification(
				tool(), (exchange, request) -> result());

		var wrapped = this.observationSupport.wrapStatelessSync(List.of(toolSpecification)).get(0);

		wrapped.callHandler().apply(null, request());

		assertObservation("stateless", "sync").doesNotHaveError();
	}

	@Test
	void statelessAsyncToolCallEmitsObservation() {
		var toolSpecification = new io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncToolSpecification(
				tool(), (exchange, request) -> Mono.just(result()));

		var wrapped = this.observationSupport.wrapStatelessAsync(List.of(toolSpecification)).get(0);

		wrapped.callHandler().apply(null, request()).block();

		assertObservation("stateless", "async").doesNotHaveError();
	}

	@Test
	void syncExceptionMarksObservationError() {
		SyncToolSpecification toolSpecification = new SyncToolSpecification(tool(), (exchange, request) -> {
			throw new IllegalStateException("boom");
		});

		SyncToolSpecification wrapped = this.observationSupport.wrapStatefulSync(List.of(toolSpecification)).get(0);

		assertThatThrownBy(() -> wrapped.callHandler().apply(null, request()))
			.isInstanceOf(IllegalStateException.class);
		assertObservation("stateful", "sync").hasError();
	}

	@Test
	void asyncExceptionMarksObservationError() {
		AsyncToolSpecification toolSpecification = new AsyncToolSpecification(tool(),
				(exchange, request) -> Mono.error(new IllegalStateException("boom")));

		AsyncToolSpecification wrapped = this.observationSupport.wrapStatefulAsync(List.of(toolSpecification)).get(0);

		assertThatThrownBy(() -> wrapped.callHandler().apply(null, request()).block())
			.isInstanceOf(IllegalStateException.class);
		assertObservation("stateful", "async").hasError();
	}

	@Test
	void asyncCancellationStopsObservationWithoutError() {
		AsyncToolSpecification toolSpecification = new AsyncToolSpecification(tool(),
				(exchange, request) -> Mono.never());

		AsyncToolSpecification wrapped = this.observationSupport.wrapStatefulAsync(List.of(toolSpecification)).get(0);

		Disposable subscription = wrapped.callHandler().apply(null, request()).subscribe();
		subscription.dispose();

		assertObservation("stateful", "async").doesNotHaveError();
	}

	@Test
	void errorResultMarksObservationError() {
		SyncToolSpecification toolSpecification = new SyncToolSpecification(tool(),
				(exchange, request) -> errorResult());

		SyncToolSpecification wrapped = this.observationSupport.wrapStatefulSync(List.of(toolSpecification)).get(0);

		wrapped.callHandler().apply(null, request());

		assertObservation("stateful", "sync").hasError();
	}

	@Test
	void contentIsAvailableInContextAndFilteredIntoHighCardinalityValues() {
		this.observationRegistry.observationConfig().observationFilter(new McpServerToolContentObservationFilter());
		SyncToolSpecification toolSpecification = new SyncToolSpecification(tool(), (exchange, request) -> result());

		SyncToolSpecification wrapped = this.observationSupport.wrapStatefulSync(List.of(toolSpecification)).get(0);

		wrapped.callHandler().apply(null, request());

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasSingleObservationThat()
			.hasHighCardinalityKeyValueWithKey("spring.ai.mcp.server.tool.call.arguments")
			.hasHighCardinalityKeyValueWithKey("spring.ai.mcp.server.tool.call.result");
		TestObservationRegistryAssert.assertThat(this.observationRegistry).hasHandledContextsThatSatisfy(contexts -> {
			assertThat(contexts).singleElement()
				.isInstanceOfSatisfying(McpServerToolObservationContext.class, context -> {
					JsonAssertions.assertThatJson(context.getToolCallArguments()).isEqualTo("{\"city\":\"Warsaw\"}");
					assertThat(context.getHighCardinalityKeyValues()).anySatisfy(keyValue -> {
						assertThat(keyValue.getKey()).isEqualTo("spring.ai.mcp.server.tool.call.arguments");
						JsonAssertions.assertThatJson(keyValue.getValue()).isEqualTo("{\"city\":\"Warsaw\"}");
					});
					assertThat(context.getToolCallResult()).contains("ok");
				});
		});
	}

	@Test
	void customObservationConventionIsHonored() {
		var support = new McpServerToolObservationSupport(this.observationRegistry,
				new DefaultMcpServerToolObservationConvention("custom.tool"));
		SyncToolSpecification toolSpecification = new SyncToolSpecification(tool(), (exchange, request) -> result());

		SyncToolSpecification wrapped = support.wrapStatefulSync(List.of(toolSpecification)).get(0);

		wrapped.callHandler().apply(null, request());

		TestObservationRegistryAssert.assertThat(this.observationRegistry).hasObservationWithNameEqualTo("custom.tool");
	}

	@Test
	void mcpServerToolObservationIsDistinctFromClientToolCallingObservation() {
		ToolCallingObservationContext clientObservationContext = ToolCallingObservationContext.builder()
			.toolDefinition(
					ToolDefinition.builder().name("weather").description("Get the weather").inputSchema("{}").build())
			.build();
		ToolCallingObservationDocumentation.TOOL_CALL
			.observation(null, new DefaultToolCallingObservationConvention(), () -> clientObservationContext,
					this.observationRegistry)
			.start()
			.stop();
		SyncToolSpecification toolSpecification = new SyncToolSpecification(tool(), (exchange, request) -> result());
		SyncToolSpecification wrapped = this.observationSupport.wrapStatefulSync(List.of(toolSpecification)).get(0);

		wrapped.callHandler().apply(null, request());

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo("spring.ai.tool");
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo("spring.ai.mcp.server.tool");
		TestObservationRegistryAssert.assertThat(this.observationRegistry).hasHandledContextsThatSatisfy(contexts -> {
			assertThat(contexts).hasSize(2);
			assertThat(contexts)
				.anySatisfy(context -> assertThat(context.getLowCardinalityKeyValues()).anySatisfy(keyValue -> {
					assertThat(keyValue.getKey()).isEqualTo("spring.ai.kind");
					assertThat(keyValue.getValue()).isEqualTo("tool_call");
				}));
			assertThat(contexts)
				.anySatisfy(context -> assertThat(context.getLowCardinalityKeyValues()).anySatisfy(keyValue -> {
					assertThat(keyValue.getKey()).isEqualTo("spring.ai.kind");
					assertThat(keyValue.getValue()).isEqualTo("mcp_server_tool_call");
				}));
		});
	}

	private io.micrometer.observation.tck.ObservationContextAssert<?> assertObservation(String protocol, String type) {
		return TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasSingleObservationThat()
			.hasNameEqualTo("spring.ai.mcp.server.tool")
			.hasLowCardinalityKeyValue("gen_ai.operation.name", "execute_tool")
			.hasLowCardinalityKeyValue("gen_ai.system", "spring_ai")
			.hasLowCardinalityKeyValue("spring.ai.kind", "mcp_server_tool_call")
			.hasLowCardinalityKeyValue("spring.ai.tool.definition.name", "weather")
			.hasLowCardinalityKeyValue("spring.ai.tool.type", "function")
			.hasLowCardinalityKeyValue("spring.ai.mcp.server.protocol", protocol)
			.hasLowCardinalityKeyValue("spring.ai.mcp.server.type", type)
			.doesNotHaveHighCardinalityKeyValueWithKey("spring.ai.mcp.server.tool.call.arguments")
			.doesNotHaveHighCardinalityKeyValueWithKey("spring.ai.mcp.server.tool.call.result");
	}

	private static McpSchema.Tool tool() {
		return McpSchema.Tool.builder()
			.name("weather")
			.description("Get the weather")
			.inputSchema(Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string"))))
			.build();
	}

	private static McpSchema.CallToolRequest request() {
		return new McpSchema.CallToolRequest("weather", Map.of("city", "Warsaw"));
	}

	private static McpSchema.CallToolResult result() {
		return McpSchema.CallToolResult.builder()
			.content(List.of(new McpSchema.TextContent("ok")))
			.isError(false)
			.build();
	}

	private static McpSchema.CallToolResult errorResult() {
		return McpSchema.CallToolResult.builder()
			.content(List.of(new McpSchema.TextContent("failed")))
			.isError(true)
			.build();
	}

}
