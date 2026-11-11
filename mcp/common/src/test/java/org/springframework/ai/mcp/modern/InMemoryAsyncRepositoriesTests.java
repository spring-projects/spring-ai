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

package org.springframework.ai.mcp.modern;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCRequest;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCResponse;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.CallToolResult;
import io.modelcontextprotocol.modern.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.modern.McpSchema.CompleteResult;
import io.modelcontextprotocol.modern.McpSchema.GetPromptResult;
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.McpSchema.MetaKeys;
import io.modelcontextprotocol.modern.McpSchema.Prompt;
import io.modelcontextprotocol.modern.McpSchema.PromptArgument;
import io.modelcontextprotocol.modern.McpSchema.PromptMessage;
import io.modelcontextprotocol.modern.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.modern.McpSchema.Resource;
import io.modelcontextprotocol.modern.McpSchema.ResourceTemplate;
import io.modelcontextprotocol.modern.McpSchema.Role;
import io.modelcontextprotocol.modern.McpSchema.TextContent;
import io.modelcontextprotocol.modern.McpSchema.TextResourceContents;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.server.McpAsyncResponse;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.McpTransportResponse;
import io.modelcontextprotocol.modern.server.feature.McpAsyncPrompt;
import io.modelcontextprotocol.modern.server.feature.McpAsyncTool;
import io.modelcontextprotocol.modern.server.feature.McpChangeFeed;
import io.modelcontextprotocol.modern.server.feature.PromptsFeature;
import io.modelcontextprotocol.modern.server.feature.ResourcesFeature;
import io.modelcontextprotocol.modern.server.feature.ServerChange;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives the asynchronous in-memory repositories through a real {@link McpServer} on its
 * non-blocking path, the twin of {@link InMemoryRepositoriesTests}.
 *
 * @author Christian Tzolov
 */
class InMemoryAsyncRepositoriesTests {

	private static final Map<String, Object> EMPTY_SCHEMA = Map.of("type", "object", "properties", Map.of());

