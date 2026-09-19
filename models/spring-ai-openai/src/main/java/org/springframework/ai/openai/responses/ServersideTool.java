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

package org.springframework.ai.openai.responses;

import java.util.List;
import java.util.Map;

import com.openai.core.JsonValue;
import com.openai.models.responses.FileSearchTool;
import com.openai.models.responses.Tool;
import com.openai.models.responses.WebSearchTool;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * A tool that OpenAI executes server-side, inside the same request that generates the
 * response.
 * <p>
 * Server-side tools are not {@link org.springframework.ai.tool.ToolCallback
 * ToolCallbacks}: there is nothing for the application to run, and their calls are never
 * surfaced as {@link org.springframework.ai.chat.messages.AssistantMessage.ToolCall tool
 * calls} - they appear in the turn transcript and, summarized, under
 * {@link OpenAiResponsesMetadata#SERVERSIDE_TOOL_CALLS} on the generation metadata.
 * <p>
 * {@link Raw} is the escape hatch for tools OpenAI ships before Spring AI types them.
 *
 * @author Dimitar Proynov
 * @since 2.1.0
 */
public sealed interface ServersideTool {

	/**
	 * Convert this tool to the SDK request representation.
	 */
	Tool toTool();

	/**
	 * Search the public web. See the
	 * <a href="https://platform.openai.com/docs/guides/tools-web-search">web search
	 * guide</a>.
	 *
	 * @param searchContextSize one of {@code low}, {@code medium}, {@code high}, or
	 * {@code null} for the provider default
	 * @param allowedDomains restricts results to these domains, or {@code null} for no
	 * restriction
	 */
	record WebSearch(@Nullable String searchContextSize,
			@Nullable List<String> allowedDomains) implements ServersideTool {

		public static WebSearch of() {
			return new WebSearch(null, null);
		}

		@Override
		public Tool toTool() {
			WebSearchTool.Builder builder = WebSearchTool.builder().type(WebSearchTool.Type.WEB_SEARCH);
			if (this.searchContextSize != null) {
				builder.searchContextSize(WebSearchTool.SearchContextSize.of(this.searchContextSize));
			}
			if (!CollectionUtils.isEmpty(this.allowedDomains)) {
				builder.filters(WebSearchTool.Filters.builder().allowedDomains(this.allowedDomains).build());
			}
			return Tool.ofWebSearch(builder.build());
		}

	}

	/**
	 * Search vector stores hosted at OpenAI.
	 *
	 * @param vectorStoreIds the vector stores to search; at least one is required
	 * @param maxNumResults maximum number of results, or {@code null} for the provider
	 * default
	 */
	record FileSearch(List<String> vectorStoreIds, @Nullable Integer maxNumResults) implements ServersideTool {

		public FileSearch {
			Assert.notEmpty(vectorStoreIds, "vectorStoreIds must not be empty");
		}

		public static FileSearch of(String... vectorStoreIds) {
			return new FileSearch(List.of(vectorStoreIds), null);
		}

		@Override
		public Tool toTool() {
			FileSearchTool.Builder builder = FileSearchTool.builder().vectorStoreIds(this.vectorStoreIds);
			if (this.maxNumResults != null) {
				builder.maxNumResults(this.maxNumResults.longValue());
			}
			return Tool.ofFileSearch(builder.build());
		}

	}

	/**
	 * Run Python in a sandboxed container.
	 *
	 * @param containerId an existing container id, or {@code null} to let OpenAI create
	 * one per request
	 */
	record CodeInterpreter(@Nullable String containerId) implements ServersideTool {

		public static CodeInterpreter of() {
			return new CodeInterpreter(null);
		}

		@Override
		public Tool toTool() {
			Tool.CodeInterpreter.Container container = this.containerId != null
					? Tool.CodeInterpreter.Container.ofString(this.containerId)
					: Tool.CodeInterpreter.Container.ofCodeInterpreterToolAuto(
							Tool.CodeInterpreter.Container.CodeInterpreterToolAuto.builder().build());
			return Tool.ofCodeInterpreter(Tool.CodeInterpreter.builder().container(container).build());
		}

	}

	/**
	 * Call tools on a remote MCP server from inside OpenAI's request.
	 * <p>
	 * The approval round-trip is out of scope: {@code requireApproval} should be
	 * {@code never} unless the application inspects
	 * {@link OpenAiResponsesMetadata#SERVERSIDE_TOOL_CALLS} on the generation metadata
	 * itself, because an {@code mcp_approval_request} left unanswered ends the turn
	 * without running the tool.
	 *
	 * @param serverLabel the label the model uses to address the server
	 * @param serverUrl the server URL, or {@code null} when using {@code connectorId}
	 * @param connectorId an OpenAI-managed connector id, or {@code null}
	 * @param allowedTools restricts the callable tools, or {@code null} for all of them
	 * @param headers headers to send to the MCP server, e.g. authorization
	 * @param requireApproval {@code always} or {@code never}, or {@code null} for the
	 * provider default
	 */
	record Mcp(String serverLabel, @Nullable String serverUrl, @Nullable String connectorId,
			@Nullable List<String> allowedTools, @Nullable Map<String, String> headers,
			@Nullable String requireApproval) implements ServersideTool {

		public Mcp {
			Assert.hasText(serverLabel, "serverLabel must not be empty");
		}

		public static Mcp of(String serverLabel, String serverUrl) {
			return new Mcp(serverLabel, serverUrl, null, null, null, "never");
		}

		@Override
		public Tool toTool() {
			Tool.Mcp.Builder builder = Tool.Mcp.builder().serverLabel(this.serverLabel);
			if (this.serverUrl != null) {
				builder.serverUrl(this.serverUrl);
			}
			if (this.connectorId != null) {
				builder.connectorId(Tool.Mcp.ConnectorId.of(this.connectorId));
			}
			if (!CollectionUtils.isEmpty(this.allowedTools)) {
				builder.allowedToolsOfMcp(this.allowedTools);
			}
			if (!CollectionUtils.isEmpty(this.headers)) {
				Tool.Mcp.Headers.Builder headersBuilder = Tool.Mcp.Headers.builder();
				this.headers.forEach((key, value) -> headersBuilder.putAdditionalProperty(key, JsonValue.from(value)));
				builder.headers(headersBuilder.build());
			}
			if (this.requireApproval != null) {
				builder.requireApproval(Tool.Mcp.RequireApproval.McpToolApprovalSetting.of(this.requireApproval));
			}
			return Tool.ofMcp(builder.build());
		}

	}

	/**
	 * Generate images. Generated images are surfaced as
	 * {@link org.springframework.ai.content.Media} on the assistant message.
	 *
	 * @param model the image model, or {@code null} for the provider default
	 * @param size e.g. {@code 1024x1024}, or {@code null} for the provider default
	 */
	record ImageGeneration(@Nullable String model, @Nullable String size) implements ServersideTool {

		public static ImageGeneration of() {
			return new ImageGeneration(null, null);
		}

		@Override
		public Tool toTool() {
			Tool.ImageGeneration.Builder builder = Tool.ImageGeneration.builder();
			if (this.model != null) {
				builder.model(this.model);
			}
			if (this.size != null) {
				builder.size(this.size);
			}
			return Tool.ofImageGeneration(builder.build());
		}

	}

	/**
	 * A tool declared as the raw request JSON, for tools this class does not type yet.
	 *
	 * @param tool the tool object, e.g. {@code Map.of("type", "local_shell")}
	 */
	record Raw(Map<String, Object> tool) implements ServersideTool {

		public Raw {
			Assert.notEmpty(tool, "tool must not be empty");
		}

		@Override
		public Tool toTool() {
			Tool converted = JsonValue.from(this.tool).convert(Tool.class);
			Assert.state(converted != null, () -> "Could not convert server-side tool declaration: " + this.tool);
			return converted;
		}

	}

}
