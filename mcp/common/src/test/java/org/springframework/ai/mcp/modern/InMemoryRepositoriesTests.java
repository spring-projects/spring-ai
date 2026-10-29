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
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.CallToolResult;
import io.modelcontextprotocol.modern.McpSchema.CompleteResult;
import io.modelcontextprotocol.modern.McpSchema.GetPromptResult;
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.McpSchema.MetaKeys;
import io.modelcontextprotocol.modern.McpSchema.Prompt;
import io.modelcontextprotocol.modern.McpSchema.PromptMessage;
import io.modelcontextprotocol.modern.McpSchema.PromptReference;
import io.modelcontextprotocol.modern.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.modern.McpSchema.Resource;
import io.modelcontextprotocol.modern.McpSchema.ResourceTemplate;
import io.modelcontextprotocol.modern.McpSchema.Role;
import io.modelcontextprotocol.modern.McpSchema.TextContent;
import io.modelcontextprotocol.modern.McpSchema.TextResourceContents;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.McpTransportResponse;
import io.modelcontextprotocol.modern.server.feature.CompletionsFeature;
import io.modelcontextprotocol.modern.server.feature.McpChangeFeed;
import io.modelcontextprotocol.modern.server.feature.PromptsFeature;
import io.modelcontextprotocol.modern.server.feature.ResourcesFeature;
import io.modelcontextprotocol.modern.server.feature.ServerChange;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives the in-memory repositories through a real {@link McpServer}, which is the only
 * way to obtain a {@code McpRequestContext} outside the SDK.
 *
 * @author Christian Tzolov
 */
class InMemoryRepositoriesTests {

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
		McpTransportResponse response = server
			.handleBlocking(McpTransportContext.EMPTY, new JSONRPCRequest(method, 1, params))
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

	private static SyncToolSpecification echoTool() {
		Tool tool = Tool.builder("echo", EMPTY_SCHEMA).description("echo").build();
		return new SyncToolSpecification(tool,
				(ctx, request) -> McpSyncResponse.result(CallToolResult.builder()
					.addContent(TextContent.builder("echo:" + request.arguments().get("text")).build())
					.build()));
	}

