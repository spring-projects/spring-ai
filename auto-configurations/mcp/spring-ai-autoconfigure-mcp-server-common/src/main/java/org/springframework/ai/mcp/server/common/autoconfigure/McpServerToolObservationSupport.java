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
import java.util.function.BiFunction;
import java.util.function.Supplier;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.server.common.autoconfigure.observation.DefaultMcpServerToolObservationConvention;
import org.springframework.ai.mcp.server.common.autoconfigure.observation.McpServerToolObservationContext;
import org.springframework.ai.mcp.server.common.autoconfigure.observation.McpServerToolObservationConvention;
import org.springframework.ai.mcp.server.common.autoconfigure.observation.McpServerToolObservationDocumentation;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.json.schema.JsonSchemaUtils;

/**
 * Observability support for MCP server tool calls.
 *
 * @author Michal Grandys
 */
final class McpServerToolObservationSupport {

	private static final String PROTOCOL_STATEFUL = "stateful";

	private static final String PROTOCOL_STATELESS = "stateless";

	private static final String TYPE_SYNC = "sync";

	private static final String TYPE_ASYNC = "async";

	private static final McpServerToolObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultMcpServerToolObservationConvention();

	private final ObservationRegistry observationRegistry;

	private final McpServerToolObservationConvention observationConvention;

	McpServerToolObservationSupport(ObservationRegistry observationRegistry,
			@Nullable McpServerToolObservationConvention observationConvention) {
		this.observationRegistry = observationRegistry;
		this.observationConvention = observationConvention != null ? observationConvention
				: DEFAULT_OBSERVATION_CONVENTION;
	}

	List<SyncToolSpecification> wrapStatefulSync(List<SyncToolSpecification> toolSpecifications) {
		return toolSpecifications.stream().map(this::wrapStatefulSync).toList();
	}

	List<AsyncToolSpecification> wrapStatefulAsync(List<AsyncToolSpecification> toolSpecifications) {
		return toolSpecifications.stream().map(this::wrapStatefulAsync).toList();
	}

	List<McpStatelessServerFeatures.SyncToolSpecification> wrapStatelessSync(
			List<McpStatelessServerFeatures.SyncToolSpecification> toolSpecifications) {
		return toolSpecifications.stream().map(this::wrapStatelessSync).toList();
	}

	List<McpStatelessServerFeatures.AsyncToolSpecification> wrapStatelessAsync(
			List<McpStatelessServerFeatures.AsyncToolSpecification> toolSpecifications) {
		return toolSpecifications.stream().map(this::wrapStatelessAsync).toList();
	}

	private SyncToolSpecification wrapStatefulSync(SyncToolSpecification toolSpecification) {
		ToolDefinition toolDefinition = createToolDefinition(toolSpecification.tool());
		return new SyncToolSpecification(toolSpecification.tool(), (exchange, request) -> observeSync(toolDefinition,
				request, PROTOCOL_STATEFUL, () -> toolSpecification.callHandler().apply(exchange, request)));
	}

	private AsyncToolSpecification wrapStatefulAsync(AsyncToolSpecification toolSpecification) {
		ToolDefinition toolDefinition = createToolDefinition(toolSpecification.tool());
		return new AsyncToolSpecification(toolSpecification.tool(), (exchange, request) -> observeAsync(toolDefinition,
				request, PROTOCOL_STATEFUL, () -> toolSpecification.callHandler().apply(exchange, request)));
	}

	private McpStatelessServerFeatures.SyncToolSpecification wrapStatelessSync(
			McpStatelessServerFeatures.SyncToolSpecification toolSpecification) {
		ToolDefinition toolDefinition = createToolDefinition(toolSpecification.tool());
		return new McpStatelessServerFeatures.SyncToolSpecification(toolSpecification.tool(),
				(context, request) -> observeSync(toolDefinition, request, PROTOCOL_STATELESS,
						() -> toolSpecification.callHandler().apply(context, request)));
	}

	private McpStatelessServerFeatures.AsyncToolSpecification wrapStatelessAsync(
			McpStatelessServerFeatures.AsyncToolSpecification toolSpecification) {
		ToolDefinition toolDefinition = createToolDefinition(toolSpecification.tool());
		return new McpStatelessServerFeatures.AsyncToolSpecification(toolSpecification.tool(),
				(context, request) -> observeAsync(toolDefinition, request, PROTOCOL_STATELESS,
						() -> toolSpecification.callHandler().apply(context, request)));
	}

	private CallToolResult observeSync(ToolDefinition toolDefinition, CallToolRequest request, String protocol,
			Supplier<CallToolResult> toolCallSupplier) {
		return observe(toolDefinition, request, protocol, TYPE_SYNC,
				(observationContext, observation) -> observation.observe(() -> {
					CallToolResult result = toolCallSupplier.get();
					observationContext.setToolCallResult(toJson(result));
					recordErrorResult(observation, result);
					return result;
				}));
	}

	private Mono<CallToolResult> observeAsync(ToolDefinition toolDefinition, CallToolRequest request, String protocol,
			Supplier<Mono<CallToolResult>> toolCallSupplier) {
		return Mono
			.defer(() -> observe(toolDefinition, request, protocol, TYPE_ASYNC, (observationContext, observation) -> {
				observation.start();
				try {
					return toolCallSupplier.get().doOnNext(result -> {
						observationContext.setToolCallResult(toJson(result));
						recordErrorResult(observation, result);
					}).doOnError(observation::error).doFinally(signalType -> observation.stop());
				}
				catch (RuntimeException | Error ex) {
					observation.error(ex);
					observation.stop();
					return Mono.error(ex);
				}
			}));
	}

	private <T> T observe(ToolDefinition toolDefinition, CallToolRequest request, String protocol, String type,
			BiFunction<McpServerToolObservationContext, Observation, T> observationHandler) {
		McpServerToolObservationContext observationContext = createObservationContext(toolDefinition, request, protocol,
				type);
		Observation observation = createObservation(observationContext);
		return observationHandler.apply(observationContext, observation);
	}

	private Observation createObservation(McpServerToolObservationContext observationContext) {
		return McpServerToolObservationDocumentation.MCP_SERVER_TOOL_CALL.observation(this.observationConvention,
				DEFAULT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry);
	}

	private McpServerToolObservationContext createObservationContext(ToolDefinition toolDefinition,
			CallToolRequest request, String protocol, String type) {
		return McpServerToolObservationContext.builder()
			.toolDefinition(toolDefinition)
			.toolCallArguments(toJson(request.arguments()))
			.mcpServerProtocol(protocol)
			.mcpServerType(type)
			.build();
	}

	private static ToolDefinition createToolDefinition(McpSchema.Tool tool) {
		return ToolDefinition.builder()
			.name(tool.name())
			.description(tool.description())
			.inputSchema(JsonSchemaUtils.ensureValidInputSchema(toJson(tool.inputSchema())))
			.build();
	}

	private static String toJson(@Nullable Object value) {
		if (value == null) {
			return "{}";
		}
		return ModelOptionsUtils.toJsonString(value);
	}

	private static void recordErrorResult(Observation observation, @Nullable CallToolResult result) {
		if (result != null && Boolean.TRUE.equals(result.isError())) {
			observation.error(new McpToolCallResultErrorException());
		}
	}

	private static final class McpToolCallResultErrorException extends RuntimeException {

		private McpToolCallResultErrorException() {
			super("MCP tool call returned an error result", null, false, false);
		}

	}

}
