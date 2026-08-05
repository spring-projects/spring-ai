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

import java.util.List;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the {@code tools/list} pagination performed by {@link McpToolListingUtils}.
 *
 * @author Sukhrob Tokhirov
 */
class McpToolListingUtilsTests {

	private static Tool tool(String name) {
		Tool tool = mock(Tool.class);
		when(tool.name()).thenReturn(name);
		return tool;
	}

	private static ListToolsResult page(String toolName, String nextCursor) {
		return ListToolsResult.builder(List.of(tool(toolName))).nextCursor(nextCursor).build();
	}

	private static List<String> toolNames(List<Tool> tools) {
		return tools.stream().map(Tool::name).toList();
	}

	@Test
	void syncListAllToolsShouldFollowCursorsUntilTheLastPage() {
		ListToolsResult page1 = page("tool1", "cursor1");
		ListToolsResult page2 = page("tool2", "cursor2");
		ListToolsResult page3 = page("tool3", null);

		McpSyncClient mcpClient = mock(McpSyncClient.class);
		when(mcpClient.listTools(McpSchema.FIRST_PAGE)).thenReturn(page1);
		when(mcpClient.listTools("cursor1")).thenReturn(page2);
		when(mcpClient.listTools("cursor2")).thenReturn(page3);

		assertThat(toolNames(McpToolListingUtils.listAllTools(mcpClient))).containsExactly("tool1", "tool2", "tool3");
	}

	@Test
	void syncListAllToolsShouldStopOnEmptyCursor() {
		ListToolsResult page1 = page("tool1", "");

		McpSyncClient mcpClient = mock(McpSyncClient.class);
		when(mcpClient.listTools(McpSchema.FIRST_PAGE)).thenReturn(page1);
		when(mcpClient.listTools("")).thenThrow(new AssertionError("An empty cursor must not be requested"));

		assertThat(toolNames(McpToolListingUtils.listAllTools(mcpClient))).containsExactly("tool1");
		verify(mcpClient, times(1)).listTools(McpSchema.FIRST_PAGE);
		verify(mcpClient, never()).listTools("");
	}

	@Test
	void syncListAllToolsShouldStopOnBlankCursor() {
		ListToolsResult page1 = page("tool1", "   ");

		McpSyncClient mcpClient = mock(McpSyncClient.class);
		when(mcpClient.listTools(McpSchema.FIRST_PAGE)).thenReturn(page1);
		when(mcpClient.listTools("   ")).thenThrow(new AssertionError("A blank cursor must not be requested"));

		assertThat(toolNames(McpToolListingUtils.listAllTools(mcpClient))).containsExactly("tool1");
		verify(mcpClient, never()).listTools("   ");
	}

	@Test
	void syncListAllToolsShouldStopOnRepeatedCursor() {
		ListToolsResult page1 = page("tool1", "cursor1");
		ListToolsResult page2 = page("tool2", "cursor1");

		McpSyncClient mcpClient = mock(McpSyncClient.class);
		when(mcpClient.listTools(McpSchema.FIRST_PAGE)).thenReturn(page1);
		when(mcpClient.listTools("cursor1")).thenReturn(page2);

		assertThat(toolNames(McpToolListingUtils.listAllTools(mcpClient))).containsExactly("tool1", "tool2");
		verify(mcpClient, times(1)).listTools("cursor1");
	}

	@Test
	void asyncListAllToolsShouldFollowCursorsUntilTheLastPage() {
		ListToolsResult page1 = page("tool1", "cursor1");
		ListToolsResult page2 = page("tool2", "cursor2");
		ListToolsResult page3 = page("tool3", null);

		McpAsyncClient mcpClient = mock(McpAsyncClient.class);
		when(mcpClient.listTools(McpSchema.FIRST_PAGE)).thenReturn(Mono.just(page1));
		when(mcpClient.listTools("cursor1")).thenReturn(Mono.just(page2));
		when(mcpClient.listTools("cursor2")).thenReturn(Mono.just(page3));

		assertThat(toolNames(McpToolListingUtils.listAllTools(mcpClient).block())).containsExactly("tool1", "tool2",
				"tool3");
	}

	@Test
	void asyncListAllToolsShouldStopOnEmptyCursor() {
		ListToolsResult page1 = page("tool1", "");

		McpAsyncClient mcpClient = mock(McpAsyncClient.class);
		when(mcpClient.listTools(McpSchema.FIRST_PAGE)).thenReturn(Mono.just(page1));
		when(mcpClient.listTools("")).thenThrow(new AssertionError("An empty cursor must not be requested"));

		assertThat(toolNames(McpToolListingUtils.listAllTools(mcpClient).block())).containsExactly("tool1");
		verify(mcpClient, times(1)).listTools(McpSchema.FIRST_PAGE);
		verify(mcpClient, never()).listTools("");
	}

	@Test
	void asyncListAllToolsShouldStopOnRepeatedCursor() {
		ListToolsResult page1 = page("tool1", "cursor1");
		ListToolsResult page2 = page("tool2", "cursor1");

		McpAsyncClient mcpClient = mock(McpAsyncClient.class);
		when(mcpClient.listTools(McpSchema.FIRST_PAGE)).thenReturn(Mono.just(page1));
		when(mcpClient.listTools("cursor1")).thenReturn(Mono.just(page2));

		assertThat(toolNames(McpToolListingUtils.listAllTools(mcpClient).block())).containsExactly("tool1", "tool2");
		verify(mcpClient, times(1)).listTools("cursor1");
	}

	@Test
	void asyncListAllToolsShouldTrackCursorsPerSubscription() {
		ListToolsResult page1 = page("tool1", "cursor1");
		ListToolsResult page2 = page("tool2", null);

		McpAsyncClient mcpClient = mock(McpAsyncClient.class);
		when(mcpClient.listTools(McpSchema.FIRST_PAGE)).thenReturn(Mono.just(page1));
		when(mcpClient.listTools("cursor1")).thenReturn(Mono.just(page2));

		Mono<List<Tool>> tools = McpToolListingUtils.listAllTools(mcpClient);

		assertThat(toolNames(tools.block())).containsExactly("tool1", "tool2");
		assertThat(toolNames(tools.block())).containsExactly("tool1", "tool2");
	}

}
