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
import io.modelcontextprotocol.modern.McpSchema.CompleteRequest;
import io.modelcontextprotocol.modern.McpSchema.CompleteResult;
import io.modelcontextprotocol.modern.McpSchema.CreateMessageRequest;
import io.modelcontextprotocol.modern.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.modern.McpSchema.GetPromptOutcome;
import io.modelcontextprotocol.modern.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.McpSchema.InputRequest;
import io.modelcontextprotocol.modern.McpSchema.InputRequiredResult;
import io.modelcontextprotocol.modern.McpSchema.MetaKeys;
import io.modelcontextprotocol.modern.McpSchema.PromptMessage;
import io.modelcontextprotocol.modern.McpSchema.ReadResourceOutcome;
import io.modelcontextprotocol.modern.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.modern.McpSchema.Role;
import io.modelcontextprotocol.modern.McpSchema.SamplingMessage;
import io.modelcontextprotocol.modern.McpSchema.TextContent;
import io.modelcontextprotocol.modern.McpSchema.TextResourceContents;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.McpTransportResponse;
import io.modelcontextprotocol.modern.server.feature.CompletionsFeature;
import io.modelcontextprotocol.modern.server.feature.PromptsFeature;
import io.modelcontextprotocol.modern.server.feature.ResourcesFeature;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import io.modelcontextprotocol.spec.McpError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpModernSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.annotation.method.resource.SyncModernMcpResourceMethodCallback;
import org.springframework.ai.mcp.annotation.method.tool.ReturnMode;
import org.springframework.ai.mcp.annotation.method.tool.SyncModernMcpToolMethodCallback;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.ai.mcp.modern.InMemorySyncCompletionRepository;
import org.springframework.ai.mcp.modern.InMemorySyncPromptRepository;
import org.springframework.ai.mcp.modern.InMemorySyncResourceRepository;
import org.springframework.ai.mcp.modern.InMemorySyncToolRepository;
import org.springframework.ai.mcp.modern.SyncResourceSpecification;
import org.springframework.ai.mcp.modern.SyncToolSpecification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Serves annotated beans through a real modern {@link McpServer} built from the in-memory
 * repositories the modern providers feed.
 *
 * @author Christian Tzolov
 */
