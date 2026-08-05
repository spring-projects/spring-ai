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

package org.springframework.ai.mcp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import org.springframework.util.StringUtils;

/**
 * Internal helpers that page through the tools exposed by an MCP server.
 *
 * <p>
 * The MCP specification terminates pagination when a {@code tools/list} response omits
 * the {@code nextCursor} field. Servers that instead return an empty or repeated cursor
 * would make a naive pagination loop run forever, so the traversal here stops on a blank
 * cursor and on any cursor that has already been requested.
 *
 * @author Sukhrob Tokhirov
 */
final class McpToolListingUtils {

	private static final Logger logger = LoggerFactory.getLogger(McpToolListingUtils.class);

	private McpToolListingUtils() {
	}

	static List<Tool> listAllTools(McpSyncClient mcpClient) {
		List<Tool> tools = new ArrayList<>();
		Set<String> requestedCursors = new HashSet<>();
		String cursor = McpSchema.FIRST_PAGE;
		while (true) {
			ListToolsResult result = mcpClient.listTools(cursor);
			tools.addAll(result.tools());
			cursor = nextCursor(result, requestedCursors);
			if (cursor == null) {
				return tools;
			}
		}
	}

	static Mono<List<Tool>> listAllTools(McpAsyncClient mcpClient) {
		return Mono.defer(() -> {
			Set<String> requestedCursors = ConcurrentHashMap.newKeySet();
			return mcpClient.listTools(McpSchema.FIRST_PAGE).expand(result -> {
				String cursor = nextCursor(result, requestedCursors);
				return cursor != null ? mcpClient.listTools(cursor) : Mono.empty();
			}).flatMapIterable(ListToolsResult::tools).collectList();
		});
	}

	/**
	 * Returns the cursor to request next, or {@code null} when the traversal must stop.
	 */
	private static @Nullable String nextCursor(ListToolsResult result, Set<String> requestedCursors) {
		String nextCursor = result.nextCursor();
		if (!StringUtils.hasText(nextCursor)) {
			return null;
		}
		if (!requestedCursors.add(nextCursor)) {
			logger.warn("Stopping tools/list pagination: cursor '{}' was already requested. "
					+ "Some tools may be missing.", nextCursor);
			return null;
		}
		return nextCursor;
	}

}
