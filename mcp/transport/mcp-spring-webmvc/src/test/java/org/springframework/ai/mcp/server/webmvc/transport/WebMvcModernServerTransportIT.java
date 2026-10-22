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

package org.springframework.ai.mcp.server.webmvc.transport;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.CallToolOutcome;
import io.modelcontextprotocol.modern.McpSchema.CallToolRequest;
import io.modelcontextprotocol.modern.McpSchema.CallToolResult;
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.McpSchema.MetaKeys;
import io.modelcontextprotocol.modern.McpSchema.TextContent;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.feature.McpChangeFeed;
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository;
import io.modelcontextprotocol.modern.server.feature.ServerChange;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import io.modelcontextprotocol.modern.server.feature.ToolsPage;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import org.apache.catalina.LifecycleState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.ai.mcp.server.TomcatTestUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for {@link WebMvcModernServerTransport}, driven over raw HTTP since
 * no client exists yet for the 2026-07-28 revision.
 *
 * @author Christian Tzolov
 */
@Timeout(60)
class WebMvcModernServerTransportIT {

	private static final String ENDPOINT = "/mcp";

	private static final McpJsonMapper JSON = McpJsonDefaults.getMapper();

	private static final Map<String, Object> EMPTY_SCHEMA = Map.of("type", "object", "properties", Map.of());

	private static final McpChangeFeed CHANGES = new McpChangeFeed();

	private static final int MAX_REQUEST_SIZE = 64 * 1024;

	private TomcatTestUtil.TomcatServer tomcatServer;

	private WebMvcModernServerTransport transport;

	private int port;

	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	@BeforeEach
	void before() {
		this.tomcatServer = TomcatTestUtil.createTomcatServer("", 0, TestConfig.class);
		try {
			this.tomcatServer.tomcat().start();
			assertThat(this.tomcatServer.tomcat().getServer().getState()).isEqualTo(LifecycleState.STARTED);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to start Tomcat", e);
		}
		this.port = this.tomcatServer.tomcat().getConnector().getLocalPort();
		this.transport = this.tomcatServer.appContext().getBean(WebMvcModernServerTransport.class);
	}

	@AfterEach
	void after() {
		if (this.transport != null) {
			this.transport.closeGracefully().block();
		}
		if (this.tomcatServer != null && this.tomcatServer.appContext() != null) {
			this.tomcatServer.appContext().close();
		}
		if (this.tomcatServer != null) {
			try {
				this.tomcatServer.tomcat().stop();
				this.tomcatServer.tomcat().destroy();
			}
			catch (Exception e) {
				// ignore
			}
		}
	}

	// ---------------------------------------------------------------------
	// Request helpers
	// ---------------------------------------------------------------------

	private static Map<String, Object> meta() {
		Map<String, Object> meta = new HashMap<>();
		meta.put(MetaKeys.PROTOCOL_VERSION, McpSchema.LATEST_PROTOCOL_VERSION);
		meta.put(MetaKeys.CLIENT_CAPABILITIES, Map.of());
		return meta;
	}

	private static Map<String, Object> params(Object... keyValues) {
		Map<String, Object> params = new HashMap<>();
		params.put("_meta", meta());
		for (int i = 0; i < keyValues.length; i += 2) {
			params.put((String) keyValues[i], keyValues[i + 1]);
		}
		return params;
	}