	@Test
	@SuppressWarnings("unchecked")
	void toolRepositoryListsAndCallsTools() {
		InMemorySyncToolRepository repository = InMemorySyncToolRepository.builder().tool(echoTool()).build();
		McpServer server = McpServer.builder()
			.serverInfo(Implementation.builder("test", "1").build())
			.feature(ToolsFeature.ofSync(repository))
			.build();

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
	void toolRepositoryBroadcastsChanges() {
		McpChangeFeed changes = new McpChangeFeed();
		InMemorySyncToolRepository repository = InMemorySyncToolRepository.builder().changeBroadcaster(changes).build();

		StepVerifier.create(changes.changes().take(2))
			.then(() -> repository.add(echoTool()))
			.expectNextMatches(change -> change instanceof ServerChange.ToolsListChanged)
			.then(() -> repository.remove("echo"))
			.expectNextMatches(change -> change instanceof ServerChange.ToolsListChanged)
			.verifyComplete();

		assertThat(repository.tools()).isEmpty();
		assertThat(repository.remove("echo")).isFalse();
	}

	@Test
	void repositoriesRejectCursors() {
		InMemorySyncToolRepository repository = InMemorySyncToolRepository.builder().tool(echoTool()).build();
		McpServer server = McpServer.builder()
			.serverInfo(Implementation.builder("test", "1").build())
			.feature(ToolsFeature.ofSync(repository))
			.build();

		Map<String, Object> stale = call(server, McpSchema.METHOD_TOOLS_LIST, params("cursor", "stale"));
		assertThat(stale.get("code")).isEqualTo(McpSchema.ErrorCodes.INVALID_PARAMS);
		assertThat((String) stale.get("message")).contains("stale");
	}

	@Test
	void toolRepositoryRejectsDuplicates() {
		InMemorySyncToolRepository repository = InMemorySyncToolRepository.builder().tool(echoTool()).build();
		assertThatThrownBy(() -> repository.add(echoTool())).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("echo");
	}

	@Test
	@SuppressWarnings("unchecked")
	void resourceRepositoryMatchesStaticAndTemplateUris() {
		InMemorySyncResourceRepository repository = InMemorySyncResourceRepository.builder()
			.resource(new SyncResourceSpecification(Resource.builder("test://static", "static").build(),
					(ctx, request) -> McpSyncResponse.result(ReadResourceResult
						.builder(List.of(TextResourceContents.builder(request.uri(), "static-content").build()))
						.build())))
			.resourceTemplate(new SyncResourceTemplateSpecification(
					ResourceTemplate.builder("test://items/{id}", "items").build(),
					(ctx, request) -> McpSyncResponse.result(ReadResourceResult
						.builder(List.of(TextResourceContents.builder(request.uri(), "item:" + request.uri()).build()))
						.build())))
			.subscribeSupported(true)
			.build();
		McpServer server = McpServer.builder()
			.serverInfo(Implementation.builder("test", "1").build())
			.feature(ResourcesFeature.ofSync(repository))
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

		assertThatThrownBy(() -> repository.add(new SyncResourceTemplateSpecification(
				ResourceTemplate.builder("test://items/{id}", "dup").build(), (ctx, request) -> null)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(repository.remove("test://items/{id}")).isTrue();
		assertThat(repository.resourceTemplates()).isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void promptRepositoryGetsPrompts() {
		InMemorySyncPromptRepository repository = InMemorySyncPromptRepository.builder()
			.prompt(new SyncPromptSpecification(Prompt.builder("greet").build(),
					(ctx, request) -> McpSyncResponse.result(GetPromptResult
						.builder(
								List.of(PromptMessage.builder(Role.USER, TextContent.builder("hello").build()).build()))
						.build())))
			.build();
		McpServer server = McpServer.builder()
			.serverInfo(Implementation.builder("test", "1").build())
			.feature(PromptsFeature.ofSync(repository))
			.build();

		Map<String, Object> result = call(server, McpSchema.METHOD_PROMPTS_GET, params("name", "greet"));
		List<Map<String, Object>> messages = (List<Map<String, Object>>) result.get("messages");
		assertThat(messages).hasSize(1);
		Map<String, Object> unknown = call(server, McpSchema.METHOD_PROMPTS_GET, params("name", "nope"));
		assertThat(unknown.get("code")).isEqualTo(McpSchema.ErrorCodes.INVALID_PARAMS);
	}

	@Test
	@SuppressWarnings("unchecked")
	void completionRepositoryDispatchesByReference() {
		InMemorySyncCompletionRepository repository = InMemorySyncCompletionRepository.builder()
			.completion(new SyncCompletionSpecification(new PromptReference("greet"),
					(ctx, request) -> CompleteResult.of(new CompleteResult.Completion(List.of("a", "b")))))
			.build();
		McpServer server = McpServer.builder()
			.serverInfo(Implementation.builder("test", "1").build())
			.feature(CompletionsFeature.ofSync(repository))
			.build();

		Map<String, Object> result = call(server, McpSchema.METHOD_COMPLETION_COMPLETE, params("ref",
				Map.of("type", "ref/prompt", "name", "greet"), "argument", Map.of("name", "x", "value", "")));
		Map<String, Object> completion = (Map<String, Object>) result.get("completion");
		assertThat((List<String>) completion.get("values")).containsExactly("a", "b");

		Map<String, Object> unknown = call(server, McpSchema.METHOD_COMPLETION_COMPLETE, params("ref",
				Map.of("type", "ref/prompt", "name", "nope"), "argument", Map.of("name", "x", "value", "")));
		assertThat(unknown.get("code")).isEqualTo(McpSchema.ErrorCodes.INVALID_PARAMS);
	}

}
