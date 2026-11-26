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

package org.springframework.ai.mcp.client.common.autoconfigure;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.modern.McpSchema.CacheScope;
import io.modelcontextprotocol.modern.McpSchema.CallToolOutcome;
import io.modelcontextprotocol.modern.McpSchema.CallToolResult;
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.McpSchema.TextContent;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.feature.McpChangeFeed;
import io.modelcontextprotocol.modern.server.feature.McpSyncTool;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import io.modelcontextprotocol.modern.server.transport.StdioMcpTransport;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.modern.InMemorySyncToolRepository;

/**
 * A modern MCP server on the standard streams, started as a process by the tests of the
 * modern STDIO connections. Its {@code echo} tool answers with its {@code text} argument,
 * and its {@code add_tool} tool adds a tool at runtime, which the server reports on a
 * listen stream.
 *
 * @author Christian Tzolov
 */
public final class ModernStdioTestServer {

	private ModernStdioTestServer() {
	}

	/**
	 * The command that starts the server with the Java runtime and the class path of the
	 * running test.
	 * @return the command and its arguments
	 */
	static List<String> command() {
		String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		return List.of(java, "-cp", System.getProperty("java.class.path"), ModernStdioTestServer.class.getName());
	}

	public static void main(String[] args) {
		// The standard output carries the protocol only: whatever else the process
		// prints, such as its log, goes to the standard error.
		PrintStream protocolOut = System.out;
		System.setOut(System.err);

		McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(JsonMapper.shared());
		McpChangeFeed changes = new McpChangeFeed();

		InMemorySyncToolRepository[] tools = new InMemorySyncToolRepository[1];
		tools[0] = InMemorySyncToolRepository.builder()
			.tools(List.of(
					McpSyncTool.of(tool("echo", "Answers with its text argument"),
							(context, request) -> text("echo: " + request.arguments().get("text"))),
					McpSyncTool.of(tool("add_tool", "Adds the tool named 'added'"), (context, request) -> {
						tools[0].add(McpSyncTool.of(tool("added", "Added at runtime"),
								(addedContext, addedRequest) -> text("added")));
						return text("added a tool");
					})))
			.changeBroadcaster(changes)
			.build();

		McpServer server = McpServer.builder()
			.serverInfo(Implementation.builder("modern-stdio-test-server", "1.0.0").build())
			.jsonMapper(jsonMapper)
			// A private listing that a client may keep for a minute: only a client that
			// outlives a request keeps it, and only a listen stream tells it of a change.
			.feature(ToolsFeature.ofSync(tools[0], jsonMapper, McpJsonDefaults.getSchemaValidator(), 60_000L,
					CacheScope.PRIVATE))
			.subscriptions(changes)
			.build();

		// Serves until the standard input ends.
		new StdioMcpTransport(server, jsonMapper, System.in, protocolOut).start().block();
	}

	private static Tool tool(String name, String description) {
		return Tool.builder(name, Map.of("type", "object", "properties", Map.of("text", Map.of("type", "string"))))
			.description(description)
			.build();
	}

	private static McpSyncResponse<CallToolOutcome> text(String text) {
		return McpSyncResponse.result(CallToolResult.builder().addContent(TextContent.builder(text).build()).build());
	}

}
