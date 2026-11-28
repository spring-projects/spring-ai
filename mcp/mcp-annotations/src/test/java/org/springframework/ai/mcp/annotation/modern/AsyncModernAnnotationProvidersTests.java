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

package org.springframework.ai.mcp.annotation.modern;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCMessage;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCNotification;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCRequest;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCResponse;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.CacheScope;
import io.modelcontextprotocol.modern.McpSchema.CallToolOutcome;
import io.modelcontextprotocol.modern.McpSchema.CallToolRequest;
import io.modelcontextprotocol.modern.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.modern.McpSchema.CompleteRequest;
import io.modelcontextprotocol.modern.McpSchema.CompleteResult;
import io.modelcontextprotocol.modern.McpSchema.CreateMessageRequest;
import io.modelcontextprotocol.modern.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.modern.McpSchema.GetPromptOutcome;
import io.modelcontextprotocol.modern.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.McpSchema.InputRequiredResult;
import io.modelcontextprotocol.modern.McpSchema.MetaKeys;
import io.modelcontextprotocol.modern.McpSchema.PromptMessage;
import io.modelcontextprotocol.modern.McpSchema.ReadResourceOutcome;
import io.modelcontextprotocol.modern.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.modern.McpSchema.Role;
import io.modelcontextprotocol.modern.McpSchema.SamplingMessage;
import io.modelcontextprotocol.modern.McpSchema.TextContent;
import io.modelcontextprotocol.modern.McpSchema.TextResourceContents;
import io.modelcontextprotocol.modern.server.McpAsyncNotifier;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.McpSyncNotifier;
import io.modelcontextprotocol.modern.server.McpTransportResponse;
import io.modelcontextprotocol.modern.server.feature.McpAsyncTool;
import io.modelcontextprotocol.modern.server.feature.PromptsFeature;
import io.modelcontextprotocol.modern.server.feature.ResourcesFeature;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import io.modelcontextprotocol.spec.McpError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import org.slf4j.LoggerFactory;
import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpClientCapability;
import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.common.ModernMethodSupport;
import org.springframework.ai.mcp.annotation.context.McpModernRequestContext;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.annotation.method.resource.AsyncModernMcpResourceMethodCallback;
import org.springframework.ai.mcp.annotation.method.tool.AsyncModernMcpToolMethodCallback;
import org.springframework.ai.mcp.annotation.method.tool.ReturnMode;
import org.springframework.ai.mcp.annotation.method.tool.SyncModernMcpToolMethodCallback;
import org.springframework.ai.mcp.annotation.spring.AsyncMcpAnnotationProviders;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.ai.mcp.modern.AsyncResourceRegistration;
import org.springframework.ai.mcp.modern.InMemoryAsyncPromptRepository;
import org.springframework.ai.mcp.modern.InMemoryAsyncResourceRepository;
import org.springframework.ai.mcp.modern.InMemoryAsyncToolRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The asynchronous twin of {@link ModernAnnotationProvidersTests}: drives the async
 * modern providers through a real {@link McpServer} on its non-blocking path.
 *
 * @author Christian Tzolov
 */
class AsyncModernAnnotationProvidersTests {

	private static Map<String, Object> params(Object... keyValues) {
		Map<String, Object> params = new HashMap<>();
		Map<String, Object> meta = new HashMap<>();
		meta.put(MetaKeys.PROTOCOL_VERSION, McpSchema.LATEST_PROTOCOL_VERSION);
		meta.put(MetaKeys.CLIENT_CAPABILITIES, Map.of("elicitation", Map.of("form", Map.of()), "sampling", Map.of()));
		params.put("_meta", meta);
		for (int i = 0; i < keyValues.length; i += 2) {
			params.put((String) keyValues[i], keyValues[i + 1]);
		}
		return params;
	}

	private static McpServer server() {
		return server(new Tools());
	}

	private static McpServer server(Object toolBean) {
		return McpServer.builder()
			.serverInfo(Implementation.builder("test", "1").build())
			.feature(ToolsFeature.ofAsync(InMemoryAsyncToolRepository.builder()
				.tools(AsyncMcpAnnotationProviders.modernTools(List.of(toolBean)))
				.build()))
			.feature(ResourcesFeature.ofAsync(InMemoryAsyncResourceRepository.builder()
				.resources(AsyncMcpAnnotationProviders.modernResources(List.of(new Resources())))
				.resourceTemplates(AsyncMcpAnnotationProviders.modernResourceTemplates(List.of(new Resources()),
						List.of(new Completions()), null))
				.build()))
			.feature(PromptsFeature.ofAsync(InMemoryAsyncPromptRepository.builder()
				.prompts(AsyncMcpAnnotationProviders.modernPrompts(List.of(new Prompts()), List.of(new Completions()),
						null))
				.build()))
			.build();
	}

	private static McpServer returnTypesServer() {
		ReturnTypes bean = new ReturnTypes();
		return McpServer.builder()
			.serverInfo(Implementation.builder("test", "1").build())
			.feature(ToolsFeature.ofAsync(InMemoryAsyncToolRepository.builder()
				.tools(AsyncMcpAnnotationProviders.modernTools(List.of(bean)))
				.build()))
			.feature(ResourcesFeature.ofAsync(InMemoryAsyncResourceRepository.builder()
				.resources(AsyncMcpAnnotationProviders.modernResources(List.of(bean)))
				.build()))
			.feature(PromptsFeature.ofAsync(InMemoryAsyncPromptRepository.builder()
				.prompts(AsyncMcpAnnotationProviders.modernPrompts(List.of(bean), List.of(bean), null))
				.build()))
			.build();
	}