class ModernAnnotationProvidersTests {

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
		return McpServer.builder()
			.serverInfo(Implementation.builder("test", "1").build())
			.feature(ToolsFeature.ofSync(InMemorySyncToolRepository.builder()
				.tools(SyncMcpAnnotationProviders.modernToolSpecifications(List.of(new Tools())))
				.build()))
			.feature(ResourcesFeature.ofSync(InMemorySyncResourceRepository.builder()
				.resources(SyncMcpAnnotationProviders.modernResourceSpecifications(List.of(new Resources())))
				.resourceTemplates(
						SyncMcpAnnotationProviders.modernResourceTemplateSpecifications(List.of(new Resources())))
				.build()))
			.feature(PromptsFeature.ofSync(InMemorySyncPromptRepository.builder()
				.prompts(SyncMcpAnnotationProviders.modernPromptSpecifications(List.of(new Prompts())))
				.build()))
			.feature(CompletionsFeature.ofSync(InMemorySyncCompletionRepository.builder()
				.completions(SyncMcpAnnotationProviders.modernCompleteSpecifications(List.of(new Completions())))
				.build()))
			.build();
	}

	private static McpTransportResponse send(McpServer server, String method, Map<String, Object> params) {
		return server.handleBlocking(McpTransportContext.EMPTY, new JSONRPCRequest(method, 1, params)).block();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> result(McpTransportResponse response) {
		if (response instanceof McpTransportResponse.Result result) {
			return (Map<String, Object>) result.response().result();
		}
		if (response instanceof McpTransportResponse.Streaming streaming) {
			// The response is the last message of the stream.
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

	@Test
	@SuppressWarnings("unchecked")
	void toolSchemaExcludesInfrastructureParameters() {
		List<SyncToolSpecification> specs = SyncMcpAnnotationProviders.modernToolSpecifications(List.of(new Tools()));
		SyncToolSpecification progress = specs.stream()
			.filter(spec -> spec.tool().name().equals("progress"))
			.findFirst()
			.orElseThrow();
		Map<String, Object> properties = (Map<String, Object>) progress.tool().inputSchema().get("properties");
		assertThat(properties).containsOnlyKeys("steps");
		assertThat(((SyncModernMcpToolMethodCallback) progress.handler()).isStreaming()).isTrue();

		SyncToolSpecification greet = specs.stream()
			.filter(spec -> spec.tool().name().equals("greet"))
			.findFirst()
			.orElseThrow();
		assertThat(((SyncModernMcpToolMethodCallback) greet.handler()).isStreaming()).isFalse();
	}

	@Test
	void simpleToolReturnsText() {
		McpServer server = server();
		Map<String, Object> result = result(
				send(server, McpSchema.METHOD_TOOLS_CALL, params("name", "greet", "arguments", Map.of("name", "Bob"))));
		assertThat(firstText(result)).isEqualTo("Hello Bob");
	}

	@Test
	void legacyResultIsConverted() {
		Map<String, Object> result = result(send(server(), McpSchema.METHOD_TOOLS_CALL, params("name", "legacy")));
		assertThat(firstText(result)).isEqualTo("legacy");
		assertThat(result.get("resultType")).isEqualTo("complete");
	}

	@Test
	void runtimeExceptionBecomesErrorResult() {
		Map<String, Object> result = result(send(server(), McpSchema.METHOD_TOOLS_CALL, params("name", "boom")));
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
	void contextParameterMakesTheCallStreamingWithProgress() {
		Map<String, Object> params = params("name", "progress", "arguments", Map.of("steps", 2));
		((Map<String, Object>) params.get("_meta")).put(MetaKeys.PROGRESS_TOKEN, "tok");
		McpTransportResponse response = send(server(), McpSchema.METHOD_TOOLS_CALL, params);

		assertThat(response).isInstanceOf(McpTransportResponse.Streaming.class);
		List<JSONRPCMessage> messages = ((McpTransportResponse.Streaming) response).messages().collectList().block();
		assertThat(messages).hasSize(3);
		assertThat(messages.get(0)).isInstanceOf(JSONRPCNotification.class);
		assertThat(((JSONRPCNotification) messages.get(0)).method()).isEqualTo(McpSchema.METHOD_NOTIFICATION_PROGRESS);
		assertThat(messages.get(2)).isInstanceOf(JSONRPCResponse.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> result = (Map<String, Object>) ((JSONRPCResponse) messages.get(2)).result();
		assertThat(firstText(result)).isEqualTo("done:2:" + McpSchema.LATEST_PROTOCOL_VERSION);
	}

	@Test
	@SuppressWarnings("unchecked")
	void inputRequiredRoundTrip() {
		McpServer server = server();
		Map<String, Object> params = params("name", "ask_name");
		((Map<String, Object>) params.get("_meta")).put(MetaKeys.PROGRESS_TOKEN, "tok");

		McpTransportResponse first = send(server, McpSchema.METHOD_TOOLS_CALL, params);
		List<JSONRPCMessage> messages = ((McpTransportResponse.Streaming) first).messages().collectList().block();
		Map<String, Object> inputRequired = (Map<String, Object>) ((JSONRPCResponse) messages.get(0)).result();
		assertThat(inputRequired.get("resultType")).isEqualTo("input_required");
		assertThat((Map<String, Object>) inputRequired.get("inputRequests")).containsKey("user");
		String sealedState = (String) inputRequired.get("requestState");
		assertThat(sealedState).isNotBlank();

		Map<String, Object> retry = params("name", "ask_name", "requestState", sealedState, "inputResponses",
				Map.of("user", Map.of("action", "accept", "content", Map.of("name", "Ann"))));
		((Map<String, Object>) retry.get("_meta")).put(MetaKeys.PROGRESS_TOKEN, "tok");
		McpTransportResponse second = send(server, McpSchema.METHOD_TOOLS_CALL, retry);
		messages = ((McpTransportResponse.Streaming) second).messages().collectList().block();
		Map<String, Object> result = (Map<String, Object>) ((JSONRPCResponse) messages.get(0)).result();
		assertThat(firstText(result)).isEqualTo("Hi Ann state=asked");
	}

	@Test
	void modernRequestParameterIsInjected() {
		Map<String, Object> result = result(send(server(), McpSchema.METHOD_TOOLS_CALL, params("name", "raw_request")));
		assertThat(firstText(result)).isEqualTo("state=null");
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
	void annotationCacheHintsAreAppliedToConvertedLegacyResults() {
		McpServer server = server();
		Map<String, Object> hinted = result(
				send(server, McpSchema.METHOD_RESOURCES_READ, params("uri", "test://hinted")));
		assertThat(hinted.get("ttlMs")).isEqualTo(30000L);
		assertThat(hinted.get("cacheScope")).isEqualTo("public");
		assertThat(((List<Map<String, Object>>) hinted.get("contents")).get(0).get("text")).isEqualTo("hinted-body");

		// Defaults: not cacheable, private.
		Map<String, Object> plain = result(
				send(server, McpSchema.METHOD_RESOURCES_READ, params("uri", "test://static")));
		assertThat(plain.get("ttlMs")).isEqualTo(0L);
		assertThat(plain.get("cacheScope")).isEqualTo("private");

		SyncModernMcpResourceMethodCallback callback = (SyncModernMcpResourceMethodCallback) SyncMcpAnnotationProviders
			.modernResourceSpecifications(List.of(new Resources()))
			.stream()
			.filter(spec -> spec.resource().uri().equals("test://hinted"))
			.findFirst()
			.orElseThrow()
			.handler();
		assertThat(callback.ttlMs()).isEqualTo(30000L);
		assertThat(callback.cacheScope()).isEqualTo(CacheScope.PUBLIC);
	}

	@Test
	@SuppressWarnings("unchecked")
	void promptListOmitsInjectedModernParameters() {
		Map<String, Object> list = result(send(server(), McpSchema.METHOD_PROMPTS_LIST, params()));
		List<Map<String, Object>> prompts = (List<Map<String, Object>>) list.get("prompts");
		Map<String, Object> confirm = prompts.stream()
			.filter(prompt -> "confirm".equals(prompt.get("name")))
			.findFirst()
			.orElseThrow();
		assertThat((List<Map<String, Object>>) confirm.get("arguments")).extracting(argument -> argument.get("name"))
			.containsExactly("topic");
	}

	@Test
	@SuppressWarnings("unchecked")
	void resourceContextParameterStreamsProgressAndKeepsCacheHints() {
		List<SyncResourceSpecification> specs = SyncMcpAnnotationProviders
			.modernResourceSpecifications(List.of(new Resources()));
		SyncResourceSpecification cached = specs.stream()
			.filter(spec -> spec.resource().uri().equals("test://cached"))
			.findFirst()
			.orElseThrow();
		assertThat(((SyncModernMcpResourceMethodCallback) cached.handler()).isStreaming()).isTrue();

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
		// 2026-07-28: results of MRTR retries must not be cached, whatever the handler
		// set. The SDK applies this in McpServer.resultResponse; the test guards it.
		assertThat(second.get("ttlMs")).isEqualTo(0L);
		assertThat(second.get("cacheScope")).isEqualTo("private");
	}

	@Test
	@SuppressWarnings("unchecked")
	void inputRequiredWithoutClientCapabilityIsRejected() {
		Map<String, Object> params = params("uri", "test://gated");
		((Map<String, Object>) params.get("_meta")).put(MetaKeys.CLIENT_CAPABILITIES, Map.of());
		McpTransportResponse response = send(server(), McpSchema.METHOD_RESOURCES_READ, params);

		// The handler declares the modern context, so the answer is a stream whose last
		// message carries the error.
		JSONRPCResponse error = finalResponse(response);
		assertThat(error.error()).isNotNull();
		assertThat(error.error().code()).isEqualTo(McpSchema.ErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY);
		Map<String, Object> data = McpJsonDefaults.getMapper().convertValue(error.error().data(), Map.class);
		Map<String, Object> required = (Map<String, Object>) data.get("requiredCapabilities");
		assertThat((Map<String, Object>) required.get("elicitation")).containsKey("form");
	}

	@ParameterizedTest
	@CsvSource({ "form, elicitation, form", "url, elicitation, url", "url-map, elicitation, url", "sampling, sampling,",
			"roots, roots," })
	@SuppressWarnings("unchecked")
	void everyInputRequestKindIsCheckedAgainstClientCapabilities(String kind, String capability, String subCapability) {
		Map<String, Object> params = params("name", "probe", "arguments", Map.of("kind", kind));
		((Map<String, Object>) params.get("_meta")).put(MetaKeys.CLIENT_CAPABILITIES, Map.of());
		JSONRPCResponse error = finalResponse(send(server(), McpSchema.METHOD_TOOLS_CALL, params));

		assertThat(error.error()).isNotNull();
		assertThat(error.error().code()).isEqualTo(McpSchema.ErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY);
		Map<String, Object> data = McpJsonDefaults.getMapper().convertValue(error.error().data(), Map.class);
		Map<String, Object> required = (Map<String, Object>) data.get("requiredCapabilities");
		assertThat(required).containsOnlyKeys(capability);
		if (subCapability != null) {
			assertThat((Map<String, Object>) required.get(capability)).containsOnlyKeys(subCapability);
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	void inputRequestsPassWhenTheClientDeclaresTheCapability() {
		Map<String, Object> params = params("name", "probe", "arguments", Map.of("kind", "url-map"));
		((Map<String, Object>) params.get("_meta")).put(MetaKeys.CLIENT_CAPABILITIES,
				Map.of("elicitation", Map.of("url", Map.of())));
		Map<String, Object> result = result(send(server(), McpSchema.METHOD_TOOLS_CALL, params));
		assertThat(result.get("resultType")).isEqualTo("input_required");
	}

	@Test
	void resourceErrorsAreTranslated() {
		McpTransportResponse response = send(server(), McpSchema.METHOD_RESOURCES_READ, params("uri", "test://broken"));
		assertThat(response).isInstanceOf(McpTransportResponse.Error.class);
		JSONRPCResponse error = ((McpTransportResponse.Error) response).response();
		// Unexpected handler failures are internal errors; the cause stays server-side.
		assertThat(error.error().code()).isEqualTo(-32603);
		assertThat(error.error().message()).isEqualTo("Internal error").doesNotContain("broken");
		assertThat(error.error().data()).isNull();
	}

	@Test
	void legacyContextParametersAreSkippedByProvidersAndRejectedByCallbacks() throws Exception {
		// The providers skip session-bound methods, as the stateless providers do.
		assertThat(SyncMcpAnnotationProviders.modernToolSpecifications(List.of(new LegacyTools()))).isEmpty();
		assertThat(SyncMcpAnnotationProviders.modernResourceSpecifications(List.of(new LegacyResources()))).isEmpty();
		assertThat(SyncMcpAnnotationProviders.modernPromptSpecifications(List.of(new LegacyPrompts()))).isEmpty();
		assertThat(SyncMcpAnnotationProviders.modernCompleteSpecifications(List.of(new LegacyCompletions()))).isEmpty();

		// Built directly, the callbacks explain what to use instead.
		assertThatThrownBy(() -> new SyncModernMcpToolMethodCallback(ReturnMode.TEXT,
				LegacyTools.class.getDeclaredMethod("legacy", McpSyncRequestContext.class), new LegacyTools()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("McpModernSyncRequestContext");
		assertThatThrownBy(() -> SyncModernMcpResourceMethodCallback.builder()
			.method(LegacyResources.class.getDeclaredMethod("read", McpSyncRequestContext.class))
			.bean(new LegacyResources())
			.uri("test://legacy")
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("McpModernSyncRequestContext");
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

	@SuppressWarnings("unused")
	static class Tools {

		@McpTool(name = "greet", description = "Greets")
		String greet(@McpToolParam(description = "name") String name) {
			return "Hello " + name;
		}

		@McpTool(name = "progress", description = "Reports progress")
		String progress(McpModernSyncRequestContext ctx, @McpToolParam(description = "steps") int steps) {
			for (int i = 1; i <= steps; i++) {
				ctx.progress(i * 100 / steps);
			}
			return "done:" + steps + ":" + ctx.protocolVersion();
		}

		@McpTool(name = "ask_name", description = "MRTR")
		CallToolOutcome askName(McpModernSyncRequestContext ctx) {
			var answer = ctx.inputResponse("user", Map.class);
			if (answer.isEmpty()) {
				return InputRequiredResult.builder()
					.elicit("user", ElicitFormRequest.builder("Your name?", Map.of("type", "object")).build())
					.requestState("asked")
					.build();
			}
			Map<?, ?> content = (Map<?, ?>) answer.get().get("content");
			return McpSchema.CallToolResult.builder()
				.addContent(TextContent.builder("Hi " + content.get("name") + " state=" + ctx.requestState()).build())
				.build();
		}

		@McpTool(name = "probe", description = "Returns one input request of the given kind")
		CallToolOutcome probe(@McpToolParam(description = "kind") String kind) {
			return switch (kind) {
				case "form" -> InputRequiredResult.builder()
					.elicit("k", ElicitFormRequest.builder("m", Map.of("type", "object")).build())
					.build();
				case "url" -> InputRequiredResult.builder().elicitUrl("k", "m", "https://example.com").build();
				case "url-map" -> new InputRequiredResult(
						Map.of("k",
								new InputRequest(McpSchema.METHOD_ELICITATION_CREATE,
										Map.of("mode", "url", "message", "m", "url", "https://example.com"))),
						null, null, null);
				case "sampling" -> InputRequiredResult.builder()
					.createMessage("k",
							CreateMessageRequest
								.builder(List.of(new SamplingMessage(Role.USER, TextContent.builder("m").build())), 10)
								.build())
					.build();
				case "roots" -> InputRequiredResult.builder().listRoots("k").build();
				default -> throw new IllegalArgumentException(kind);
			};
		}

		@McpTool(name = "legacy", description = "Returns a legacy result")
		io.modelcontextprotocol.spec.McpSchema.CallToolResult legacy() {
			return io.modelcontextprotocol.spec.McpSchema.CallToolResult.builder().addTextContent("legacy").build();
		}

		@McpTool(name = "boom", description = "Fails")
		String boom() {
			throw new IllegalStateException("boom");
		}

		@McpTool(name = "protocol_error", description = "Protocol error")
		String protocolError() {
			throw McpError.builder(-32602).message("bad arg").build();
		}

		@McpTool(name = "raw_request", description = "Sees the modern request")
		String rawRequest(CallToolRequest request) {
			return "state=" + request.requestState();
		}

	}

	@SuppressWarnings("unused")
	static class Resources {

		@McpResource(uri = "test://static", name = "static", description = "static")
		String readStatic() {
			return "static-body";
		}

		@McpResource(uri = "test://items/{id}", name = "item", description = "item")
		String readItem(String id) {
			return "item-" + id;
		}

		@McpResource(uri = "test://hinted", name = "hinted", description = "hinted", ttlMs = 30000,
				cacheScope = CacheScope.PUBLIC)
		String readHinted() {
			return "hinted-body";
		}

		// The annotation hints are ignored because the method returns a modern result.
		@McpResource(uri = "test://cached", name = "cached", description = "cached", ttlMs = 1)
		ReadResourceResult readCached(McpModernSyncRequestContext ctx, String uri) {
			ctx.progress(50);
			return ReadResourceResult.builder(List.of(TextResourceContents.builder(uri, "cached-body").build()))
				.ttlMs(5000)
				.cacheScope(CacheScope.PUBLIC)
				.build();
		}

		@McpResource(uri = "test://gated", name = "gated", description = "gated")
		ReadResourceOutcome readGated(McpModernSyncRequestContext ctx, McpSchema.ReadResourceRequest request) {
			if (ctx.inputResponse("pin", Map.class).isEmpty()) {
				return InputRequiredResult.builder()
					.elicit("pin", ElicitFormRequest.builder("PIN?", Map.of("type", "object")).build())
					.requestState("pin-asked")
					.build();
			}
			// Cache hints on a retry result must be dropped by the callback.
			return ReadResourceResult.builder(List
				.of(TextResourceContents.builder(request.uri(), "gated-body state=" + request.requestState()).build()))
				.ttlMs(5000)
				.cacheScope(CacheScope.PUBLIC)
				.build();
		}

		@McpResource(uri = "test://broken", name = "broken", description = "broken")
		String readBroken() {
			throw new IllegalStateException("broken");
		}

	}

	@SuppressWarnings("unused")
	static class LegacyTools {

		@McpTool(name = "legacy", description = "legacy")
		String legacy(McpSyncRequestContext ctx) {
			return "legacy";
		}

	}

	@SuppressWarnings("unused")
	static class LegacyResources {

		@McpResource(uri = "test://legacy", name = "legacy", description = "legacy")
		String read(McpSyncRequestContext ctx) {
			return "legacy";
		}

	}

	@SuppressWarnings("unused")
	static class LegacyPrompts {

		@McpPrompt(name = "legacy", description = "legacy")
		String prompt(McpSyncRequestContext ctx) {
			return "legacy";
		}

	}

	@SuppressWarnings("unused")
	static class LegacyCompletions {

		@McpComplete(prompt = "legacy")
		List<String> complete(McpSyncRequestContext ctx, String value) {
			return List.of(value);
		}

	}

	@SuppressWarnings("unused")
	static class Prompts {

		@McpPrompt(name = "greeting", description = "greeting")
		String greeting(@McpArg(name = "name", required = true) String name) {
			return "Hello " + name;
		}

		@McpPrompt(name = "confirm", description = "confirm")
		GetPromptOutcome confirm(GetPromptRequest request, @McpArg(name = "topic", required = true) String topic) {
			if (request.inputResponses() == null || !request.inputResponses().containsKey("ok")) {
				return InputRequiredResult.builder()
					.elicit("ok", ElicitFormRequest.builder("Confirm?", Map.of("type", "object")).build())
					.requestState("confirm-asked")
					.build();
			}
			return McpSchema.GetPromptResult
				.builder(
						List.of(PromptMessage
							.builder(Role.USER,
									TextContent.builder("confirmed " + topic + " state=" + request.requestState())
										.build())
							.build()))
				.build();
		}

	}

	@SuppressWarnings("unused")
	static class Completions {

		@McpComplete(prompt = "greeting")
		List<String> complete(String value) {
			return List.of(value + "1", value + "2");
		}

		@McpComplete(prompt = "confirm")
		CompleteResult completeConfirm(McpModernSyncRequestContext ctx, CompleteRequest request) {
			ctx.progress(10); // dropped: completions are not streamed
			return new CompleteResult(
					new CompleteResult.Completion(List.of(request.argument().value() + ":" + ctx.protocolVersion(),
							request.argument().value() + ":" + request.argument().name())),
					null, null);
		}

	}

}
