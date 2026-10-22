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

package org.springframework.ai.mcp.server.webflux.transport.modern;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.CallToolOutcome;
import io.modelcontextprotocol.modern.McpSchema.CallToolRequest;
import io.modelcontextprotocol.modern.McpSchema.ErrorCodes;
import io.modelcontextprotocol.modern.McpSchema.MetaKeys;
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
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link WebFluxMcpTransport}.
 *
 * @author Dariusz Jędrzejczyk
 */
class WebFluxMcpTransportTests {

	private static final String VERSION = McpSchema.LATEST_PROTOCOL_VERSION;

	private McpChangeFeed changes;

	private McpServer server;

	@BeforeEach
	void setUp() {
		this.changes = new McpChangeFeed();
		this.server = McpServer.builder()
			.serverInfo(ModernRequests.SERVER_INFO)
			.feature(ToolsFeature.ofSync(new TestTools()))
			.subscriptions(this.changes)
			.build();
	}

	@Test
	void requestIsAnsweredWithJson() {
		String body = post(client(), McpSchema.METHOD_TOOLS_LIST,
				ModernRequests.request(1, McpSchema.METHOD_TOOLS_LIST, ModernRequests.params()))
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();

		JsonAssertions.assertThatJson(body).inPath("$.id").isEqualTo(1);
		JsonAssertions.assertThatJson(body).inPath("$.result.tools[*].name").isArray().contains("echo", "progress");
		JsonAssertions.assertThatJson(body).inPath("$.result._meta").isObject().containsKey(MetaKeys.SERVER_INFO);
	}

	@Test
	void syncHandlerRunsOffTheCallingThread() {
		String body = callTool(client(), "thread", Map.of()).exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();

		// Resolved with handle(), never handleBlocking(): sync code is moved to
		// boundedElastic.
		JsonAssertions.assertThatJson(body)
			.inPath("$.result.content[0].text")
			.asString()
			.startsWith("boundedElastic")
			.endsWith("|false");
	}

	@Test
	void transportContextReachesHandlers() {
		WebTestClient client = client(builder -> builder.contextExtractor(
				request -> McpTransportContext.create(Map.of("tenant", request.headers().firstHeader("X-Tenant")))));

		String body = callTool(client, "context", Map.of()).header("X-Tenant", "acme")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();

		JsonAssertions.assertThatJson(body).inPath("$.result.content[0].text").isEqualTo("acme");
	}