	private static McpTransportResponse send(McpServer server, String method, Map<String, Object> params) {
		return server.handle(McpTransportContext.EMPTY, new JSONRPCRequest(method, 1, params)).block();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> result(McpTransportResponse response) {
		if (response instanceof McpTransportResponse.Result result) {
			return (Map<String, Object>) result.response().result();
		}
		if (response instanceof McpTransportResponse.Streaming streaming) {
			List<JSONRPCMessage> messages = streaming.messages().collectList().block();
			return (Map<String, Object>) ((JSONRPCResponse) messages.get(messages.size() - 1)).result();
		}
		throw new AssertionError("Not a result: " + ((McpTransportResponse.Error) response).response().error());
	}

	private static JSONRPCResponse finalResponse(McpTransportResponse response) {
		if (response instanceof McpTransportResponse.Result result) {
			return result.response();
		}
		if (response instanceof McpTransportResponse.Error error) {
			return error.response();
		}
		List<JSONRPCMessage> messages = ((McpTransportResponse.Streaming) response).messages().collectList().block();
		return (JSONRPCResponse) messages.get(messages.size() - 1);
	}

	@SuppressWarnings("unchecked")
	private static String firstText(Map<String, Object> result) {
		List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
		return (String) content.get(0).get("text");
	}

	private static McpAsyncTool tool(List<McpAsyncTool> tools, String name) {
		return tools.stream().filter(tool -> tool.definition().name().equals(name)).findFirst().orElseThrow();
	}

	@Test
	@SuppressWarnings("unchecked")
	void toolSchemaExcludesInfrastructureParameters() {
		List<McpAsyncTool> tools = AsyncMcpAnnotationProviders.modernTools(List.of(new Tools()));
		McpAsyncTool progress = tool(tools, "progress");
		Map<String, Object> properties = (Map<String, Object>) progress.definition().inputSchema().get("properties");
		assertThat(properties).containsOnlyKeys("steps");
		assertThat(((AsyncModernMcpToolMethodCallback) progress.handler()).isStreaming()).isTrue();
		assertThat(((AsyncModernMcpToolMethodCallback) tool(tools, "greet").handler()).isStreaming()).isFalse();
		assertThat(((AsyncModernMcpToolMethodCallback) tool(tools, "ask_name").handler()).isStreaming()).isFalse();
	}

	@Test
	void reactiveToolReturnsText() {
		Map<String, Object> result = result(send(server(), McpSchema.METHOD_TOOLS_CALL,
				params("name", "greet", "arguments", Map.of("name", "Bob"))));
		assertThat(firstText(result)).isEqualTo("Hello Bob");
	}

	@Test
	void reactiveToolRunsOnTheCallingThread() {
		Map<String, Object> result = result(
				send(server(), McpSchema.METHOD_TOOLS_CALL, params("name", "reactive_thread")));
		assertThat(firstText(result)).isEqualTo(Thread.currentThread().getName());
	}

	@Test
	void plainToolRunsOffTheCallingThreadUnlessTheRequestIsBlocking() {
		McpServer server = server();
		Map<String, Object> params = params("name", "plain");
		Map<String, Object> result = result(send(server, McpSchema.METHOD_TOOLS_CALL, params));
		assertThat(firstText(result)).startsWith("boundedElastic");

		McpTransportResponse blocking = server
			.handleBlocking(McpTransportContext.EMPTY, new JSONRPCRequest(McpSchema.METHOD_TOOLS_CALL, 1, params))
			.block();
		assertThat(firstText(result(blocking))).isEqualTo(Thread.currentThread().getName());
	}

	@ParameterizedTest
	@ValueSource(strings = { "mono", "future", "stage", "publisher_of_mono", "publisher_of_one", "custom_publisher",
			"flow_publisher", "object_of_mono", "object_of_future", "object_of_value" })
	void toolAnswersWithASingleValueFromAnySupportedReturnType(String tool) {
		Map<String, Object> result = result(
				send(returnTypesServer(), McpSchema.METHOD_TOOLS_CALL, params("name", tool)));
		assertThat(result.get("isError")).isNotEqualTo(true);
		assertThat(firstText(result)).isEqualTo("value");
	}

	@ParameterizedTest
	@ValueSource(strings = { "future_of_void", "publisher_of_void" })
	void toolWithoutAValueAnswersDone(String tool) {
		List<McpAsyncTool> tools = AsyncMcpAnnotationProviders.modernTools(List.of(new ReturnTypes()));
		assertThat(tool(tools, tool).definition().outputSchema()).isNull();
		Map<String, Object> result = result(
				send(returnTypesServer(), McpSchema.METHOD_TOOLS_CALL, params("name", tool)));
		assertThat(firstText(result)).contains("Done");
	}

	@ParameterizedTest
	@ValueSource(strings = { "publisher_of_two", "object_of_two" })
	void toolPublisherOfSeveralElementsFailsTheCall(String tool) {
		Map<String, Object> result = result(
				send(returnTypesServer(), McpSchema.METHOD_TOOLS_CALL, params("name", tool)));
		assertThat(result.get("isError")).isEqualTo(true);
		assertThat(firstText(result)).contains("more than one element",
				tool.equals("object_of_two") ? "objectOfTwo" : "publisherOfTwo");
	}

	@Test
	void toolPublisherIsCancelledOnceItHasEmittedTooMuch() {
		ReturnTypes bean = new ReturnTypes();
		result(send(server(bean), McpSchema.METHOD_TOOLS_CALL, params("name", "unbounded_publisher")));
		assertThat(bean.emitted.get()).isEqualTo(2);
	}

	@Test
	void failedFutureBecomesErrorResult() {
		Map<String, Object> result = result(
				send(returnTypesServer(), McpSchema.METHOD_TOOLS_CALL, params("name", "failed_future")));
		assertThat(result.get("isError")).isEqualTo(true);
	}

	@Test
	void outputSchemaComesFromTheFutureAndPublisherElementType() {
		List<McpAsyncTool> tools = AsyncMcpAnnotationProviders.modernTools(List.of(new ReturnTypes()));
		assertThat(tool(tools, "future_of_point").definition().outputSchema()).containsKey("properties");
		assertThat(tool(tools, "publisher_of_point").definition().outputSchema()).containsKey("properties");
	}

	@Test
	@SuppressWarnings("unchecked")
	void rawReactiveToolHasNoOutputSchemaAndAnswers() {
		List<McpAsyncTool> tools = AsyncMcpAnnotationProviders.modernTools(List.of(new ReturnTypes()));
		assertThat(tool(tools, "raw_mono").definition().outputSchema()).isNull();
		Map<String, Object> result = result(
				send(returnTypesServer(), McpSchema.METHOD_TOOLS_CALL, params("name", "raw_mono")));
		assertThat(firstText(result)).isEqualTo("value");
	}

	@ParameterizedTest
	@ValueSource(strings = { "future_prompt", "stage_prompt", "publisher_prompt", "object_prompt", "raw_prompt" })
	@SuppressWarnings("unchecked")
	void promptAnswersFromAnySupportedReturnType(String prompt) {
		Map<String, Object> result = result(
				send(returnTypesServer(), McpSchema.METHOD_PROMPTS_GET, params("name", prompt)));
		List<Map<String, Object>> messages = (List<Map<String, Object>>) result.get("messages");
		assertThat(messages).extracting(message -> ((Map<String, Object>) message.get("content")).get("text"))
			.containsExactly("value");
	}

	@ParameterizedTest
	@ValueSource(strings = { "test://future", "test://stage", "test://publisher", "test://object", "test://raw" })
	@SuppressWarnings("unchecked")
	void resourceAnswersFromAnySupportedReturnType(String uri) {
		Map<String, Object> result = result(
				send(returnTypesServer(), McpSchema.METHOD_RESOURCES_READ, params("uri", uri)));
		assertThat((List<Map<String, Object>>) result.get("contents")).extracting(content -> content.get("text"))
			.containsExactly("value");
	}

	@ParameterizedTest
	@ValueSource(strings = { "future_prompt", "stage_prompt", "publisher_prompt", "object_prompt", "raw_prompt" })
	@SuppressWarnings("unchecked")
	void completionAnswersFromAnySupportedReturnType(String prompt) {
		Map<String, Object> completion = (Map<String, Object>) result(send(returnTypesServer(),
				McpSchema.METHOD_COMPLETION_COMPLETE, params("ref", Map.of("type", "ref/prompt", "name", prompt),
						"argument", Map.of("name", "topic", "value", "x"))))
			.get("completion");
		assertThat((List<String>) completion.get("values")).containsExactly("value");
	}

	@Test
	void publisherOfSeveralElementsFailsPromptsResourcesAndCompletions() {
		McpServer server = returnTypesServer();
		assertThat(
				finalResponse(send(server, McpSchema.METHOD_PROMPTS_GET, params("name", "two_prompt"))).error().code())
			.isEqualTo(-32603);
		assertThat(finalResponse(send(server, McpSchema.METHOD_RESOURCES_READ, params("uri", "test://two"))).error()
			.code()).isEqualTo(-32603);
		assertThat(finalResponse(send(server, McpSchema.METHOD_COMPLETION_COMPLETE, params("ref",
				Map.of("type", "ref/prompt", "name", "two_prompt"), "argument", Map.of("name", "topic", "value", "x"))))
			.error()
			.code()).isEqualTo(-32603);
	}

	@Test
	void declaredFluxIsRejectedAtStartupOnEveryPrimitive() {
		assertThatThrownBy(() -> AsyncMcpAnnotationProviders.modernTools(List.of(new FluxTool())))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("returns a Flux")
			.hasMessageContaining("Mono<List<T>>")
			.hasMessageContaining("flux in " + FluxTool.class.getName());
		assertThatThrownBy(() -> AsyncMcpAnnotationProviders.modernPrompts(List.of(new FluxPrompt())))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("returns a Flux");
		assertThatThrownBy(() -> AsyncMcpAnnotationProviders.modernResources(List.of(new FluxResource())))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("returns a Flux");
		assertThatThrownBy(
				() -> AsyncMcpAnnotationProviders.modernPrompts(List.of(), List.of(new FluxCompletion()), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("returns a Flux");
	}

	@Test
	@SuppressWarnings("unchecked")
	void resourceTemplateCompletionsArePublished() {
		Map<String, Object> completion = result(send(server(), McpSchema.METHOD_COMPLETION_COMPLETE,
				params("ref", Map.of("type", "ref/resource", "uri", "test://items/{id}"), "argument",
						Map.of("name", "id", "value", "4"))));
		Map<String, Object> values = (Map<String, Object>) completion.get("completion");
		assertThat((List<String>) values.get("values")).containsExactly("item-4");
	}

	@Test
	void completionWithoutAnAnnotatedTargetFailsAtStartup() {
		assertThatThrownBy(() -> AsyncMcpAnnotationProviders.modernPrompts(List.of(), List.of(new Completions()), null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("[greeting, confirm]")
			.hasMessageContaining("McpAsyncPrompt.withCompletionHandler");
		assertThatThrownBy(
				() -> AsyncMcpAnnotationProviders.modernResourceTemplates(List.of(), List.of(new Completions()), null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("[test://items/{id}]")
			.hasMessageContaining("AsyncResourceTemplateRegistration.withCompletionHandler");
		// A static resource has no variables to complete.
		assertThatThrownBy(() -> AsyncMcpAnnotationProviders.modernResourceTemplates(List.of(new Resources()),
				List.of(new StaticResourceCompletion()), null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("[test://static]");
	}

	@Test
	void completionOfASkippedPromptOrTemplateIsSkippedToo() {
		// An async server skips methods that declare McpSyncNotifier, with a warning.
		assertThat(AsyncMcpAnnotationProviders.modernPrompts(List.of(new SkippedTargets()),
				List.of(new SkippedTargetCompletions()), null))
			.isEmpty();
		assertThat(AsyncMcpAnnotationProviders.modernResourceTemplates(List.of(new SkippedTargets()),
				List.of(new SkippedTargetCompletions()), null))
			.isEmpty();
	}

	@Test
	void twoCompletionsForOneReferenceFailAtStartup() {
		assertThatThrownBy(() -> AsyncMcpAnnotationProviders.modernPrompts(List.of(new Prompts()),
				List.of(new Completions(), new DuplicateCompletion()), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("complete the prompt 'greeting'");
	}

	@Test
	void monoVoidAnswersDone() {
		List<McpAsyncTool> tools = AsyncMcpAnnotationProviders.modernTools(List.of(new Tools()));
		assertThat(tool(tools, "fire").definition().outputSchema()).isNull();
		Map<String, Object> result = result(send(server(), McpSchema.METHOD_TOOLS_CALL, params("name", "fire")));
		assertThat(firstText(result)).contains("Done");
	}

	@Test
	@SuppressWarnings("unchecked")
	void outputSchemaComesFromTheMonoElementType() {
		List<McpAsyncTool> tools = AsyncMcpAnnotationProviders.modernTools(List.of(new Tools()));
		Map<String, Object> schema = tool(tools, "structured").definition().outputSchema();
		assertThat((Map<String, Object>) schema.get("properties")).containsKeys("x", "y");
		assertThat(tool(tools, "greet").definition().outputSchema()).isNull();

		Map<String, Object> result = result(send(server(), McpSchema.METHOD_TOOLS_CALL, params("name", "structured")));
		assertThat((Map<String, Object>) result.get("structuredContent")).containsEntry("x", 1).containsEntry("y", 2);
	}

	@Test
	void legacyResultIsConverted() {
		Map<String, Object> result = result(send(server(), McpSchema.METHOD_TOOLS_CALL, params("name", "legacy")));
		assertThat(firstText(result)).isEqualTo("legacy");
		assertThat(result.get("resultType")).isEqualTo("complete");
	}

	@Test
	void errorSignalBecomesErrorResult() {
		Map<String, Object> result = result(send(server(), McpSchema.METHOD_TOOLS_CALL, params("name", "boom")));
		assertThat(result.get("isError")).isEqualTo(true);
		assertThat(firstText(result)).contains("boom");
	}

	@Test
	void thrownExceptionBecomesErrorResult() {
		Map<String, Object> result = result(send(server(), McpSchema.METHOD_TOOLS_CALL, params("name", "boom_sync")));
		assertThat(result.get("isError")).isEqualTo(true);
		assertThat(firstText(result)).contains("boom");
	}

	@Test
	void legacyProtocolErrorIsTranslated() {
		McpTransportResponse response = send(server(), McpSchema.METHOD_TOOLS_CALL, params("name", "protocol_error"));
		assertThat(response).isInstanceOf(McpTransportResponse.Error.class);
		JSONRPCResponse error = ((McpTransportResponse.Error) response).response();
		assertThat(error.error().code()).isEqualTo(-32602);
		assertThat(error.error().message()).isEqualTo("bad arg");
	}

	@Test
	@SuppressWarnings("unchecked")
	void notifierParameterMakesTheCallStreamingWithProgress() {
		Map<String, Object> params = params("name", "progress", "arguments", Map.of("steps", 2));
		((Map<String, Object>) params.get("_meta")).put(MetaKeys.PROGRESS_TOKEN, "tok");
		McpTransportResponse response = send(server(), McpSchema.METHOD_TOOLS_CALL, params);

		assertThat(response).isInstanceOf(McpTransportResponse.Streaming.class);
		List<JSONRPCMessage> messages = ((McpTransportResponse.Streaming) response).messages().collectList().block();
		assertThat(messages).hasSize(3);
		assertThat(((JSONRPCNotification) messages.get(0)).method()).isEqualTo(McpSchema.METHOD_NOTIFICATION_PROGRESS);
		assertThat(((JSONRPCNotification) messages.get(1)).method()).isEqualTo(McpSchema.METHOD_NOTIFICATION_PROGRESS);
		Map<String, Object> result = (Map<String, Object>) ((JSONRPCResponse) messages.get(2)).result();
		assertThat(firstText(result)).isEqualTo("done:2:" + McpSchema.LATEST_PROTOCOL_VERSION);
	}

	@Test
	@SuppressWarnings("unchecked")
	void inputRequiredRoundTrip() {
		McpServer server = server();
		McpTransportResponse first = send(server, McpSchema.METHOD_TOOLS_CALL, params("name", "ask_name"));
		assertThat(first).isInstanceOf(McpTransportResponse.Result.class);
		Map<String, Object> inputRequired = result(first);
		assertThat(inputRequired.get("resultType")).isEqualTo("input_required");
		assertThat((Map<String, Object>) inputRequired.get("inputRequests")).containsKey("user");
		String sealedState = (String) inputRequired.get("requestState");

		Map<String, Object> retry = params("name", "ask_name", "requestState", sealedState, "inputResponses",
				Map.of("user", Map.of("action", "accept", "content", Map.of("name", "Ann"))));
		assertThat(firstText(result(send(server, McpSchema.METHOD_TOOLS_CALL, retry)))).isEqualTo("Hi Ann state=asked");
	}

	@Test
	void modernRequestParameterIsInjected() {
		Map<String, Object> result = result(send(server(), McpSchema.METHOD_TOOLS_CALL, params("name", "raw_request")));
		assertThat(firstText(result)).isEqualTo("state=null");
	}

	@Test
	@SuppressWarnings("unchecked")
	void inputRequiredWithoutClientCapabilityIsRejected() {
		Map<String, Object> params = params("name", "probe");
		((Map<String, Object>) params.get("_meta")).put(MetaKeys.CLIENT_CAPABILITIES, Map.of());
		JSONRPCResponse error = finalResponse(send(server(), McpSchema.METHOD_TOOLS_CALL, params));

		assertThat(error.error()).isNotNull();
		assertThat(error.error().code()).isEqualTo(McpSchema.ErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY);
		Map<String, Object> data = McpJsonDefaults.getMapper().convertValue(error.error().data(), Map.class);
		assertThat((Map<String, Object>) data.get("requiredCapabilities")).containsOnlyKeys("sampling");
	}

	@Test
	@SuppressWarnings("unchecked")
	void resourcesArePublished() {
		McpServer server = server();
		Map<String, Object> read = result(
				send(server, McpSchema.METHOD_RESOURCES_READ, params("uri", "test://static")));
		List<Map<String, Object>> contents = (List<Map<String, Object>>) read.get("contents");
		assertThat(contents.get(0).get("text")).isEqualTo("static-body");

		read = result(send(server, McpSchema.METHOD_RESOURCES_READ, params("uri", "test://items/7")));
		contents = (List<Map<String, Object>>) read.get("contents");
		assertThat(contents.get(0).get("text")).isEqualTo("item-7");

		Map<String, Object> list = result(send(server, McpSchema.METHOD_RESOURCES_LIST, params()));
		assertThat((List<?>) list.get("resources")).hasSize(5);
		Map<String, Object> templates = result(send(server, McpSchema.METHOD_RESOURCES_TEMPLATES_LIST, params()));
		assertThat((List<?>) templates.get("resourceTemplates")).hasSize(1);
	}

	@Test
	@SuppressWarnings("unchecked")
	void annotationCacheHintsAreAppliedToConvertedLegacyResults() {
		McpServer server = server();
		Map<String, Object> hinted = result(
				send(server, McpSchema.METHOD_RESOURCES_READ, params("uri", "test://hinted")));
		assertThat(hinted.get("ttlMs")).isEqualTo(30000L);
		assertThat(hinted.get("cacheScope")).isEqualTo("public");
		assertThat(((List<Map<String, Object>>) hinted.get("contents")).get(0).get("text")).isEqualTo("hinted-body");

		Map<String, Object> plain = result(
				send(server, McpSchema.METHOD_RESOURCES_READ, params("uri", "test://static")));
		assertThat(plain.get("ttlMs")).isEqualTo(0L);
		assertThat(plain.get("cacheScope")).isEqualTo("private");

		AsyncModernMcpResourceMethodCallback callback = (AsyncModernMcpResourceMethodCallback) AsyncMcpAnnotationProviders
			.modernResources(List.of(new Resources()))
			.stream()
			.filter(spec -> spec.resource().uri().equals("test://hinted"))
			.findFirst()
			.orElseThrow()
			.handler()
			.handler();
		assertThat(callback.ttlMs()).isEqualTo(30000L);
		assertThat(callback.cacheScope()).isEqualTo(CacheScope.PUBLIC);
	}

	@Test
	@SuppressWarnings("unchecked")
	void resourceNotifierParameterStreamsProgressAndKeepsCacheHints() {
		List<AsyncResourceRegistration> specs = AsyncMcpAnnotationProviders.modernResources(List.of(new Resources()));
		AsyncResourceRegistration cached = specs.stream()
			.filter(spec -> spec.resource().uri().equals("test://cached"))
			.findFirst()
			.orElseThrow();
		assertThat(((AsyncModernMcpResourceMethodCallback) cached.handler().handler()).isStreaming()).isTrue();

		Map<String, Object> params = params("uri", "test://cached");
		((Map<String, Object>) params.get("_meta")).put(MetaKeys.PROGRESS_TOKEN, "tok");
		McpTransportResponse response = send(server(), McpSchema.METHOD_RESOURCES_READ, params);

		assertThat(response).isInstanceOf(McpTransportResponse.Streaming.class);
		List<JSONRPCMessage> messages = ((McpTransportResponse.Streaming) response).messages().collectList().block();
		assertThat(messages).hasSize(2);
		assertThat(((JSONRPCNotification) messages.get(0)).method()).isEqualTo(McpSchema.METHOD_NOTIFICATION_PROGRESS);
		Map<String, Object> result = (Map<String, Object>) ((JSONRPCResponse) messages.get(1)).result();
		assertThat(result.get("ttlMs")).isEqualTo(5000L);
		assertThat(result.get("cacheScope")).isEqualTo("public");
		List<Map<String, Object>> contents = (List<Map<String, Object>>) result.get("contents");
		assertThat(contents.get(0).get("text")).isEqualTo("cached-body");
	}

	@Test
	@SuppressWarnings("unchecked")
	void resourceInputRequiredRoundTrip() {
		McpServer server = server();
		Map<String, Object> first = result(
				send(server, McpSchema.METHOD_RESOURCES_READ, params("uri", "test://gated")));
		assertThat(first.get("resultType")).isEqualTo("input_required");
		String sealedState = (String) first.get("requestState");

		Map<String, Object> second = result(send(server, McpSchema.METHOD_RESOURCES_READ, params("uri", "test://gated",
				"requestState", sealedState, "inputResponses", Map.of("pin", Map.of("action", "accept")))));
		List<Map<String, Object>> contents = (List<Map<String, Object>>) second.get("contents");
		assertThat(contents.get(0).get("text")).isEqualTo("gated-body state=pin-asked");
	}

	@Test
	void resourceErrorsAreTranslated() {
		McpTransportResponse response = send(server(), McpSchema.METHOD_RESOURCES_READ, params("uri", "test://broken"));
		assertThat(response).isInstanceOf(McpTransportResponse.Error.class);
		JSONRPCResponse error = ((McpTransportResponse.Error) response).response();
		assertThat(error.error().code()).isEqualTo(-32603);
		assertThat(error.error().message()).isEqualTo("Internal error").doesNotContain("broken");
	}

	@Test
	@SuppressWarnings("unchecked")
	void promptsAndCompletionsArePublished() {
		McpServer server = server();
		Map<String, Object> prompt = result(send(server, McpSchema.METHOD_PROMPTS_GET,
				params("name", "greeting", "arguments", Map.of("name", "Zoe"))));
		List<Map<String, Object>> messages = (List<Map<String, Object>>) prompt.get("messages");
		assertThat(((Map<String, Object>) messages.get(0).get("content")).get("text")).isEqualTo("Hello Zoe");

		Map<String, Object> completion = result(send(server, McpSchema.METHOD_COMPLETION_COMPLETE, params("ref",
				Map.of("type", "ref/prompt", "name", "greeting"), "argument", Map.of("name", "name", "value", "Z"))));
		Map<String, Object> values = (Map<String, Object>) completion.get("completion");
		assertThat((List<String>) values.get("values")).containsExactly("Z1", "Z2");
	}

	@Test
	@SuppressWarnings("unchecked")
	void promptModernRequestAndOutcomePassThrough() {
		McpServer server = server();
		Map<String, Object> first = result(send(server, McpSchema.METHOD_PROMPTS_GET,
				params("name", "confirm", "arguments", Map.of("topic", "x"))));
		assertThat(first.get("resultType")).isEqualTo("input_required");
		String sealedState = (String) first.get("requestState");

		Map<String, Object> second = result(
				send(server, McpSchema.METHOD_PROMPTS_GET, params("name", "confirm", "arguments", Map.of("topic", "x"),
						"requestState", sealedState, "inputResponses", Map.of("ok", Map.of("action", "accept")))));
		List<Map<String, Object>> messages = (List<Map<String, Object>>) second.get("messages");
		assertThat(((Map<String, Object>) messages.get(0).get("content")).get("text"))
			.isEqualTo("confirmed x state=confirm-asked");
	}

	@Test
	@SuppressWarnings("unchecked")
	void completionSeesModernContextAndReturnsModernResult() {
		Map<String, Object> completion = result(send(server(), McpSchema.METHOD_COMPLETION_COMPLETE, params("ref",
				Map.of("type", "ref/prompt", "name", "confirm"), "argument", Map.of("name", "topic", "value", "a"))));
		Map<String, Object> values = (Map<String, Object>) completion.get("completion");
		assertThat((List<String>) values.get("values")).containsExactly("a:" + McpSchema.LATEST_PROTOCOL_VERSION,
				"a:topic");
	}

	@Test
	@SuppressWarnings("unchecked")
	void requiredCapabilitiesRefuseTheCallBeforeTheMethodRuns() {
		Gated bean = new Gated();
		McpServer server = server(bean);
		Map<String, Object> params = params("name", "gated_summary");
		((Map<String, Object>) params.get("_meta")).put(MetaKeys.CLIENT_CAPABILITIES, Map.of());
		McpTransportResponse response = send(server, McpSchema.METHOD_TOOLS_CALL, params);

		assertThat(response).isInstanceOf(McpTransportResponse.Error.class);
		JSONRPCResponse error = ((McpTransportResponse.Error) response).response();
		assertThat(error.error().code()).isEqualTo(McpSchema.ErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY);
		assertThat(bean.calls).hasValue(0);

		Map<String, Object> result = result(send(server, McpSchema.METHOD_TOOLS_CALL, params("name", "gated_summary")));
		assertThat(result.get("resultType")).isEqualTo("input_required");
		assertThat(bean.calls).hasValue(1);
	}

	@Test
	void requiredCapabilitiesAreDeclaredOnEveryPrimitiveAndTheOutcomeTypeIsReadThroughTheMono() {
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		Logger logger = (Logger) LoggerFactory.getLogger(ModernMethodSupport.class);
		logger.addAppender(appender);
		try {
			Gated bean = new Gated();
			var both = tool(AsyncMcpAnnotationProviders.modernTools(List.of(bean)), "gated_both");
			assertThat(both.requiredCapabilities().supportsElicitationForm()).isTrue();
			assertThat(both.requiredCapabilities().supportsSampling()).isTrue();
			assertThat(AsyncMcpAnnotationProviders.modernPrompts(List.of(bean)).get(0).requiredCapabilities())
				.satisfies(required -> assertThat(required.supportsElicitationForm()).isTrue());
			assertThat(
					AsyncMcpAnnotationProviders.modernResources(List.of(bean)).get(0).handler().requiredCapabilities())
				.satisfies(required -> assertThat(required.supportsRoots()).isTrue());
			assertThat(AsyncMcpAnnotationProviders.modernResourceTemplates(List.of(bean))
				.get(0)
				.handler()
				.requiredCapabilities()).satisfies(required -> assertThat(required.supportsElicitationUrl()).isTrue());
			assertThat(AsyncMcpAnnotationProviders.modernTools(List.of(new Tools())))
				.allSatisfy(tool -> assertThat(tool.requiredCapabilities()).isEqualTo(ClientCapabilities.NONE));

			// Mono<CallToolOutcome> can carry an InputRequiredResult: no warning.
			assertThat(appender.list).noneSatisfy(
					event -> assertThat(event.getFormattedMessage()).contains("cannot carry an InputRequiredResult"));
		}
		finally {
			logger.detachAppender(appender);
		}
	}

	@Test
	void legacyContextParametersAreSkippedByProvidersAndRejectedByCallbacks() throws Exception {
		assertThat(AsyncMcpAnnotationProviders.modernTools(List.of(new ModernAnnotationProvidersTests.LegacyTools())))
			.isEmpty();
		assertThat(AsyncMcpAnnotationProviders
			.modernResources(List.of(new ModernAnnotationProvidersTests.LegacyResources()))).isEmpty();
		assertThat(
				AsyncMcpAnnotationProviders.modernPrompts(List.of(new ModernAnnotationProvidersTests.LegacyPrompts())))
			.isEmpty();
		assertThat(AsyncMcpAnnotationProviders.modernPrompts(List.of(),
				List.of(new ModernAnnotationProvidersTests.LegacyCompletions()), null))
			.isEmpty();

		assertThatThrownBy(() -> new AsyncModernMcpToolMethodCallback(ReturnMode.TEXT,
				ModernAnnotationProvidersTests.LegacyTools.class.getDeclaredMethod("legacy",
						McpSyncRequestContext.class),
				new ModernAnnotationProvidersTests.LegacyTools()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("McpModernRequestContext");
	}

	@Test
	void theNotifierOfTheOtherModelIsSkippedByProvidersAndRejectedByCallbacks() throws Exception {
		// Each provider serves the notifier of its own programming model.
		assertThat(AsyncMcpAnnotationProviders.modernTools(List.of(new SyncNotifierTools()))).isEmpty();
		assertThat(SyncMcpAnnotationProviders.modernTools(List.of(new AsyncNotifierTools()))).isEmpty();
		assertThat(AsyncMcpAnnotationProviders.modernResources(List.of(new SyncNotifierTools()))).isEmpty();
		assertThat(SyncMcpAnnotationProviders.modernResources(List.of(new AsyncNotifierTools()))).isEmpty();

		assertThatThrownBy(() -> new AsyncModernMcpToolMethodCallback(ReturnMode.TEXT,
				SyncNotifierTools.class.getDeclaredMethod("sync", McpSyncNotifier.class), new SyncNotifierTools()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("use McpAsyncNotifier instead");
		assertThatThrownBy(() -> new SyncModernMcpToolMethodCallback(ReturnMode.TEXT,
				AsyncNotifierTools.class.getDeclaredMethod("async", McpAsyncNotifier.class), new AsyncNotifierTools()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("use McpSyncNotifier instead");
		assertThatThrownBy(() -> AsyncModernMcpResourceMethodCallback.builder()
			.method(SyncNotifierTools.class.getDeclaredMethod("read", McpSyncNotifier.class))
			.bean(new SyncNotifierTools())
			.uri("test://sync")
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("use McpAsyncNotifier instead");
	}

	@SuppressWarnings("unused")
	static class Tools {

		@McpTool(name = "greet", description = "Greets")
		Mono<String> greet(@McpToolParam(description = "name") String name) {
			return Mono.just("Hello " + name);
		}

		@McpTool(name = "reactive_thread", description = "Thread a reactive method runs on")
		Mono<String> reactiveThread() {
			return Mono.fromSupplier(() -> Thread.currentThread().getName());
		}

		@McpTool(name = "plain", description = "Thread a blocking method runs on")
		String plain() {
			return Thread.currentThread().getName();
		}

		@McpTool(name = "fire", description = "Fire and forget")
		Mono<Void> fire() {
			return Mono.empty();
		}

		@McpTool(name = "structured", description = "Structured output", generateOutputSchema = true)
		Mono<Point> structured() {
			return Mono.just(new Point(1, 2));
		}

		@McpTool(name = "progress", description = "Reports progress")
		Mono<String> progress(McpAsyncNotifier notifier, McpModernRequestContext ctx,
				@McpToolParam(description = "steps") int steps) {
			return Flux.range(1, steps)
				.concatMap(i -> notifier.progress(i, (double) steps, null))
				.then(Mono.fromSupplier(() -> "done:" + steps + ":" + ctx.protocolVersion()));
		}

		@McpTool(name = "ask_name", description = "MRTR")
		Mono<CallToolOutcome> askName(McpModernRequestContext ctx) {
			Map<?, ?> answer = ctx.inputResponse("user", Map.class);
			if (answer == null) {
				return Mono.just(InputRequiredResult.builder()
					.elicit("user", ElicitFormRequest.builder("Your name?", Map.of("type", "object")).build())
					.requestState("asked")
					.build());
			}
			Map<?, ?> content = (Map<?, ?>) answer.get("content");
			return Mono.just(McpSchema.CallToolResult.builder()
				.addContent(TextContent.builder("Hi " + content.get("name") + " state=" + ctx.requestState()).build())
				.build());
		}

		@McpTool(name = "probe", description = "Asks for sampling without declaring it")
		Mono<CallToolOutcome> probe() {
			return Mono.just(InputRequiredResult.builder()
				.createMessage("k",
						CreateMessageRequest
							.builder(List.of(new SamplingMessage(Role.USER, TextContent.builder("m").build())), 10)
							.build())
				.build());
		}

		@McpTool(name = "legacy", description = "Returns a legacy result")
		Mono<io.modelcontextprotocol.spec.McpSchema.CallToolResult> legacy() {
			return Mono
				.just(io.modelcontextprotocol.spec.McpSchema.CallToolResult.builder().addTextContent("legacy").build());
		}

		@McpTool(name = "boom", description = "Fails in the stream")
		Mono<String> boom() {
			return Mono.error(new IllegalStateException("boom"));
		}

		@McpTool(name = "boom_sync", description = "Fails before the stream")
		String boomSync() {
			throw new IllegalStateException("boom");
		}

		@McpTool(name = "protocol_error", description = "Protocol error")
		Mono<String> protocolError() {
			return Mono.error(McpError.builder(-32602).message("bad arg").build());
		}

		@McpTool(name = "raw_request", description = "Sees the modern request")
		Mono<String> rawRequest(CallToolRequest request) {
			return Mono.just("state=" + request.requestState());
		}

	}

	record Point(int x, int y) {
	}

	@SuppressWarnings("unused")
	static class Resources {

		@McpResource(uri = "test://static", name = "static", description = "static")
		Mono<String> readStatic() {
			return Mono.just("static-body");
		}

		@McpResource(uri = "test://items/{id}", name = "item", description = "item")
		Mono<String> readItem(String id) {
			return Mono.just("item-" + id);
		}

		// A plain method on the async server runs through fromBlocking.
		@McpResource(uri = "test://hinted", name = "hinted", description = "hinted", ttlMs = 30000,
				cacheScope = CacheScope.PUBLIC)
		String readHinted() {
			return "hinted-body";
		}

		@McpResource(uri = "test://cached", name = "cached", description = "cached", ttlMs = 1)
		Mono<ReadResourceResult> readCached(McpAsyncNotifier notifier, String uri) {
			return notifier.progress(50, 100.0, null)
				.then(Mono
					.just(ReadResourceResult.builder(List.of(TextResourceContents.builder(uri, "cached-body").build()))
						.ttlMs(5000)
						.cacheScope(CacheScope.PUBLIC)
						.build()));
		}

		@McpResource(uri = "test://gated", name = "gated", description = "gated")
		Mono<ReadResourceOutcome> readGated(McpModernRequestContext ctx, McpSchema.ReadResourceRequest request) {
			if (ctx.inputResponse("pin", Map.class) == null) {
				return Mono.just(InputRequiredResult.builder()
					.elicit("pin", ElicitFormRequest.builder("PIN?", Map.of("type", "object")).build())
					.requestState("pin-asked")
					.build());
			}
			return Mono.just(ReadResourceResult.builder(List
				.of(TextResourceContents.builder(request.uri(), "gated-body state=" + request.requestState()).build()))
				.build());
		}

		@McpResource(uri = "test://broken", name = "broken", description = "broken")
		Mono<String> readBroken() {
			return Mono.error(new IllegalStateException("broken"));
		}

	}

	@SuppressWarnings("unused")
	static class Gated {

		final AtomicInteger calls = new AtomicInteger();

		@McpTool(name = "gated_summary", description = "Needs sampling",
				requiredCapabilities = McpClientCapability.SAMPLING)
		Mono<CallToolOutcome> summary(McpAsyncNotifier notifier) {
			this.calls.incrementAndGet();
			return notifier.progress(1, 2.0, null)
				.then(Mono.just(InputRequiredResult.builder()
					.createMessage("k",
							CreateMessageRequest
								.builder(List.of(new SamplingMessage(Role.USER, TextContent.builder("m").build())), 10)
								.build())
					.build()));
		}

		@McpTool(name = "gated_both", description = "Needs form elicitation and sampling",
				requiredCapabilities = { McpClientCapability.ELICITATION_FORM, McpClientCapability.SAMPLING })
		Mono<CallToolOutcome> both() {
			return Mono.just(McpSchema.CallToolResult.builder().addContent(TextContent.builder("ok").build()).build());
		}

		@McpPrompt(name = "gated_prompt", description = "Needs form elicitation",
				requiredCapabilities = McpClientCapability.ELICITATION_FORM)
		Mono<GetPromptOutcome> prompt() {
			return Mono.just(McpSchema.GetPromptResult.builder(List.of()).build());
		}

		@McpResource(uri = "test://required", name = "required", description = "Needs roots",
				requiredCapabilities = McpClientCapability.ROOTS)
		Mono<ReadResourceOutcome> read(String uri) {
			return Mono
				.just(ReadResourceResult.builder(List.of(TextResourceContents.builder(uri, "x").build())).build());
		}

		@McpResource(uri = "test://required/{id}", name = "required-item", description = "Needs URL elicitation",
				requiredCapabilities = McpClientCapability.ELICITATION_URL)
		Mono<ReadResourceOutcome> readItem(String id) {
			return Mono.just(ReadResourceResult
				.builder(List.of(TextResourceContents.builder("test://required/" + id, id).build()))
				.build());
		}

	}

	@SuppressWarnings("unused")
	static class SyncNotifierTools {

		@McpTool(name = "sync", description = "sync notifier")
		String sync(McpSyncNotifier notifier) {
			return "sync";
		}

		@McpResource(uri = "test://sync", name = "sync", description = "sync notifier")
		String read(McpSyncNotifier notifier) {
			return "sync";
		}

	}

	@SuppressWarnings("unused")
	static class AsyncNotifierTools {

		@McpTool(name = "async", description = "async notifier")
		String async(McpAsyncNotifier notifier) {
			return "async";
		}

		@McpResource(uri = "test://async", name = "async", description = "async notifier")
		String read(McpAsyncNotifier notifier) {
			return "async";
		}

	}

	@SuppressWarnings({ "unused", "rawtypes" })
	static class ReturnTypes {

		final AtomicInteger emitted = new AtomicInteger();

		@McpTool(name = "mono", description = "d")
		Mono<String> mono() {
			return Mono.just("value");
		}

		@McpTool(name = "future", description = "d")
		CompletableFuture<String> future() {
			return CompletableFuture.supplyAsync(() -> "value");
		}

		@McpTool(name = "stage", description = "d")
		CompletionStage<String> stage() {
			return CompletableFuture.supplyAsync(() -> "value");
		}

		@McpTool(name = "publisher_of_mono", description = "d")
		Publisher<String> publisherOfMono() {
			return Mono.just("value");
		}

		@McpTool(name = "publisher_of_one", description = "d")
		Publisher<String> publisherOfOne() {
			return Flux.just("value");
		}

		@McpTool(name = "custom_publisher", description = "d")
		Publisher<String> customPublisher() {
			return subscriber -> Flux.just("value").subscribe(subscriber);
		}

		@McpTool(name = "flow_publisher", description = "d")
		Flow.Publisher<String> flowPublisher() {
			return JdkFlowAdapter.publisherToFlowPublisher(Mono.just("value"));
		}

		@McpTool(name = "object_of_mono", description = "d")
		Object objectOfMono() {
			return Mono.fromSupplier(() -> "value");
		}

		@McpTool(name = "object_of_future", description = "d")
		Object objectOfFuture() {
			return CompletableFuture.supplyAsync(() -> "value");
		}

		@McpTool(name = "object_of_value", description = "d")
		Object objectOfValue() {
			return "value";
		}

		@McpTool(name = "future_of_void", description = "d")
		CompletableFuture<Void> futureOfVoid() {
			return CompletableFuture.completedFuture(null);
		}

		@McpTool(name = "publisher_of_void", description = "d")
		Publisher<Void> publisherOfVoid() {
			return Mono.empty();
		}

		@McpTool(name = "publisher_of_two", description = "d")
		Publisher<String> publisherOfTwo() {
			return Flux.just("one", "two");
		}

		@McpTool(name = "object_of_two", description = "d")
		Object objectOfTwo() {
			return Flux.just("one", "two");
		}

		@McpTool(name = "unbounded_publisher", description = "d")
		Publisher<Integer> unboundedPublisher() {
			return Flux.range(1, Integer.MAX_VALUE).doOnNext(i -> this.emitted.incrementAndGet());
		}

		@McpTool(name = "failed_future", description = "d")
		CompletableFuture<String> failedFuture() {
			return CompletableFuture.failedFuture(new IllegalStateException("boom"));
		}

		@McpTool(name = "future_of_point", description = "d", generateOutputSchema = true)
		CompletableFuture<Point> futureOfPoint() {
			return CompletableFuture.completedFuture(new Point(1, 2));
		}

		@McpTool(name = "publisher_of_point", description = "d", generateOutputSchema = true)
		Publisher<Point> publisherOfPoint() {
			return Mono.just(new Point(1, 2));
		}

		@McpTool(name = "raw_mono", description = "d", generateOutputSchema = true)
		Mono rawMono() {
			return Mono.just("value");
		}

		@McpPrompt(name = "future_prompt", description = "d")
		CompletableFuture<String> futurePrompt(@McpArg(name = "topic") String topic) {
			return CompletableFuture.supplyAsync(() -> "value");
		}

		@McpPrompt(name = "stage_prompt", description = "d")
		CompletionStage<String> stagePrompt(@McpArg(name = "topic") String topic) {
			return CompletableFuture.supplyAsync(() -> "value");
		}

		@McpPrompt(name = "publisher_prompt", description = "d")
		Publisher<String> publisherPrompt(@McpArg(name = "topic") String topic) {
			return Mono.just("value");
		}

		@McpPrompt(name = "object_prompt", description = "d")
		Object objectPrompt(@McpArg(name = "topic") String topic) {
			return Mono.just("value");
		}

		@McpPrompt(name = "raw_prompt", description = "d")
		Mono rawPrompt(@McpArg(name = "topic") String topic) {
			return Mono.just("value");
		}

		@McpPrompt(name = "two_prompt", description = "d")
		Publisher<String> twoPrompt(@McpArg(name = "topic") String topic) {
			return Flux.just("one", "two");
		}

		@McpResource(uri = "test://future", name = "future", description = "d")
		CompletableFuture<String> futureResource() {
			return CompletableFuture.supplyAsync(() -> "value");
		}

		@McpResource(uri = "test://stage", name = "stage", description = "d")
		CompletionStage<String> stageResource() {
			return CompletableFuture.supplyAsync(() -> "value");
		}

		@McpResource(uri = "test://publisher", name = "publisher", description = "d")
		Publisher<String> publisherResource() {
			return Mono.just("value");
		}

		@McpResource(uri = "test://object", name = "object", description = "d")
		Object objectResource() {
			return Mono.just("value");
		}

		@McpResource(uri = "test://raw", name = "raw", description = "d")
		Mono rawResource() {
			return Mono.just("value");
		}

		@McpResource(uri = "test://two", name = "two", description = "d")
		Publisher<String> twoResource() {
			return Flux.just("one", "two");
		}

		@McpComplete(prompt = "future_prompt")
		CompletableFuture<List<String>> futureCompletion(String value) {
			return CompletableFuture.supplyAsync(() -> List.of("value"));
		}

		@McpComplete(prompt = "stage_prompt")
		CompletionStage<CompleteResult> stageCompletion(String value) {
			return CompletableFuture
				.supplyAsync(() -> new CompleteResult(new CompleteResult.Completion(List.of("value")), null, null));
		}

		@McpComplete(prompt = "publisher_prompt")
		Publisher<String> publisherCompletion(String value) {
			return Mono.just("value");
		}

		@McpComplete(prompt = "object_prompt")
		Object objectCompletion(String value) {
			return Mono.just(List.of("value"));
		}

		@McpComplete(prompt = "raw_prompt")
		Mono rawCompletion(String value) {
			return Mono.just("value");
		}

		@McpComplete(prompt = "two_prompt")
		Publisher<String> twoCompletion(String value) {
			return Flux.just("one", "two");
		}

	}

	@SuppressWarnings("unused")
	static class FluxTool {

		@McpTool(name = "flux", description = "d")
		Flux<String> flux() {
			return Flux.just("first", "second");
		}

	}

	@SuppressWarnings("unused")
	static class FluxPrompt {

		@McpPrompt(name = "flux", description = "d")
		Flux<String> flux() {
			return Flux.just("one", "two");
		}

	}

	@SuppressWarnings("unused")
	static class FluxResource {

		@McpResource(uri = "test://flux", name = "flux", description = "d")
		Flux<String> flux() {
			return Flux.just("one", "two");
		}

	}

	@SuppressWarnings("unused")
	static class FluxCompletion {

		@McpComplete(prompt = "flux")
		Flux<String> flux(String value) {
			return Flux.just(value + "1", value + "2");
		}

	}

	@SuppressWarnings("unused")
	static class Prompts {

		@McpPrompt(name = "greeting", description = "greeting")
		Mono<String> greeting(@McpArg(name = "name", required = true) String name) {
			return Mono.just("Hello " + name);
		}

		@McpPrompt(name = "confirm", description = "confirm")
		Mono<GetPromptOutcome> confirm(GetPromptRequest request,
				@McpArg(name = "topic", required = true) String topic) {
			if (request.inputResponses() == null || !request.inputResponses().containsKey("ok")) {
				return Mono.just(InputRequiredResult.builder()
					.elicit("ok", ElicitFormRequest.builder("Confirm?", Map.of("type", "object")).build())
					.requestState("confirm-asked")
					.build());
			}
			return Mono.just(
					McpSchema.GetPromptResult.builder(
							List.of(PromptMessage
								.builder(Role.USER,
										TextContent.builder("confirmed " + topic + " state=" + request.requestState())
											.build())
								.build()))
						.build());
		}

	}

	@SuppressWarnings("unused")
	static class Completions {

		@McpComplete(prompt = "greeting")
		Mono<List<String>> complete(String value) {
			return Mono.just(List.of(value + "1", value + "2"));
		}

		@McpComplete(prompt = "confirm")
		Mono<CompleteResult> completeConfirm(McpModernRequestContext ctx, CompleteRequest request) {
			return Mono.just(
					new CompleteResult(
							new CompleteResult.Completion(
									List.of(request.argument().value() + ":" + ctx.protocolVersion(),
											request.argument().value() + ":" + request.argument().name())),
							null, null));
		}

		@McpComplete(uri = "test://items/{id}")
		Mono<List<String>> completeItem(String value) {
			return Mono.just(List.of("item-" + value));
		}

	}

	@SuppressWarnings("unused")
	static class StaticResourceCompletion {

		@McpComplete(uri = "test://static")
		Mono<List<String>> complete(String value) {
			return Mono.just(List.of(value));
		}

	}

	@SuppressWarnings("unused")
	static class DuplicateCompletion {

		@McpComplete(prompt = "greeting")
		Mono<List<String>> again(String value) {
			return Mono.just(List.of(value));
		}

	}

	@SuppressWarnings("unused")
	static class SkippedTargets {

		@McpPrompt(name = "skipped_prompt", description = "d")
		Mono<String> prompt(McpSyncNotifier notifier) {
			return Mono.just("value");
		}

		@McpResource(uri = "test://skipped/{id}", name = "skipped", description = "d")
		Mono<String> resource(McpSyncNotifier notifier, String id) {
			return Mono.just(id);
		}

	}

	@SuppressWarnings("unused")
	static class SkippedTargetCompletions {

		@McpComplete(prompt = "skipped_prompt")
		Mono<List<String>> completePrompt(String value) {
			return Mono.just(List.of(value));
		}

		@McpComplete(uri = "test://skipped/{id}")
		Mono<List<String>> completeTemplate(String value) {
			return Mono.just(List.of(value));
		}

	}

}
