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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
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
import io.modelcontextprotocol.modern.server.McpSyncNotifier;
import io.modelcontextprotocol.modern.server.McpTransportResponse;
import io.modelcontextprotocol.modern.server.feature.CompletionsFeature;
import io.modelcontextprotocol.modern.server.feature.McpSyncTool;
import io.modelcontextprotocol.modern.server.feature.PromptsFeature;
import io.modelcontextprotocol.modern.server.feature.ResourcesFeature;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import io.modelcontextprotocol.spec.McpError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpClientCapability;
import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.McpIcon;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.common.ModernMethodSupport;
import org.springframework.ai.mcp.annotation.context.McpModernSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.annotation.method.resource.SyncModernMcpResourceMethodCallback;
import org.springframework.ai.mcp.annotation.method.tool.ReturnMode;
import org.springframework.ai.mcp.annotation.method.tool.SyncModernMcpToolMethodCallback;
import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.ai.mcp.modern.InMemorySyncCompletionRepository;
import org.springframework.ai.mcp.modern.InMemorySyncPromptRepository;
import org.springframework.ai.mcp.modern.InMemorySyncResourceRepository;
import org.springframework.ai.mcp.modern.InMemorySyncToolRepository;
import org.springframework.ai.mcp.modern.SyncResourceEntry;

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
		return server(new Tools());
	}

	/**
	 * A server serving {@code toolBean}'s tools next to the shared resources and prompts.
	 */
	private static McpServer server(Object toolBean) {
		return McpServer.builder()
			.serverInfo(Implementation.builder("test", "1").build())
			.feature(ToolsFeature.ofSync(InMemorySyncToolRepository.builder()
				.tools(SyncMcpAnnotationProviders.modernTools(List.of(toolBean)))
				.build()))
			.feature(ResourcesFeature.ofSync(InMemorySyncResourceRepository.builder()
				.resources(SyncMcpAnnotationProviders.modernResources(List.of(new Resources())))
				.resourceTemplates(SyncMcpAnnotationProviders.modernResourceTemplates(List.of(new Resources())))
				.build()))
			.feature(PromptsFeature.ofSync(InMemorySyncPromptRepository.builder()
				.prompts(SyncMcpAnnotationProviders.modernPrompts(List.of(new Prompts())))
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
		List<McpSyncTool> specs = SyncMcpAnnotationProviders.modernTools(List.of(new Tools()));
		McpSyncTool progress = specs.stream()
			.filter(spec -> spec.definition().name().equals("progress"))
			.findFirst()
			.orElseThrow();
		Map<String, Object> properties = (Map<String, Object>) progress.definition().inputSchema().get("properties");
		assertThat(properties).containsOnlyKeys("steps");
		assertThat(((SyncModernMcpToolMethodCallback) progress.handler()).isStreaming()).isTrue();

		McpSyncTool greet = specs.stream()
			.filter(spec -> spec.definition().name().equals("greet"))
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
	void notifierParameterMakesTheCallStreamingWithProgress() {
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
	void notifierMethodStreamsWithoutProgressToken() {
		// The shape is fixed by the signature, not by the request: without a token the
		// stream carries the result alone.
		Map<String, Object> params = params("name", "progress", "arguments", Map.of("steps", 2));
		McpTransportResponse response = send(server(), McpSchema.METHOD_TOOLS_CALL, params);

		assertThat(response).isInstanceOf(McpTransportResponse.Streaming.class);
		List<JSONRPCMessage> messages = ((McpTransportResponse.Streaming) response).messages().collectList().block();
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0)).isInstanceOf(JSONRPCResponse.class);
	}

	@Test
	void contextParameterAloneDoesNotStream() {
		McpSyncTool askName = SyncMcpAnnotationProviders.modernTools(List.of(new Tools()))
			.stream()
			.filter(spec -> spec.definition().name().equals("ask_name"))
			.findFirst()
			.orElseThrow();
		assertThat(((SyncModernMcpToolMethodCallback) askName.handler()).isStreaming()).isFalse();
	}

	@Test
	@SuppressWarnings("unchecked")
	void inputRequiredRoundTrip() {
		McpServer server = server();
		Map<String, Object> params = params("name", "ask_name");
		((Map<String, Object>) params.get("_meta")).put(MetaKeys.PROGRESS_TOKEN, "tok");

		McpTransportResponse first = send(server, McpSchema.METHOD_TOOLS_CALL, params);
		// The context alone does not make the call a stream.
		assertThat(first).isInstanceOf(McpTransportResponse.Result.class);
		Map<String, Object> inputRequired = result(first);
		assertThat(inputRequired.get("resultType")).isEqualTo("input_required");
		assertThat((Map<String, Object>) inputRequired.get("inputRequests")).containsKey("user");
		String sealedState = (String) inputRequired.get("requestState");
		assertThat(sealedState).isNotBlank();

		Map<String, Object> retry = params("name", "ask_name", "requestState", sealedState, "inputResponses",
				Map.of("user", Map.of("action", "accept", "content", Map.of("name", "Ann"))));
		((Map<String, Object>) retry.get("_meta")).put(MetaKeys.PROGRESS_TOKEN, "tok");
		McpTransportResponse second = send(server, McpSchema.METHOD_TOOLS_CALL, retry);
		assertThat(second).isInstanceOf(McpTransportResponse.Result.class);
		Map<String, Object> result = result(second);
		assertThat(firstText(result)).isEqualTo("Hi Ann state=asked");
	}

	@Test
	@SuppressWarnings("unchecked")
	void inputResponsesAreConvertedWithTheConfiguredMapper() {
		AtomicInteger conversions = new AtomicInteger();
		McpJsonMapper recording = new McpJsonMapper() {
			private final McpJsonMapper delegate = McpJsonDefaults.getMapper();

			@Override
			public <T> T readValue(String content, Class<T> type) throws IOException {
				return this.delegate.readValue(content, type);
			}

			@Override
			public <T> T readValue(byte[] content, Class<T> type) throws IOException {
				return this.delegate.readValue(content, type);
			}

			@Override
			public <T> T readValue(String content, TypeRef<T> type) throws IOException {
				return this.delegate.readValue(content, type);
			}

			@Override
			public <T> T readValue(byte[] content, TypeRef<T> type) throws IOException {
				return this.delegate.readValue(content, type);
			}

			@Override
			public <T> T convertValue(Object fromValue, Class<T> type) {
				conversions.incrementAndGet();
				return this.delegate.convertValue(fromValue, type);
			}

			@Override
			public <T> T convertValue(Object fromValue, TypeRef<T> type) {
				conversions.incrementAndGet();
				return this.delegate.convertValue(fromValue, type);
			}

			@Override
			public String writeValueAsString(Object value) throws IOException {
				return this.delegate.writeValueAsString(value);
			}

			@Override
			public byte[] writeValueAsBytes(Object value) throws IOException {
				return this.delegate.writeValueAsBytes(value);
			}
		};
		McpServer server = McpServer.builder()
			.serverInfo(Implementation.builder("test", "1").build())
			.feature(ToolsFeature.ofSync(InMemorySyncToolRepository.builder()
				.tools(SyncMcpAnnotationProviders.modernTools(List.of(new Tools()), recording))
				.build()))
			.build();

		Map<String, Object> inputRequired = result(
				send(server, McpSchema.METHOD_TOOLS_CALL, params("name", "ask_name")));
		assertThat(conversions).hasValue(0);
		Map<String, Object> retry = params("name", "ask_name", "requestState", inputRequired.get("requestState"),
				"inputResponses", Map.of("user", Map.of("action", "accept", "content", Map.of("name", "Ann"))));
		assertThat(firstText(result(send(server, McpSchema.METHOD_TOOLS_CALL, retry)))).isEqualTo("Hi Ann state=asked");
		assertThat(conversions).hasPositiveValue();
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
	void resourceNotifierParameterStreamsProgressAndKeepsCacheHints() {
		List<SyncResourceEntry> specs = SyncMcpAnnotationProviders.modernResources(List.of(new Resources()));
		SyncResourceEntry cached = specs.stream()
			.filter(spec -> spec.resource().uri().equals("test://cached"))
			.findFirst()
			.orElseThrow();
		assertThat(((SyncModernMcpResourceMethodCallback) cached.handler().handler()).isStreaming()).isTrue();

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

		// The method runs and returns the input request, which the runtime check refuses.
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
	void runtimeCapabilityCheckWarnsOncePerMethod() {
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		Logger logger = (Logger) LoggerFactory.getLogger(ModernMethodSupport.class);
		logger.addAppender(appender);
		try {
			McpServer server = server();
			for (int i = 0; i < 2; i++) {
				Map<String, Object> params = params("name", "probe", "arguments", Map.of("kind", "sampling"));
				((Map<String, Object>) params.get("_meta")).put(MetaKeys.CLIENT_CAPABILITIES, Map.of());
				JSONRPCResponse error = finalResponse(send(server, McpSchema.METHOD_TOOLS_CALL, params));
				assertThat(error.error().code()).isEqualTo(McpSchema.ErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY);
			}
			// Both calls are refused; the hint about the attribute is logged once.
			assertThat(appender.list).singleElement().satisfies(event -> {
				assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
				assertThat(event.getFormattedMessage()).startsWith("Method probe in " + Tools.class.getName())
					.contains("requested [SAMPLING]")
					.contains("requiredCapabilities");
			});
		}
		finally {
			logger.detachAppender(appender);
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
	@SuppressWarnings("unchecked")
	void requiredCapabilitiesRefuseTheCallBeforeTheMethodRuns() {
		Gated bean = new Gated();
		McpServer server = server(bean);
		Map<String, Object> params = params("name", "gated_summary");
		((Map<String, Object>) params.get("_meta")).put(MetaKeys.CLIENT_CAPABILITIES, Map.of());
		McpTransportResponse response = send(server, McpSchema.METHOD_TOOLS_CALL, params);

		// A plain error, not a stream: the notifier the method declares was never
		// created.
		assertThat(response).isInstanceOf(McpTransportResponse.Error.class);
		JSONRPCResponse error = ((McpTransportResponse.Error) response).response();
		assertThat(error.error().code()).isEqualTo(McpSchema.ErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY);
		Map<String, Object> data = McpJsonDefaults.getMapper().convertValue(error.error().data(), Map.class);
		assertThat((Map<String, Object>) data.get("requiredCapabilities")).containsOnlyKeys("sampling");
		assertThat(bean.calls).hasValue(0);

		// A client that declares sampling reaches the method.
		Map<String, Object> result = result(send(server, McpSchema.METHOD_TOOLS_CALL, params("name", "gated_summary")));
		assertThat(result.get("resultType")).isEqualTo("input_required");
		assertThat(bean.calls).hasValue(1);
	}

	@Test
	@SuppressWarnings("unchecked")
	void requiredCapabilitiesErrorListsOnlyTheMissingOnes() {
		Map<String, Object> params = params("name", "gated_both");
		((Map<String, Object>) params.get("_meta")).put(MetaKeys.CLIENT_CAPABILITIES,
				Map.of("elicitation", Map.of("form", Map.of())));
		JSONRPCResponse error = finalResponse(send(server(new Gated()), McpSchema.METHOD_TOOLS_CALL, params));

		assertThat(error.error().code()).isEqualTo(McpSchema.ErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY);
		Map<String, Object> data = McpJsonDefaults.getMapper().convertValue(error.error().data(), Map.class);
		assertThat((Map<String, Object>) data.get("requiredCapabilities")).containsOnlyKeys("sampling");
	}

	@Test
	void requiredCapabilitiesAreDeclaredOnEveryPrimitive() {
		Gated bean = new Gated();
		var tools = SyncMcpAnnotationProviders.modernTools(List.of(bean));
		var both = tools.stream()
			.filter(tool -> tool.definition().name().equals("gated_both"))
			.findFirst()
			.orElseThrow();
		assertThat(both.requiredCapabilities().supportsElicitationForm()).isTrue();
		assertThat(both.requiredCapabilities().supportsSampling()).isTrue();
		assertThat(both.requiredCapabilities().supportsElicitationUrl()).isFalse();
		assertThat(both.requiredCapabilities().supportsRoots()).isFalse();

		assertThat(SyncMcpAnnotationProviders.modernPrompts(List.of(bean)).get(0).requiredCapabilities())
			.satisfies(required -> assertThat(required.supportsElicitationForm()).isTrue());
		assertThat(SyncMcpAnnotationProviders.modernResources(List.of(bean)).get(0).handler().requiredCapabilities())
			.satisfies(required -> assertThat(required.supportsRoots()).isTrue());
		assertThat(SyncMcpAnnotationProviders.modernResourceTemplates(List.of(bean))
			.get(0)
			.handler()
			.requiredCapabilities()).satisfies(required -> assertThat(required.supportsElicitationUrl()).isTrue());

		// Without the attribute nothing is required.
		assertThat(SyncMcpAnnotationProviders.modernTools(List.of(new Tools())))
			.allSatisfy(tool -> assertThat(tool.requiredCapabilities()).isEqualTo(ClientCapabilities.NONE));
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
		assertThat(SyncMcpAnnotationProviders.modernTools(List.of(new LegacyTools()))).isEmpty();
		assertThat(SyncMcpAnnotationProviders.modernResources(List.of(new LegacyResources()))).isEmpty();
		assertThat(SyncMcpAnnotationProviders.modernPrompts(List.of(new LegacyPrompts()))).isEmpty();
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

	@Test
	@SuppressWarnings("unchecked")
	void definitionAttributesArePublishedOnBothPaths() throws Exception {
		Decorated bean = new Decorated();

		// modern path
		var tool = SyncMcpAnnotationProviders.modernTools(List.of(bean)).get(0).definition();
		assertThat(tool.icons()).extracting(icon -> icon.src()).containsExactly("https://example.com/icon.png");
		assertThat(tool.icons().get(0).theme()).isEqualTo("dark");
		Map<String, Object> tenant = (Map<String, Object>) ((Map<String, Object>) tool.inputSchema().get("properties"))
			.get("tenant");
		assertThat(tenant.get("x-mcp-header")).isEqualTo("Mcp-Param-tenant");
		var resource = SyncMcpAnnotationProviders.modernResources(List.of(bean)).get(0).resource();
		assertThat(resource.size()).isEqualTo(42L);
		assertThat(resource.icons()).hasSize(1);
		var prompt = SyncMcpAnnotationProviders.modernPrompts(List.of(bean)).get(0).definition();
		assertThat(prompt.icons()).hasSize(1);
		assertThat(prompt.arguments().get(0).title()).isEqualTo("Topic");

		// legacy path, same annotations
		assertThat(SyncMcpAnnotationProviders.toolSpecifications(List.of(bean)).get(0).tool().icons()).hasSize(1);
		assertThat(SyncMcpAnnotationProviders.resourceSpecifications(List.of(bean)).get(0).resource().size())
			.isEqualTo(42L);
		assertThat(SyncMcpAnnotationProviders.promptSpecifications(List.of(bean))
			.get(0)
			.prompt()
			.arguments()
			.get(0)
			.title()).isEqualTo("Topic");

		// the rule is the schema type, so an enum qualifies as a string
		Map<String, Object> level = (Map<String, Object>) ((Map<String, Object>) SyncMcpAnnotationProviders
			.modernTools(List.of(bean))
			.stream()
			.filter(spec -> spec.definition().name().equals("levelled"))
			.findFirst()
			.orElseThrow()
			.definition()
			.inputSchema()
			.get("properties")).get("level");
		assertThat(level.get("type")).isEqualTo("string");
		assertThat(level.get("x-mcp-header")).isEqualTo("Mcp-Param-level");

		// header mirroring is restricted to string, integer and boolean schema types
		assertThatThrownBy(() -> McpJsonSchemaGenerator
			.generateForMethodInput(Decorated.class.getDeclaredMethod("badHeader", Map.class)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("header = true");
	}

	@SuppressWarnings("unused")
	static class Tools {

		@McpTool(name = "greet", description = "Greets")
		String greet(@McpToolParam(description = "name") String name) {
			return "Hello " + name;
		}

		@McpTool(name = "progress", description = "Reports progress")
		String progress(McpSyncNotifier notifier, McpModernSyncRequestContext ctx,
				@McpToolParam(description = "steps") int steps) {
			for (int i = 1; i <= steps; i++) {
				notifier.progress(i, (double) steps, null);
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
		ReadResourceResult readCached(McpSyncNotifier notifier, String uri) {
			notifier.progress(50, 100.0, null);
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
	static class Gated {

		final AtomicInteger calls = new AtomicInteger();

		@McpTool(name = "gated_summary", description = "Needs sampling",
				requiredCapabilities = McpClientCapability.SAMPLING)
		CallToolOutcome summary(McpSyncNotifier notifier) {
			this.calls.incrementAndGet();
			notifier.progress(1, 2.0, null);
			return InputRequiredResult.builder()
				.createMessage("k",
						CreateMessageRequest
							.builder(List.of(new SamplingMessage(Role.USER, TextContent.builder("m").build())), 10)
							.build())
				.build();
		}

		@McpTool(name = "gated_both", description = "Needs form elicitation and sampling",
				requiredCapabilities = { McpClientCapability.ELICITATION_FORM, McpClientCapability.SAMPLING })
		CallToolOutcome both() {
			return McpSchema.CallToolResult.builder().addContent(TextContent.builder("ok").build()).build();
		}

		@McpPrompt(name = "gated_prompt", description = "Needs form elicitation",
				requiredCapabilities = McpClientCapability.ELICITATION_FORM)
		GetPromptOutcome prompt() {
			return McpSchema.GetPromptResult.builder(List.of()).build();
		}

		@McpResource(uri = "test://required", name = "required", description = "Needs roots",
				requiredCapabilities = McpClientCapability.ROOTS)
		ReadResourceOutcome read(String uri) {
			return ReadResourceResult.builder(List.of(TextResourceContents.builder(uri, "x").build())).build();
		}

		@McpResource(uri = "test://required/{id}", name = "required-item", description = "Needs URL elicitation",
				requiredCapabilities = McpClientCapability.ELICITATION_URL)
		ReadResourceOutcome readItem(String id) {
			return ReadResourceResult
				.builder(List.of(TextResourceContents.builder("test://required/" + id, id).build()))
				.build();
		}

	}

	static class Decorated {

		@McpTool(name = "iconic", description = "Has an icon and a header parameter",
				icons = @McpIcon(src = "https://example.com/icon.png", mimeType = "image/png", sizes = "48x48",
						theme = "dark"))
		String iconic(@McpToolParam(description = "Tenant", header = true) String tenant) {
			return tenant;
		}

		@McpResource(uri = "test://sized", name = "sized", description = "sized", size = 42,
				icons = @McpIcon(src = "https://example.com/resource.svg"))
		String sized() {
			return "x";
		}

		@McpPrompt(name = "titled", description = "titled", icons = @McpIcon(src = "https://example.com/prompt.svg"))
		String titled(@McpArg(name = "topic", title = "Topic", required = true) String topic) {
			return topic;
		}

		@McpTool(name = "levelled", description = "Has an enum header parameter")
		String levelled(@McpToolParam(description = "Level", header = true) Level level) {
			return level.name();
		}

		@SuppressWarnings("unused")
		String badHeader(@McpToolParam(header = true) Map<String, Object> options) {
			return "";
		}

	}

	enum Level {

		LOW, HIGH

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
			return new CompleteResult(
					new CompleteResult.Completion(List.of(request.argument().value() + ":" + ctx.protocolVersion(),
							request.argument().value() + ":" + request.argument().name())),
					null, null);
		}

	}

}
