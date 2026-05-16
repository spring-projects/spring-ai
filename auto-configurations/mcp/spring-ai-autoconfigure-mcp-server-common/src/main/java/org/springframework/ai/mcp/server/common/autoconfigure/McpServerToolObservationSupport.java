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

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.observation.DefaultToolCallingObservationConvention;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.ai.tool.observation.ToolCallingObservationConvention;
import org.springframework.ai.tool.observation.ToolCallingObservationDocumentation;
import org.springframework.ai.util.json.schema.JsonSchemaUtils;

/**
 * Observability support for MCP server tool calls.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
final class McpServerToolObservationSupport {

	private static final String MCP_SERVER_PROTOCOL = "spring.ai.mcp.server.protocol";

	private static final String MCP_SERVER_TYPE = "spring.ai.mcp.server.type";

	private static final String PROTOCOL_STATEFUL = "stateful";

	private static final String PROTOCOL_STATELESS = "stateless";

	private static final String TYPE_SYNC = "sync";

	private static final String TYPE_ASYNC = "async";

	private static final ToolCallingObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultToolCallingObservationConvention();

	private final ObservationRegistry observationRegistry;

	private final ToolCallingObservationConvention observationConvention;

	McpServerToolObservationSupport(ObservationRegistry observationRegistry,
			@Nullable ToolCallingObservationConvention observationConvention) {
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

	List<io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification> wrapStatelessSync(
			List<io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification> toolSpecifications) {
		return toolSpecifications.stream().map(this::wrapStatelessSync).toList();
	}

	List<io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncToolSpecification> wrapStatelessAsync(
			List<io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncToolSpecification> toolSpecifications) {
		return toolSpecifications.stream().map(this::wrapStatelessAsync).toList();
	}

	private SyncToolSpecification wrapStatefulSync(SyncToolSpecification toolSpecification) {
		return new SyncToolSpecification(toolSpecification.tool(),
				(exchange, request) -> observeSync(toolSpecification.tool(), request, PROTOCOL_STATEFUL, TYPE_SYNC,
						() -> toolSpecification.callHandler().apply(exchange, request)));
	}

	private AsyncToolSpecification wrapStatefulAsync(AsyncToolSpecification toolSpecification) {
		return new AsyncToolSpecification(toolSpecification.tool(),
				(exchange, request) -> observeAsync(toolSpecification.tool(), request, PROTOCOL_STATEFUL, TYPE_ASYNC,
						() -> toolSpecification.callHandler().apply(exchange, request)));
	}

	private io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification wrapStatelessSync(
			io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification toolSpecification) {
		return new io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification(
				toolSpecification.tool(), (context, request) -> observeSync(toolSpecification.tool(), request,
						PROTOCOL_STATELESS, TYPE_SYNC, () -> toolSpecification.callHandler().apply(context, request)));
	}

	private io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncToolSpecification wrapStatelessAsync(
			io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncToolSpecification toolSpecification) {
		return new io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncToolSpecification(
				toolSpecification.tool(), (context, request) -> observeAsync(toolSpecification.tool(), request,
						PROTOCOL_STATELESS, TYPE_ASYNC, () -> toolSpecification.callHandler().apply(context, request)));
	}

	private CallToolResult observeSync(McpSchema.Tool tool, CallToolRequest request, String protocol, String type,
			ToolCallSupplier toolCallSupplier) {
		ToolCallingObservationContext observationContext = createObservationContext(tool, request, protocol, type);
		Observation observation = createObservation(observationContext).start();
		try {
			CallToolResult result = toolCallSupplier.get();
			observationContext.setToolCallResult(toJson(result));
			recordErrorResult(observation, result);
			return result;
		}
		catch (RuntimeException | Error ex) {
			observation.error(ex);
			throw ex;
		}
		finally {
			observation.stop();
		}
	}

	private Mono<CallToolResult> observeAsync(McpSchema.Tool tool, CallToolRequest request, String protocol,
			String type, AsyncToolCallSupplier toolCallSupplier) {
		return Mono.defer(() -> {
			ToolCallingObservationContext observationContext = createObservationContext(tool, request, protocol, type);
			Observation observation = createObservation(observationContext).start();
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
		});
	}

	private Observation createObservation(ToolCallingObservationContext observationContext) {
		return ToolCallingObservationDocumentation.TOOL_CALL.observation(this.observationConvention,
				DEFAULT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry);
	}

	private ToolCallingObservationContext createObservationContext(McpSchema.Tool tool, CallToolRequest request,
			String protocol, String type) {
		ToolCallingObservationContext observationContext = ToolCallingObservationContext.builder()
			.toolDefinition(createToolDefinition(tool))
			.toolType("function")
			.toolCallArguments(toJson(request.arguments()))
			.build();
		observationContext.addLowCardinalityKeyValue(KeyValue.of(MCP_SERVER_PROTOCOL, protocol));
		observationContext.addLowCardinalityKeyValue(KeyValue.of(MCP_SERVER_TYPE, type));
		return observationContext;
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

	private interface ToolCallSupplier {

		CallToolResult get();

	}

	private interface AsyncToolCallSupplier {

		Mono<CallToolResult> get();

	}

	private static final class McpToolCallResultErrorException extends RuntimeException {

		private McpToolCallResultErrorException() {
			super("MCP tool call returned an error result", null, false, false);
		}

	}

}