	@Test
	void requestCharsetIsHonoured() {
		byte[] latin1 = ModernRequests.toolsCall(1, "echo", Map.of("text", "café"))
			.getBytes(StandardCharsets.ISO_8859_1);

		String body = client().post()
			.uri("/mcp")
			.contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.ISO_8859_1))
			.header("Mcp-Method", McpSchema.METHOD_TOOLS_CALL)
			.header("MCP-Protocol-Version", VERSION)
			.header("Mcp-Name", "echo")
			.bodyValue(latin1)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();

		JsonAssertions.assertThatJson(body).inPath("$.result.content[0].text").isEqualTo("café");
	}

	@Test
	void streamingResponseIsAnEventStream() {
		WebTestClient client = client(builder -> builder.keepAliveInterval(null));

		String progressCall = ModernRequests.request(1, McpSchema.METHOD_TOOLS_CALL, Map.of("_meta",
				ModernRequests.meta(MetaKeys.PROGRESS_TOKEN, "token"), "name", "progress", "arguments", Map.of()));

		byte[] body = post(client, McpSchema.METHOD_TOOLS_CALL, progressCall).header("Mcp-Name", "progress")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
			.expectHeader()
			.valueEquals(HttpHeaders.CACHE_CONTROL, "no-cache")
			.expectHeader()
			.valueEquals("X-Accel-Buffering", "no")
			.expectBody(byte[].class)
			.returnResult()
			.getResponseBody();

		String[] frames = new String(body, StandardCharsets.UTF_8).split("\n\n");
		// A leading comment commits the response, then two progress notifications and the
		// result, each a message event.
		assertThat(frames).hasSize(4);
		assertThat(frames[0]).isEqualTo(":");
		assertThat(frames[1]).startsWith("event:message\ndata:");
		JsonAssertions.assertThatJson(frames[1].substring("event:message\ndata:".length())).isEqualTo("""
				{"jsonrpc":"2.0","method":"notifications/progress",
				"params":{"progressToken":"token","progress":1.0,"total":2.0,"message":"half"}}""");
		JsonAssertions.assertThatJson(frames[2].substring("event:message\ndata:".length()))
			.inPath("$.params.progress")
			.isEqualTo(2.0);
		JsonAssertions.assertThatJson(frames[3].substring("event:message\ndata:".length()))
			.inPath("$.result.content[0].text")
			.isEqualTo("finished");
		JsonAssertions.assertThatJson(frames[3].substring("event:message\ndata:".length())).inPath("$.id").isEqualTo(1);
	}

	@Test
	void listenStreamForwardsChangesUntilClosedGracefully() {
		WebFluxMcpTransport transport = WebFluxMcpTransport.builder(this.server).keepAliveInterval(null).build();
		WebTestClient client = WebTestClient.bindToRouterFunction(transport.getRouterFunction()).build();

		Flux<ServerSentEvent<String>> events = post(client, McpSchema.METHOD_SUBSCRIPTIONS_LISTEN,
				ModernRequests.listen(7))
			.exchange()
			.expectStatus()
			.isOk()
			.returnResult(ModernRequests.SSE_TYPE)
			.getResponseBody();

		StepVerifier.create(events)
			.assertNext(event -> assertThat(event.data()).isNull())
			.assertNext(event -> JsonAssertions.assertThatJson(event.data())
				.inPath("$.method")
				.isEqualTo(McpSchema.METHOD_NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED))
			// Broadcast only once the stream acknowledged: the feed does not replay.
			.then(() -> this.changes.broadcast(new ServerChange.ToolsListChanged()))
			.assertNext(event -> {
				assertThat(event.event()).isEqualTo("message");
				JsonAssertions.assertThatJson(event.data())
					.inPath("$.method")
					.isEqualTo(McpSchema.METHOD_NOTIFICATION_TOOLS_LIST_CHANGED);
			})
			.then(transport::closeGracefully)
			.assertNext(event -> JsonAssertions.assertThatJson(event.data()).inPath("$.id").isEqualTo(7))
			.expectComplete()
			.verify(Duration.ofSeconds(5));
	}

	@Test
	void quietStreamGetsKeepAlives() {
		WebFluxMcpTransport transport = WebFluxMcpTransport.builder(this.server)
			.keepAliveInterval(Duration.ofMillis(20))
			.build();
		WebTestClient client = WebTestClient.bindToRouterFunction(transport.getRouterFunction()).build();

		Flux<ServerSentEvent<String>> events = post(client, McpSchema.METHOD_SUBSCRIPTIONS_LISTEN,
				ModernRequests.listen(1))
			.exchange()
			.returnResult(ModernRequests.SSE_TYPE)
			.getResponseBody();

		StepVerifier.create(events)
			.expectNextMatches(event -> event.data() == null)
			.expectNextMatches(event -> event.data() != null && event.data().contains("acknowledged"))
			.expectNextMatches(event -> event.data() == null && "".equals(event.comment()))
			.expectNextMatches(event -> event.data() == null)
			.then(transport::closeGracefully)
			.thenConsumeWhile(event -> event.data() == null)
			.assertNext(event -> JsonAssertions.assertThatJson(event.data()).inPath("$.result").isObject())
			.expectComplete()
			.verify(Duration.ofSeconds(5));
	}

	@Test
	void notificationIsAccepted() {
		String notification = """
				{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":1}}""";

		post(client(), McpSchema.METHOD_NOTIFICATION_CANCELLED, notification).exchange()
			.expectStatus()
			.isAccepted()
			.expectBody()
			.isEmpty();
	}

	@Test
	void malformedJsonIsAParseError() {
		String body = post(client(), McpSchema.METHOD_TOOLS_LIST, "{not json").exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();

		JsonAssertions.assertThatJson(body).isEqualTo("""
				{"jsonrpc":"2.0","error":{"code":-32700,"message":"Parse error"}}""");
	}

	@Test
	void emptyBodyIsAParseError() {
		String body = client().post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();

		JsonAssertions.assertThatJson(body).inPath("$.error.code").isEqualTo(ErrorCodes.PARSE_ERROR);
	}

	@Test
	void nonObjectIsAnInvalidRequest() {
		String body = post(client(), McpSchema.METHOD_TOOLS_LIST, "[1, 2]").exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();

		JsonAssertions.assertThatJson(body).inPath("$.error.code").isEqualTo(ErrorCodes.INVALID_REQUEST);
		JsonAssertions.assertThatJson(body).node("id").isAbsent();
	}

	@Test
	void invalidRequestKeepsItsId() {
		String body = post(client(), McpSchema.METHOD_TOOLS_LIST, """
				{"jsonrpc":"1.0","id":5,"method":"tools/list"}""").exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();

		JsonAssertions.assertThatJson(body).inPath("$.error.code").isEqualTo(ErrorCodes.INVALID_REQUEST);
		JsonAssertions.assertThatJson(body).inPath("$.id").isEqualTo(5);
	}

	@Test
	void responseFromTheClientIsAnInvalidRequest() {
		String body = post(client(), McpSchema.METHOD_TOOLS_LIST, """
				{"jsonrpc":"2.0","id":1,"result":{}}""").exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();

		JsonAssertions.assertThatJson(body).inPath("$.error.code").isEqualTo(ErrorCodes.INVALID_REQUEST);
		JsonAssertions.assertThatJson(body)
			.inPath("$.error.message")
			.isEqualTo("The server accepts either requests or notifications");
	}

	@Test
	void missingMethodHeaderIsAHeaderMismatch() {
		WebTestClient.RequestHeadersSpec<?> request = client().post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.header("MCP-Protocol-Version", VERSION)
			.bodyValue(ModernRequests.request(3, McpSchema.METHOD_TOOLS_LIST, ModernRequests.params()));

		assertHeaderMismatch(request, 3, "Missing required header: Mcp-Method");
	}

	@Test
	void methodHeaderMustMatchTheBody() {
		assertHeaderMismatch(
				post(client(), McpSchema.METHOD_TOOLS_CALL,
						ModernRequests.request(3, McpSchema.METHOD_TOOLS_LIST, ModernRequests.params())),
				3, "Mcp-Method header does not match request method");
	}

	@Test
	void missingVersionHeaderIsAHeaderMismatch() {
		WebTestClient.RequestHeadersSpec<?> request = client().post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.header("Mcp-Method", McpSchema.METHOD_TOOLS_LIST)
			.bodyValue(ModernRequests.request(3, McpSchema.METHOD_TOOLS_LIST, ModernRequests.params()));

		assertHeaderMismatch(request, 3, "Missing required header: MCP-Protocol-Version");
	}

	@Test
	void versionHeaderMustMatchTheBody() {
		WebTestClient.RequestHeadersSpec<?> request = client().post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.header("Mcp-Method", McpSchema.METHOD_TOOLS_LIST)
			.header("MCP-Protocol-Version", "2025-11-25")
			.bodyValue(ModernRequests.request(3, McpSchema.METHOD_TOOLS_LIST, ModernRequests.params()));

		assertHeaderMismatch(request, 3, "MCP-Protocol-Version header does not match _meta protocol version");
	}

	@Test
	void missingNameHeaderIsAHeaderMismatch() {
		assertHeaderMismatch(post(client(), McpSchema.METHOD_TOOLS_CALL, ModernRequests.toolsCall(3, "echo", Map.of())),
				3, "Missing required header: Mcp-Name");
	}

	@Test
	void nameHeaderMustMatchTheBody() {
		assertHeaderMismatch(post(client(), McpSchema.METHOD_TOOLS_CALL, ModernRequests.toolsCall(3, "echo", Map.of()))
			.header("Mcp-Name", "thread"), 3, "Mcp-Name header does not match request name/uri");
	}

	@Test
	void nameHeaderMayBeBase64Encoded() {
		String body = post(client(), McpSchema.METHOD_TOOLS_CALL,
				ModernRequests.toolsCall(3, "echo", Map.of("text", "hi")))
			.header("Mcp-Name", "=?base64?ZWNobw==?=")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();

		JsonAssertions.assertThatJson(body).inPath("$.result.content[0].text").isEqualTo("hi");
	}

	@Test
	void malformedBase64NameHeaderIsAHeaderMismatch() {
		assertHeaderMismatch(post(client(), McpSchema.METHOD_TOOLS_CALL, ModernRequests.toolsCall(3, "echo", Map.of()))
			.header("Mcp-Name", "=?base64?not*base64?="), 3, "Mcp-Name header has a malformed Base64 value");
	}

	@Test
	void missingMetaIsLeftToTheServer() {
		// No version in the body, so no version header to match: McpServer answers.
		String body = client().post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.header("Mcp-Method", McpSchema.METHOD_TOOLS_LIST)
			.bodyValue(ModernRequests.request(4, McpSchema.METHOD_TOOLS_LIST, Map.of()))
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();

		JsonAssertions.assertThatJson(body).inPath("$.error.code").isEqualTo(ErrorCodes.INVALID_PARAMS);
	}

	@Test
	void unknownMethodIsNotFound() {
		String body = post(client(), "tools/unknown",
				ModernRequests.request(1, "tools/unknown", ModernRequests.params()))
			.exchange()
			.expectStatus()
			.isNotFound()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();

		JsonAssertions.assertThatJson(body).inPath("$.error.code").isEqualTo(ErrorCodes.METHOD_NOT_FOUND);
	}

	@Test
	void unsupportedVersionIsABadRequest() {
		Map<String, Object> params = Map.of("_meta",
				Map.of(MetaKeys.PROTOCOL_VERSION, "1999-01-01", MetaKeys.CLIENT_CAPABILITIES, Map.of()));
		String body = client().post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.header("Mcp-Method", McpSchema.METHOD_TOOLS_LIST)
			.header("MCP-Protocol-Version", "1999-01-01")
			.bodyValue(ModernRequests.request(1, McpSchema.METHOD_TOOLS_LIST, params))
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();

		JsonAssertions.assertThatJson(body).inPath("$.error.code").isEqualTo(ErrorCodes.UNSUPPORTED_PROTOCOL_VERSION);
		JsonAssertions.assertThatJson(body).inPath("$.error.data.supported").isArray().containsExactly(VERSION);
	}

	@Test
	void unknownToolIsInvalidParams() {
		String body = callTool(client(), "missing", Map.of()).exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();

		JsonAssertions.assertThatJson(body).inPath("$.error.code").isEqualTo(ErrorCodes.INVALID_PARAMS);
	}

	@Test
	void unmappedErrorCodeIsAnsweredWithOk() {
		String body = callTool(client(), "boom", Map.of()).exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();

		JsonAssertions.assertThatJson(body).isEqualTo("""
				{"jsonrpc":"2.0","id":1,"error":{"code":-32603,"message":"Internal error"}}""");
	}

	@Test
	void errorStatusIsConfigurable() {
		WebTestClient client = client(builder -> builder.errorStatus(ErrorCodes.INTERNAL_ERROR, 500));

		callTool(client, "boom", Map.of()).exchange().expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Test
	void otherMethodsAreNotAllowed() {
		for (HttpMethod method : new HttpMethod[] { HttpMethod.GET, HttpMethod.DELETE, HttpMethod.PUT }) {
			client().method(method)
				.uri("/mcp")
				.exchange()
				.expectStatus()
				.isEqualTo(HttpStatus.METHOD_NOT_ALLOWED)
				.expectHeader()
				.valueEquals(HttpHeaders.ALLOW, "POST");
		}
	}

	@Test
	void otherPathsAreNotServed() {
		client().post()
			.uri("/other")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(ModernRequests.request(1, McpSchema.METHOD_TOOLS_LIST, ModernRequests.params()))
			.exchange()
			.expectStatus()
			.isNotFound();
	}

	@Test
	void endpointIsConfigurable() {
		WebTestClient client = client(builder -> builder.endpoint("/custom/mcp"));

		client.post()
			.uri("/custom/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.header("Mcp-Method", McpSchema.METHOD_TOOLS_LIST)
			.header("MCP-Protocol-Version", VERSION)
			.bodyValue(ModernRequests.request(1, McpSchema.METHOD_TOOLS_LIST, ModernRequests.params()))
			.exchange()
			.expectStatus()
			.isOk();
	}

	@Test
	void closedTransportIsUnavailable() {
		WebFluxMcpTransport transport = WebFluxMcpTransport.builder(this.server).build();
		WebTestClient client = WebTestClient.bindToRouterFunction(transport.getRouterFunction()).build();
		transport.closeGracefully();

		post(client, McpSchema.METHOD_TOOLS_LIST,
				ModernRequests.request(1, McpSchema.METHOD_TOOLS_LIST, ModernRequests.params()))
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
	}

	@Test
	void crossOriginRequestIsRejectedByDefault() {
		post(client(), McpSchema.METHOD_TOOLS_LIST,
				ModernRequests.request(1, McpSchema.METHOD_TOOLS_LIST, ModernRequests.params()))
			.header(HttpHeaders.ORIGIN, "http://evil.example")
			.exchange()
			.expectStatus()
			.isForbidden();
	}

	@Test
	void allowedOriginIsServed() {
		WebTestClient client = client(builder -> builder.httpHeaderValidator(
				DefaultServerTransportSecurityValidator.builder().allowedOrigin("http://localhost:*").build()));

		post(client, McpSchema.METHOD_TOOLS_LIST,
				ModernRequests.request(1, McpSchema.METHOD_TOOLS_LIST, ModernRequests.params()))
			.header(HttpHeaders.ORIGIN, "http://localhost:3000")
			.exchange()
			.expectStatus()
			.isOk();
	}

	@Test
	void declaredOversizedBodyIsRejected() {
		WebTestClient client = client(builder -> builder.maxRequestSize(64));

		post(client, McpSchema.METHOD_TOOLS_CALL, ModernRequests.toolsCall(1, "echo", Map.of("text", "x".repeat(100))))
			.header("Mcp-Name", "echo")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
	}

	@Test
	void streamedOversizedBodyIsRejected() {
		WebTestClient client = client(builder -> builder.maxRequestSize(64));
		byte[] chunk = ModernRequests.toolsCall(1, "echo", Map.of("text", "x".repeat(100)))
			.getBytes(StandardCharsets.UTF_8);
		Flux<DataBuffer> body = Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(chunk));

		client.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.header("Mcp-Method", McpSchema.METHOD_TOOLS_CALL)
			.header("MCP-Protocol-Version", VERSION)
			.header("Mcp-Name", "echo")
			.body(body, DataBuffer.class)
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
	}

	@Test
	void builderRejectsInvalidSettings() {
		WebFluxMcpTransport.Builder builder = WebFluxMcpTransport.builder(this.server);

		assertThatIllegalArgumentException().isThrownBy(() -> WebFluxMcpTransport.builder(null));
		assertThatIllegalArgumentException().isThrownBy(() -> builder.endpoint(""));
		assertThatIllegalArgumentException().isThrownBy(() -> builder.maxRequestSize(0));
		assertThatIllegalArgumentException().isThrownBy(() -> builder.keepAliveInterval(Duration.ZERO));
		assertThatIllegalArgumentException().isThrownBy(() -> builder.errorStatus(ErrorCodes.INTERNAL_ERROR, 100));
	}

	private WebTestClient client() {
		return client(builder -> {
		});
	}

	private WebTestClient client(Consumer<WebFluxMcpTransport.Builder> customizer) {
		WebFluxMcpTransport.Builder builder = WebFluxMcpTransport.builder(this.server);
		customizer.accept(builder);
		return WebTestClient.bindToRouterFunction(builder.build().getRouterFunction()).build();
	}

	private static WebTestClient.RequestHeadersSpec<?> post(WebTestClient client, String method, String body) {
		return client.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.header("Mcp-Method", method)
			.header("MCP-Protocol-Version", VERSION)
			.bodyValue(body);
	}

	private static WebTestClient.RequestHeadersSpec<?> callTool(WebTestClient client, String tool,
			Map<String, Object> arguments) {
		return post(client, McpSchema.METHOD_TOOLS_CALL, ModernRequests.toolsCall(1, tool, arguments))
			.header("Mcp-Name", tool);
	}

	private static void assertHeaderMismatch(WebTestClient.RequestHeadersSpec<?> request, Object id, String message) {
		String body = request.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();

		JsonAssertions.assertThatJson(body)
			.isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"error\":{\"code\":" + ErrorCodes.HEADER_MISMATCH
					+ ",\"message\":\"" + message + "\"}}");
	}

	private static final class TestTools implements McpSyncToolRepository {

		private static final Map<String, Tool> TOOLS = Map.of("echo", ModernRequests.tool("echo"), "thread",
				ModernRequests.tool("thread"), "context", ModernRequests.tool("context"), "progress",
				ModernRequests.tool("progress"), "boom", ModernRequests.tool("boom"));

		@Override
		public ToolsPage list(McpRequestContext ctx, String cursor) {
			return ToolsPage.of(ModernRequests.tools("echo", "thread", "context", "progress", "boom"));
		}

		@Override
		public Tool find(McpRequestContext ctx, String name) {
			return TOOLS.get(name);
		}

		@Override
		public McpSyncResponse<CallToolOutcome> call(McpRequestContext ctx, CallToolRequest request) {
			return switch (request.name()) {
				case "echo" ->
					McpSyncResponse.result(ModernRequests.text(String.valueOf(request.arguments().get("text"))));
				case "thread" -> McpSyncResponse
					.result(ModernRequests.text(Thread.currentThread().getName() + "|" + ctx.isBlocking()));
				case "context" ->
					McpSyncResponse.result(ModernRequests.text(String.valueOf(ctx.transportContext().get("tenant"))));
				case "progress" -> McpSyncResponse.streaming(notifier -> {
					notifier.progress(1, 2.0, "half");
					notifier.progress(2, 2.0, "done");
					return ModernRequests.text("finished");
				});
				default -> throw new IllegalStateException("boom");
			};
		}

	}

}