	private HttpRequest.Builder post(String method, Map<String, Object> params) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("jsonrpc", "2.0");
		body.put("id", 1);
		body.put("method", method);
		body.put("params", params);
		return rawPost(JSON.writeValueAsString(body)).header("Mcp-Method", method)
			.header("MCP-Protocol-Version", McpSchema.LATEST_PROTOCOL_VERSION);
	}

	private HttpRequest.Builder rawPost(String json) {
		return HttpRequest.newBuilder()
			.uri(URI.create("http://localhost:" + this.port + ENDPOINT))
			.header("Content-Type", "application/json")
			.header("Accept", "application/json, text/event-stream")
			.POST(HttpRequest.BodyPublishers.ofString(json));
	}

	private HttpResponse<String> send(HttpRequest request) throws Exception {
		return this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private static Map<String, Object> parse(String json) throws Exception {
		return JSON.readValue(json, new TypeRef<Map<String, Object>>() {
		});
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> result(String json) throws Exception {
		return (Map<String, Object>) parse(json).get("result");
	}

	@SuppressWarnings("unchecked")
	private static int errorCode(String json) throws Exception {
		Map<String, Object> error = (Map<String, Object>) parse(json).get("error");
		return ((Number) error.get("code")).intValue();
	}

	/** The JSON payloads of the {@code event: message} frames in an SSE body. */
	private static List<String> sseData(String body) {
		List<String> data = new ArrayList<>();
		for (String line : body.split("\n")) {
			if (line.startsWith("data: ")) {
				data.add(line.substring("data: ".length()));
			}
		}
		return data;
	}

	// ---------------------------------------------------------------------
	// Tests
	// ---------------------------------------------------------------------

	@Test
	void discoverReturnsSupportedVersions() throws Exception {
		HttpResponse<String> response = send(post("server/discover", params()).build());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type"))
			.hasValueSatisfying(v -> assertThat(v).contains("application/json"));
		assertThat(result(response.body()).get("supportedVersions"))
			.isEqualTo(List.of(McpSchema.LATEST_PROTOCOL_VERSION));
	}

	@Test
	@SuppressWarnings("unchecked")
	void toolsListReturnsRegisteredTools() throws Exception {
		HttpResponse<String> response = send(post("tools/list", params()).build());

		assertThat(response.statusCode()).isEqualTo(200);
		List<Map<String, Object>> tools = (List<Map<String, Object>>) result(response.body()).get("tools");
		assertThat(tools).extracting(t -> t.get("name")).contains("echo", "progress", "test_trigger_tool_change");
	}

	@Test
	@SuppressWarnings("unchecked")
	void toolCallReturnsJson() throws Exception {
		HttpResponse<String> response = send(
				post("tools/call", params("name", "echo", "arguments", Map.of())).header("Mcp-Name", "echo").build());

		assertThat(response.statusCode()).isEqualTo(200);
		Map<String, Object> result = result(response.body());
		assertThat(result.get("resultType")).isEqualTo("complete");
		List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
		assertThat(content.get(0).get("text")).isEqualTo("echo");
	}

	@Test
	void missingMcpMethodHeaderIsHeaderMismatch() throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("jsonrpc", "2.0");
		body.put("id", 1);
		body.put("method", "tools/list");
		body.put("params", params());
		HttpResponse<String> response = send(
				rawPost(JSON.writeValueAsString(body)).header("MCP-Protocol-Version", McpSchema.LATEST_PROTOCOL_VERSION)
					.build());

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(errorCode(response.body())).isEqualTo(McpSchema.ErrorCodes.HEADER_MISMATCH);
	}

	@Test
	void mcpMethodHeaderMismatchIsRejected() throws Exception {
		HttpResponse<String> response = send(
				post("tools/list", params()).setHeader("Mcp-Method", "tools/call").build());

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(errorCode(response.body())).isEqualTo(McpSchema.ErrorCodes.HEADER_MISMATCH);
	}

	@Test
	void base64McpNameHeaderIsAccepted() throws Exception {
		String encoded = "=?base64?" + Base64.getEncoder().encodeToString("echo".getBytes(StandardCharsets.UTF_8))
				+ "?=";
		HttpResponse<String> response = send(
				post("tools/call", params("name", "echo", "arguments", Map.of())).header("Mcp-Name", encoded).build());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(result(response.body())).containsKey("content");
	}

	@Test
	void mcpNameMismatchIsRejected() throws Exception {
		HttpResponse<String> response = send(
				post("tools/call", params("name", "echo", "arguments", Map.of())).header("Mcp-Name", "other").build());

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(errorCode(response.body())).isEqualTo(McpSchema.ErrorCodes.HEADER_MISMATCH);
	}

	@Test
	void missingMcpNameIsRejected() throws Exception {
		HttpResponse<String> response = send(post("tools/call", params("name", "echo", "arguments", Map.of())).build());

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(errorCode(response.body())).isEqualTo(McpSchema.ErrorCodes.HEADER_MISMATCH);
	}

	@Test
	void unsupportedProtocolVersionIsRejected() throws Exception {
		Map<String, Object> params = params();
		Map<String, Object> meta = new HashMap<>(meta());
		meta.put(MetaKeys.PROTOCOL_VERSION, "1999-01-01");
		params.put("_meta", meta);
		HttpResponse<String> response = send(
				post("tools/list", params).setHeader("MCP-Protocol-Version", "1999-01-01").build());

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(errorCode(response.body())).isEqualTo(McpSchema.ErrorCodes.UNSUPPORTED_PROTOCOL_VERSION);
	}

	@Test
	void unknownMethodIsNotFound() throws Exception {
		HttpResponse<String> response = send(post("does/not/exist", params()).build());

		assertThat(response.statusCode()).isEqualTo(404);
		assertThat(errorCode(response.body())).isEqualTo(McpSchema.ErrorCodes.METHOD_NOT_FOUND);
	}

	@Test
	void progressToolStreamsNotificationsThenResult() throws Exception {
		Map<String, Object> params = params("name", "progress", "arguments", Map.of());
		Map<String, Object> meta = new HashMap<>(meta());
		meta.put(MetaKeys.PROGRESS_TOKEN, "tok-1");
		params.put("_meta", meta);
		HttpResponse<String> response = send(post("tools/call", params).header("Mcp-Name", "progress").build());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type"))
			.hasValueSatisfying(v -> assertThat(v).contains("text/event-stream"));
		assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-cache, no-transform");
		List<String> frames = sseData(response.body());
		assertThat(frames).hasSize(2);
		assertThat(parse(frames.get(0)).get("method")).isEqualTo(McpSchema.METHOD_NOTIFICATION_PROGRESS);
		assertThat(result(frames.get(1))).containsKey("content");
	}

	@Test
	void listenStreamsAcknowledgementAndChangesUntilGracefulClose() throws Exception {
		Map<String, Object> params = params("notifications", Map.of("toolsListChanged", true));
		HttpRequest listen = post("subscriptions/listen", params).build();

		List<String> lines = new CopyOnWriteArrayList<>();
		CompletableFuture<HttpResponse<InputStream>> future = this.httpClient.sendAsync(listen,
				HttpResponse.BodyHandlers.ofInputStream());
		HttpResponse<InputStream> response = future.get(10, TimeUnit.SECONDS);
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type"))
			.hasValueSatisfying(v -> assertThat(v).contains("text/event-stream"));

		Thread reader = new Thread(() -> {
			try (BufferedReader in = new BufferedReader(
					new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = in.readLine()) != null) {
					lines.add(line);
				}
			}
			catch (Exception e) {
				// stream closed
			}
		});
		reader.start();

		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(lines)
				.anyMatch(l -> l.contains(McpSchema.METHOD_NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED)));

		HttpResponse<String> trigger = send(
				post("tools/call", params("name", "test_trigger_tool_change", "arguments", Map.of()))
					.header("Mcp-Name", "test_trigger_tool_change")
					.build());
		assertThat(trigger.statusCode()).isEqualTo(200);

		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(lines)
				.anyMatch(l -> l.contains(McpSchema.METHOD_NOTIFICATION_TOOLS_LIST_CHANGED)));

		this.transport.closeGracefully().block();
		reader.join(TimeUnit.SECONDS.toMillis(10));
		assertThat(reader.isAlive()).isFalse();

		List<String> data = sseData(String.join("\n", lines));
		Map<String, Object> last = parse(data.get(data.size() - 1));
		assertThat(last).containsKey("result");
		assertThat(result(data.get(data.size() - 1)).get("resultType")).isEqualTo("complete");
	}

	@Test
	void getIsMethodNotAllowed() throws Exception {
		HttpResponse<String> response = send(
				HttpRequest.newBuilder().uri(URI.create("http://localhost:" + this.port + ENDPOINT)).GET().build());

		assertThat(response.statusCode()).isEqualTo(405);
		assertThat(response.headers().firstValue("Allow")).hasValue("POST");
	}

	@Test
	void anyOtherMethodIsMethodNotAllowed() throws Exception {
		HttpResponse<String> response = send(HttpRequest.newBuilder()
			.uri(URI.create("http://localhost:" + this.port + ENDPOINT))
			.PUT(HttpRequest.BodyPublishers.ofString("{}"))
			.build());

		assertThat(response.statusCode()).isEqualTo(405);
		assertThat(response.headers().firstValue("Allow")).hasValue("POST");
	}

	@Test
	void streamStartsWithAHeaderCommittingComment() throws Exception {
		Map<String, Object> params = params("name", "progress", "arguments", Map.of());
		HttpResponse<String> response = send(post("tools/call", params).header("Mcp-Name", "progress").build());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).startsWith(":");
	}

	@Test
	void deleteIsMethodNotAllowed() throws Exception {
		HttpResponse<String> response = send(
				HttpRequest.newBuilder().uri(URI.create("http://localhost:" + this.port + ENDPOINT)).DELETE().build());

		assertThat(response.statusCode()).isEqualTo(405);
	}

	@Test
	void notificationIsAccepted() throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("jsonrpc", "2.0");
		body.put("method", McpSchema.METHOD_NOTIFICATION_CANCELLED);
		body.put("params", Map.of("requestId", 1));
		HttpResponse<String> response = send(
				rawPost(JSON.writeValueAsString(body)).header("Mcp-Method", McpSchema.METHOD_NOTIFICATION_CANCELLED)
					.header("MCP-Protocol-Version", McpSchema.LATEST_PROTOCOL_VERSION)
					.build());

		assertThat(response.statusCode()).isEqualTo(202);
	}

	@Test
	void malformedJsonIsParseError() throws Exception {
		HttpResponse<String> response = send(rawPost("{not json").header("Mcp-Method", "tools/list").build());

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(errorCode(response.body())).isEqualTo(McpSchema.ErrorCodes.PARSE_ERROR);
	}

	@Test
	void oversizedChunkedBodyIsPayloadTooLarge() throws Exception {
		// No Content-Length: the limit must be enforced while the body is read.
		byte[] oversized = ("{\"padding\":\"" + "x".repeat(MAX_REQUEST_SIZE) + "\"}").getBytes(StandardCharsets.UTF_8);
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("http://localhost:" + this.port + ENDPOINT))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(oversized)))
			.build();
		HttpResponse<String> response = send(request);
		assertThat(response.statusCode()).isEqualTo(413);
	}

	@Test
	void nonJsonContentTypeIsUnsupportedMediaType() throws Exception {
		HttpResponse<String> response = send(HttpRequest.newBuilder()
			.uri(URI.create("http://localhost:" + this.port + ENDPOINT))
			.header("Content-Type", "text/plain")
			.header("Mcp-Method", "tools/list")
			.POST(HttpRequest.BodyPublishers.ofString("{}"))
			.build());

		assertThat(response.statusCode()).isEqualTo(415);
	}

	@Test
	void closedTransportIsServiceUnavailable() throws Exception {
		this.transport.closeGracefully().block();
		HttpResponse<String> response = send(post("tools/list", params()).build());

		assertThat(response.statusCode()).isEqualTo(503);
	}

	@Test
	void disallowedOriginIsForbidden() throws Exception {
		HttpResponse<String> response = send(
				post("tools/list", params()).header("Origin", "http://evil.example.com").build());

		assertThat(response.statusCode()).isEqualTo(403);
	}

	@Test
	void allowedOriginIsAccepted() throws Exception {
		HttpResponse<String> response = send(
				post("tools/list", params()).header("Origin", "http://localhost:" + this.port).build());

		assertThat(response.statusCode()).isEqualTo(200);
	}

	// ---------------------------------------------------------------------
	// Server under test
	// ---------------------------------------------------------------------

	private static CallToolResult text(String text) {
		return CallToolResult.builder().addContent(TextContent.builder(text).build()).build();
	}

	static final class TestTools implements McpSyncToolRepository {

		private final Map<String, Tool> tools = new HashMap<>();

		TestTools() {
			for (String name : List.of("echo", "progress", "test_trigger_tool_change")) {
				this.tools.put(name, Tool.builder(name, EMPTY_SCHEMA).description(name).build());
			}
		}

		@Override
		public ToolsPage list(McpRequestContext ctx, String cursor) {
			return ToolsPage.of(List.copyOf(this.tools.values()));
		}

		@Override
		public Tool find(McpRequestContext ctx, String name) {
			return this.tools.get(name);
		}

		@Override
		public McpSyncResponse<CallToolOutcome> call(McpRequestContext ctx, CallToolRequest request) {
			return switch (request.name()) {
				case "echo" -> McpSyncResponse.result(text("echo"));
				case "progress" -> McpSyncResponse.streaming(notifier -> {
					notifier.progress(50, 100.0, null);
					return text("progress done");
				});
				case "test_trigger_tool_change" -> {
					CHANGES.broadcast(new ServerChange.ToolsListChanged());
					yield McpSyncResponse.result(text("Mutation triggered"));
				}
				default -> throw McpException.invalidParams("Unknown tool: " + request.name());
			};
		}

	}

	@Configuration
	@EnableWebMvc
	static class TestConfig {

		@Bean
		McpServer mcpServer() {
			return McpServer.builder()
				.serverInfo(Implementation.builder("webmvc-modern-test", "1.0.0").build())
				.feature(ToolsFeature.ofSync(new TestTools()))
				.subscriptions(CHANGES)
				.build();
		}

		@Bean
		WebMvcModernServerTransport webMvcModernServerTransport(McpServer mcpServer) {
			return WebMvcModernServerTransport.builder(mcpServer)
				.messageEndpoint(ENDPOINT)
				.keepAliveInterval(Duration.ofSeconds(1))
				.maxRequestSize(MAX_REQUEST_SIZE)
				.httpHeaderValidator(DefaultServerTransportSecurityValidator.builder()
					.allowedOrigin("http://localhost:*")
					.allowedHost("localhost:*")
					.build())
				.build();
		}

		@Bean
		RouterFunction<ServerResponse> mcpRouterFunction(WebMvcModernServerTransport transport) {
			return transport.getRouterFunction();
		}

	}

}