	private static Map<String, Object> params(Object... keyValues) {
		Map<String, Object> params = new HashMap<>();
		Map<String, Object> meta = new HashMap<>();
		meta.put(MetaKeys.PROTOCOL_VERSION, McpSchema.LATEST_PROTOCOL_VERSION);
		meta.put(MetaKeys.CLIENT_CAPABILITIES, Map.of());
		params.put("_meta", meta);
		for (int i = 0; i < keyValues.length; i += 2) {
			params.put((String) keyValues[i], keyValues[i + 1]);
		}
		return params;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> call(McpServer server, String method, Map<String, Object> params) {
		McpTransportResponse response = server.handle(McpTransportContext.EMPTY, new JSONRPCRequest(method, 1, params))
			.block();
		JSONRPCResponse jsonRpc = response instanceof McpTransportResponse.Result result ? result.response()
				: ((McpTransportResponse.Error) response).response();
		if (jsonRpc.error() != null) {
			Map<String, Object> error = new HashMap<>();
			error.put("code", jsonRpc.error().code());
			error.put("message", jsonRpc.error().message());
			return error;
		}
		return (Map<String, Object>) jsonRpc.result();
	}

	private static McpServer server(ToolsFeature feature) {
		return McpServer.builder().serverInfo(Implementation.builder("test", "1").build()).feature(feature).build();
	}

	private static McpAsyncTool echoTool() {
		return McpAsyncTool.of(Tool.builder("echo", EMPTY_SCHEMA).description("echo").build(),
				(ctx, request) -> Mono.just(McpAsyncResponse.result(CallToolResult.builder()
					.addContent(TextContent.builder("echo:" + request.arguments().get("text")).build())
					.build())));
	}

	@Test
	@SuppressWarnings("unchecked")
	void toolRepositoryListsAndCallsTools() {
		InMemoryAsyncToolRepository repository = InMemoryAsyncToolRepository.builder().tool(echoTool()).build();
		McpServer server = server(ToolsFeature.ofAsync(repository));

		Map<String, Object> list = call(server, McpSchema.METHOD_TOOLS_LIST, params());
		List<Map<String, Object>> tools = (List<Map<String, Object>>) list.get("tools");
		assertThat(tools).hasSize(1);
		assertThat(tools.get(0).get("name")).isEqualTo("echo");

		Map<String, Object> result = call(server, McpSchema.METHOD_TOOLS_CALL,
				params("name", "echo", "arguments", Map.of("text", "hi")));
		List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
		assertThat(content.get(0).get("text")).isEqualTo("echo:hi");

		Map<String, Object> unknown = call(server, McpSchema.METHOD_TOOLS_CALL, params("name", "nope"));
		assertThat(unknown.get("code")).isEqualTo(McpSchema.ErrorCodes.INVALID_PARAMS);
	}

	@Test
	void findIsEmptyForAnUnknownName() {
		InMemoryAsyncToolRepository repository = InMemoryAsyncToolRepository.builder().tool(echoTool()).build();

		StepVerifier.create(repository.find(null, "nope")).verifyComplete();
		StepVerifier.create(repository.find(null, "echo"))
			.expectNextMatches(tool -> tool.definition().name().equals("echo"))
			.verifyComplete();
	}

	@Test
	void findLooksUpWhenSubscribed() {
		InMemoryAsyncToolRepository repository = InMemoryAsyncToolRepository.builder().build();
		Mono<McpAsyncTool> find = repository.find(null, "echo");

		repository.add(echoTool());
		StepVerifier.create(find).expectNextMatches(tool -> tool.definition().name().equals("echo")).verifyComplete();
		repository.remove("echo");
		StepVerifier.create(find).verifyComplete();
	}

	@Test
	void toolRequiringUndeclaredCapabilityIsRejectedBeforeTheHandlerRuns() {
		McpAsyncTool sampling = McpAsyncTool.of(Tool.builder("sample", EMPTY_SCHEMA).build(),
				ClientCapabilities.builder().sampling().build(),
				(ctx, request) -> Mono.error(new AssertionError("handler must not run")));
		McpServer server = server(ToolsFeature.ofAsync(InMemoryAsyncToolRepository.builder().tool(sampling).build()));

		Map<String, Object> rejected = call(server, McpSchema.METHOD_TOOLS_CALL, params("name", "sample"));
		assertThat(rejected.get("code")).isEqualTo(McpSchema.ErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY);
	}

	@Test
	@SuppressWarnings("unchecked")
	void toolRepositoryPagesByName() {
		InMemoryAsyncToolRepository.Builder builder = InMemoryAsyncToolRepository.builder().pageSize(2);
		for (String name : List.of("c", "a", "b")) {
			builder.tool(McpAsyncTool.of(Tool.builder(name, EMPTY_SCHEMA).build(),
					(ctx, request) -> Mono.just(McpAsyncResponse.result(CallToolResult.builder().build()))));
		}
		McpServer server = server(ToolsFeature.ofAsync(builder.build()));

		Map<String, Object> first = call(server, McpSchema.METHOD_TOOLS_LIST, params());
		List<Map<String, Object>> tools = (List<Map<String, Object>>) first.get("tools");
		assertThat(tools).extracting(tool -> tool.get("name")).containsExactly("a", "b");
		assertThat(first.get("nextCursor")).isNotNull();

		Map<String, Object> second = call(server, McpSchema.METHOD_TOOLS_LIST,
				params("cursor", first.get("nextCursor")));
		tools = (List<Map<String, Object>>) second.get("tools");
		assertThat(tools).extracting(tool -> tool.get("name")).containsExactly("c");
		assertThat(second.get("nextCursor")).isNull();
	}

	@Test
	void toolRepositoryBroadcastsChanges() {
		McpChangeFeed changes = new McpChangeFeed();
		InMemoryAsyncToolRepository repository = InMemoryAsyncToolRepository.builder()
			.changeBroadcaster(changes)
			.build();

		StepVerifier.create(changes.changes().take(2))
			.then(() -> repository.add(echoTool()))
			.expectNextMatches(change -> change instanceof ServerChange.ToolsListChanged)
			.then(() -> repository.remove("echo"))
			.expectNextMatches(change -> change instanceof ServerChange.ToolsListChanged)
			.verifyComplete();

		assertThat(repository.list(null, null).block().tools()).isEmpty();
		assertThat(repository.remove("echo")).isFalse();
	}

	@Test
	void repositoriesRejectCursors() {
		InMemoryAsyncToolRepository repository = InMemoryAsyncToolRepository.builder().tool(echoTool()).build();

		// The invalid cursor is an error signal, not a thrown exception.
		StepVerifier.create(repository.list(null, "stale"))
			.expectErrorMatches(error -> error instanceof McpException e
					&& e.error().code() == McpSchema.ErrorCodes.INVALID_PARAMS && e.error().message().contains("stale"))
			.verify();

		McpServer server = server(ToolsFeature.ofAsync(repository));
		Map<String, Object> stale = call(server, McpSchema.METHOD_TOOLS_LIST, params("cursor", "stale"));
		assertThat(stale.get("code")).isEqualTo(McpSchema.ErrorCodes.INVALID_PARAMS);
	}

	@Test
	void toolRepositoryRejectsDuplicates() {
		InMemoryAsyncToolRepository repository = InMemoryAsyncToolRepository.builder().tool(echoTool()).build();
		assertThatThrownBy(() -> repository.add(echoTool())).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("echo");
	}

	@Test
	@SuppressWarnings("unchecked")
	void resourceRepositoryMatchesStaticAndTemplateUris() {
		McpChangeFeed changes = new McpChangeFeed();
		InMemoryAsyncResourceRepository repository = InMemoryAsyncResourceRepository.builder()
			.resource(AsyncResourceRegistration.of(Resource.builder("test://static", "static").build(),
					(ctx, request) -> Mono.just(McpAsyncResponse.result(ReadResourceResult
						.builder(List.of(TextResourceContents.builder(request.uri(), "static-content").build()))
						.build()))))
			.resourceTemplate(AsyncResourceTemplateRegistration.of(
					ResourceTemplate.builder("test://items/{id}", "items").build(),
					(ctx, request) -> Mono.just(McpAsyncResponse.result(ReadResourceResult
						.builder(List.of(TextResourceContents.builder(request.uri(), "item:" + request.uri()).build()))
						.build()))))
			.changeBroadcaster(changes)
			.subscribeSupported(true)
			.build();
		McpServer server = McpServer.builder()
			.serverInfo(Implementation.builder("test", "1").build())
			.feature(ResourcesFeature.ofAsync(repository))
			.build();

		Map<String, Object> staticRead = call(server, McpSchema.METHOD_RESOURCES_READ, params("uri", "test://static"));
		List<Map<String, Object>> contents = (List<Map<String, Object>>) staticRead.get("contents");
		assertThat(contents.get(0).get("text")).isEqualTo("static-content");

		Map<String, Object> templateRead = call(server, McpSchema.METHOD_RESOURCES_READ,
				params("uri", "test://items/42"));
		contents = (List<Map<String, Object>>) templateRead.get("contents");
		assertThat(contents.get(0).get("text")).isEqualTo("item:test://items/42");

		Map<String, Object> unknown = call(server, McpSchema.METHOD_RESOURCES_READ, params("uri", "test://nope"));
		assertThat(unknown.get("code")).isEqualTo(McpSchema.ErrorCodes.INVALID_PARAMS);

		Map<String, Object> templates = call(server, McpSchema.METHOD_RESOURCES_TEMPLATES_LIST, params());
		assertThat((List<?>) templates.get("resourceTemplates")).hasSize(1);
		assertThat(repository.supportsSubscribe()).isTrue();

		assertThatThrownBy(() -> repository.add(AsyncResourceTemplateRegistration
			.of(ResourceTemplate.builder("test://items/{id}", "dup").build(), (ctx, request) -> Mono.empty())))
			.isInstanceOf(IllegalArgumentException.class);

		StepVerifier.create(changes.changes().take(2))
			.then(() -> repository.resourceUpdated("test://static"))
			.expectNextMatches(change -> change instanceof ServerChange.ResourceUpdated updated
					&& updated.uri().equals("test://static"))
			.then(() -> repository.remove("test://items/{id}"))
			.expectNextMatches(change -> change instanceof ServerChange.ResourcesListChanged)
			.verifyComplete();
		assertThat(repository.listTemplates(null, null).block().resourceTemplates()).isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void promptRepositoryGetsPrompts() {
		InMemoryAsyncPromptRepository repository = InMemoryAsyncPromptRepository.builder()
			.prompt(McpAsyncPrompt.of(Prompt.builder("greet").build(),
					(ctx, request) -> Mono.just(McpAsyncResponse.result(GetPromptResult
						.builder(
								List.of(PromptMessage.builder(Role.USER, TextContent.builder("hello").build()).build()))
						.build()))))
			.build();
		McpServer server = McpServer.builder()
			.serverInfo(Implementation.builder("test", "1").build())
			.feature(PromptsFeature.ofAsync(repository))
			.build();

		Map<String, Object> result = call(server, McpSchema.METHOD_PROMPTS_GET, params("name", "greet"));
		List<Map<String, Object>> messages = (List<Map<String, Object>>) result.get("messages");
		assertThat(messages).hasSize(1);
		Map<String, Object> unknown = call(server, McpSchema.METHOD_PROMPTS_GET, params("name", "nope"));
		assertThat(unknown.get("code")).isEqualTo(McpSchema.ErrorCodes.INVALID_PARAMS);
	}

	@Test
	void promptAndTemplateRepositoriesServeTheirCompletions() {
		InMemoryAsyncPromptRepository prompts = InMemoryAsyncPromptRepository.builder()
			.prompt(prompt("greet").withCompletionHandler(
					(ctx, request) -> Mono.just(CompleteResult.of(new CompleteResult.Completion(List.of("a", "b"))))))
			.build();
		InMemoryAsyncResourceRepository resources = InMemoryAsyncResourceRepository.builder()
			.resourceTemplate(AsyncResourceTemplateRegistration
				.of(ResourceTemplate.builder("test://items/{id}", "items").build(), (ctx, request) -> null)
				.withCompletionHandler((ctx, request) -> Mono.just(
						CompleteResult.of(new CompleteResult.Completion(List.of(request.argument().name() + ":42"))))))
			.build();
		McpServer server = McpServer.builder()
			.serverInfo(Implementation.builder("test", "1").build())
			.feature(PromptsFeature.ofAsync(prompts))
			.feature(ResourcesFeature.ofAsync(resources))
			.build();
		assertThat(prompts.supportsCompletion()).isTrue();
		assertThat(resources.supportsCompletion()).isTrue();

		assertThat(values(complete(server, promptRef("greet"), "who"))).containsExactly("a", "b");
		assertThat(values(complete(server, Map.of("type", "ref/resource", "uri", "test://items/{id}"), "id")))
			.containsExactly("id:42");
		assertThat(resources.findTemplate(null, "test://items/{id}").block()).isNotNull();
		assertThat(resources.findTemplate(null, "test://items/42").block()).isNull();

		// A prompt added at runtime brings its own handler; one without completes to
		// no values.
		prompts.add(prompt("later").withCompletionHandler(
				(ctx, request) -> Mono.just(CompleteResult.of(new CompleteResult.Completion(List.of("c"))))));
		prompts.add(prompt("plain"));
		assertThat(values(complete(server, promptRef("later"), "who"))).containsExactly("c");
		assertThat(values(complete(server, promptRef("plain"), "who"))).isEmpty();

		assertThat(complete(server, promptRef("nope"), "who").get("code"))
			.isEqualTo(McpSchema.ErrorCodes.INVALID_PARAMS);
	}

	@Test
	void completionCanBeTurnedOff() {
		InMemoryAsyncPromptRepository prompts = InMemoryAsyncPromptRepository.builder()
			.prompt(prompt("greet").withCompletionHandler(
					(ctx, request) -> Mono.just(CompleteResult.of(new CompleteResult.Completion(List.of("a"))))))
			.completionSupported(false)
			.build();
		InMemoryAsyncResourceRepository resources = InMemoryAsyncResourceRepository.builder()
			.completionSupported(false)
			.build();
		McpServer server = McpServer.builder()
			.serverInfo(Implementation.builder("test", "1").build())
			.feature(PromptsFeature.ofAsync(prompts))
			.feature(ResourcesFeature.ofAsync(resources))
			.build();

		assertThat(prompts.supportsCompletion()).isFalse();
		assertThat(resources.supportsCompletion()).isFalse();
		assertThat(complete(server, promptRef("greet"), "who").get("code"))
			.isEqualTo(McpSchema.ErrorCodes.METHOD_NOT_FOUND);
	}

	private static McpAsyncPrompt prompt(String name) {
		return McpAsyncPrompt.of(Prompt.builder(name).arguments(List.of(PromptArgument.builder("who").build())).build(),
				(ctx, request) -> Mono.just(McpAsyncResponse.result(GetPromptResult.builder(List.of()).build())));
	}

	private static Map<String, Object> promptRef(String name) {
		return Map.of("type", "ref/prompt", "name", name);
	}

	private static Map<String, Object> complete(McpServer server, Map<String, Object> ref, String argument) {
		return call(server, McpSchema.METHOD_COMPLETION_COMPLETE,
				params("ref", ref, "argument", Map.of("name", argument, "value", "")));
	}

	@SuppressWarnings("unchecked")
	private static List<String> values(Map<String, Object> result) {
		return (List<String>) ((Map<String, Object>) result.get("completion")).get("values");
	}

}
