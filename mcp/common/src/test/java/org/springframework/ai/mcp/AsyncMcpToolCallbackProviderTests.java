/*
 * Copyright 2025-2025 the original author or authors.
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

import java.time.Duration;
import java.util.List;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncMcpToolCallbackProviderTests {

	@Mock
	private McpAsyncClient mcpClient;

	@Test
	void getToolCallbacksShouldReturnEmptyArrayWhenNoTools() {
		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of());
		when(this.mcpClient.listTools()).thenReturn(Mono.just(listToolsResult));

		AsyncMcpToolCallbackProvider provider = AsyncMcpToolCallbackProvider.builder()
			.mcpClients(this.mcpClient)
			.build();

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).isEmpty();
	}

	@Test
	void getToolCallbacksShouldReturnEmptyArrayWhenNoClients() {
		AsyncMcpToolCallbackProvider provider = AsyncMcpToolCallbackProvider.builder().mcpClients(List.of()).build();

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).isEmpty();
	}

	@Test
	void getToolCallbacksShouldReturnCallbacksForEachTool() {
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		var clientCapabilities = new ClientCapabilities(null, null, null, null);
		when(this.mcpClient.getClientCapabilities()).thenReturn(clientCapabilities);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");

		Tool tool2 = mock(Tool.class);
		when(tool2.name()).thenReturn("tool2");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1, tool2));
		when(this.mcpClient.listTools()).thenReturn(Mono.just(listToolsResult));

		AsyncMcpToolCallbackProvider provider = AsyncMcpToolCallbackProvider.builder()
			.mcpClients(this.mcpClient)
			.build();

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(2);
	}

	@Test
	void getToolCallbacksShouldThrowExceptionForDuplicateToolNames() {
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		var clientCapabilities = new ClientCapabilities(null, null, null, null);
		when(this.mcpClient.getClientCapabilities()).thenReturn(clientCapabilities);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("sameName");

		Tool tool2 = mock(Tool.class);
		when(tool2.name()).thenReturn("sameName");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1, tool2));
		when(this.mcpClient.listTools()).thenReturn(Mono.just(listToolsResult));

		AsyncMcpToolCallbackProvider provider1 = AsyncMcpToolCallbackProvider.builder()
			.toolNamePrefixGenerator(McpToolNamePrefixGenerator.noPrefix())
			.mcpClients(this.mcpClient)
			.build();

		assertThatThrownBy(() -> provider1.getToolCallbacks()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Multiple tools with the same name");

		AsyncMcpToolCallbackProvider provider2 = AsyncMcpToolCallbackProvider.builder()
			.mcpClients(this.mcpClient)
			.build();

		var toolCallbacks = provider2.getToolCallbacks();
		assertThat(toolCallbacks).hasSize(2);
		assertThat(toolCallbacks[0].getToolDefinition().name()).isEqualTo("sameName");
		assertThat(toolCallbacks[1].getToolDefinition().name()).isEqualTo("alt_1_sameName");

	}

	@Test
	void getSameNameToolsButDifferentClientInfoNamesShouldProduceDifferentToolCallbackNames() {
		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("sameName");

		Tool tool2 = mock(Tool.class);
		when(tool2.name()).thenReturn("sameName");

		McpAsyncClient mcpClient1 = mock(McpAsyncClient.class);
		ListToolsResult listToolsResult1 = mock(ListToolsResult.class);
		when(listToolsResult1.tools()).thenReturn(List.of(tool1));
		when(mcpClient1.listTools()).thenReturn(Mono.just(listToolsResult1));

		var clientInfo1 = new Implementation("testClient1", "1.0.0");
		when(mcpClient1.getClientInfo()).thenReturn(clientInfo1);
		var clientCapabilities1 = new ClientCapabilities(null, null, null, null);
		when(mcpClient1.getClientCapabilities()).thenReturn(clientCapabilities1);

		McpAsyncClient mcpClient2 = mock(McpAsyncClient.class);
		ListToolsResult listToolsResult2 = mock(ListToolsResult.class);
		when(listToolsResult2.tools()).thenReturn(List.of(tool2));
		when(mcpClient2.listTools()).thenReturn(Mono.just(listToolsResult2));

		var clientInfo2 = new Implementation("testClient2", "1.0.0");
		when(mcpClient2.getClientInfo()).thenReturn(clientInfo2);
		var clientCapabilities2 = new ClientCapabilities(null, null, null, null);
		when(mcpClient2.getClientCapabilities()).thenReturn(clientCapabilities2);

		AsyncMcpToolCallbackProvider provider = AsyncMcpToolCallbackProvider.builder()
			.mcpClients(mcpClient1, mcpClient2)
			.build();

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(2);
	}

	@Test
	void toolFilterShouldAcceptAllToolsByDefault() {
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		var clientCapabilities = new ClientCapabilities(null, null, null, null);
		when(this.mcpClient.getClientCapabilities()).thenReturn(clientCapabilities);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");

		Tool tool2 = mock(Tool.class);
		when(tool2.name()).thenReturn("tool2");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1, tool2));
		when(this.mcpClient.listTools()).thenReturn(Mono.just(listToolsResult));

		// Using the builder without explicit filter (should use default filter that
		// accepts all)
		AsyncMcpToolCallbackProvider provider = AsyncMcpToolCallbackProvider.builder()
			.mcpClients(this.mcpClient)
			.build();

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(2);
	}

	@Test
	void toolFilterShouldRejectAllToolsWhenConfigured() {
		Tool tool1 = mock(Tool.class);
		Tool tool2 = mock(Tool.class);

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1, tool2));
		when(this.mcpClient.listTools()).thenReturn(Mono.just(listToolsResult));

		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		var clientCapabilities = new ClientCapabilities(null, null, null, null);
		when(this.mcpClient.getClientCapabilities()).thenReturn(clientCapabilities);

		// Create a filter that rejects all tools
		McpToolFilter rejectAllFilter = (client, tool) -> false;

		AsyncMcpToolCallbackProvider provider = AsyncMcpToolCallbackProvider.builder()
			.toolFilter(rejectAllFilter)
			.toolNamePrefixGenerator(new DefaultMcpToolNamePrefixGenerator())
			.mcpClients(this.mcpClient)
			.build();

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).isEmpty();
	}

	@Test
	void toolFilterShouldFilterToolsByNameWhenConfigured() {
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		var clientCapabilities = new ClientCapabilities(null, null, null, null);
		when(this.mcpClient.getClientCapabilities()).thenReturn(clientCapabilities);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");

		Tool tool2 = mock(Tool.class);
		when(tool2.name()).thenReturn("tool2");

		Tool tool3 = mock(Tool.class);
		when(tool3.name()).thenReturn("tool3");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1, tool2, tool3));
		when(this.mcpClient.listTools()).thenReturn(Mono.just(listToolsResult));

		// Create a filter that only accepts tools with names containing "2" or "3"
		McpToolFilter nameFilter = (client, tool) -> tool.name().contains("2") || tool.name().contains("3");

		AsyncMcpToolCallbackProvider provider = AsyncMcpToolCallbackProvider.builder()
			.toolFilter(nameFilter)
			.toolNamePrefixGenerator(new DefaultMcpToolNamePrefixGenerator())
			.mcpClients(this.mcpClient)
			.build();

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(2);
		assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("tool2");
		assertThat(callbacks[1].getToolDefinition().name()).isEqualTo("tool3");
	}

	@Test
	void toolFilterShouldFilterToolsByClientWhenConfigured() {
		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");

		Tool tool2 = mock(Tool.class);
		// Don't stub tool2.name() since it won't be used due to the filter

		McpAsyncClient mcpClient1 = mock(McpAsyncClient.class);
		ListToolsResult listToolsResult1 = mock(ListToolsResult.class);
		when(listToolsResult1.tools()).thenReturn(List.of(tool1));
		when(mcpClient1.listTools()).thenReturn(Mono.just(listToolsResult1));

		var clientInfo1 = new Implementation("testClient1", "1.0.0");
		when(mcpClient1.getClientInfo()).thenReturn(clientInfo1);
		var clientCapabilities1 = new ClientCapabilities(null, null, null, null);
		when(mcpClient1.getClientCapabilities()).thenReturn(clientCapabilities1);

		McpAsyncClient mcpClient2 = mock(McpAsyncClient.class);
		ListToolsResult listToolsResult2 = mock(ListToolsResult.class);
		when(listToolsResult2.tools()).thenReturn(List.of(tool2));
		when(mcpClient2.listTools()).thenReturn(Mono.just(listToolsResult2));

		var clientInfo2 = new Implementation("testClient2", "1.0.0");
		when(mcpClient2.getClientInfo()).thenReturn(clientInfo2);
		var clientCapabilities2 = new ClientCapabilities(null, null, null, null);
		when(mcpClient2.getClientCapabilities()).thenReturn(clientCapabilities2);

		// Create a filter that only accepts tools from client1
		McpToolFilter clientFilter = (mcpConnectionInfo,
				tool) -> mcpConnectionInfo.clientInfo().name().equals("testClient1");

		AsyncMcpToolCallbackProvider provider = AsyncMcpToolCallbackProvider.builder()
			.toolFilter(clientFilter)
			.toolNamePrefixGenerator(new DefaultMcpToolNamePrefixGenerator())
			.mcpClients(mcpClient1, mcpClient2)
			.build();

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(1);
		assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("tool1");
	}

	@Test
	void toolFilterShouldCombineClientAndToolCriteriaWhenConfigured() {
		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("weather");

		Tool tool2 = mock(Tool.class);
		when(tool2.name()).thenReturn("calculator");

		McpAsyncClient weatherClient = mock(McpAsyncClient.class);
		ListToolsResult weatherResult = mock(ListToolsResult.class);
		when(weatherResult.tools()).thenReturn(List.of(tool1, tool2));
		when(weatherClient.listTools()).thenReturn(Mono.just(weatherResult));

		var weatherClientInfo = new Implementation("weather-service", "1.0.0");
		when(weatherClient.getClientInfo()).thenReturn(weatherClientInfo);
		var clientCapabilities = new ClientCapabilities(null, null, null, null);
		when(weatherClient.getClientCapabilities()).thenReturn(clientCapabilities);

		// Create a filter that only accepts weather tools from the weather service
		McpToolFilter complexFilter = (mcpConnectionInfo,
				tool) -> mcpConnectionInfo.clientInfo().name().equals("weather-service")
						&& tool.name().equals("weather");

		AsyncMcpToolCallbackProvider provider = AsyncMcpToolCallbackProvider.builder()
			.toolFilter(complexFilter)
			.toolNamePrefixGenerator(new DefaultMcpToolNamePrefixGenerator())
			.mcpClients(weatherClient)
			.build();

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(1);
		assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("weather");
	}

	@Test
	void asyncToolCallbacksStaticMethodShouldReturnEmptyFluxWhenNoClients() {
		var flux = AsyncMcpToolCallbackProvider.asyncToolCallbacks(List.of());

		StepVerifier.create(flux).expectNextCount(0).verifyComplete();
	}

	@Test
	void asyncToolCallbacksStaticMethodShouldReturnEmptyFluxWhenNullClients() {
		var flux = AsyncMcpToolCallbackProvider.asyncToolCallbacks(null);

		StepVerifier.create(flux).expectNextCount(0).verifyComplete();
	}

	@Test
	void asyncToolCallbacksStaticMethodShouldReturnCallbacks() {
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		var clientCapabilities = new ClientCapabilities(null, null, null, null);
		when(this.mcpClient.getClientCapabilities()).thenReturn(clientCapabilities);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1));
		when(this.mcpClient.listTools()).thenReturn(Mono.just(listToolsResult));

		var flux = AsyncMcpToolCallbackProvider.asyncToolCallbacks(List.of(this.mcpClient));

		StepVerifier.create(flux).expectNextMatches(callback -> callback instanceof ToolCallback).verifyComplete();
	}

	@Test
	void builderShouldSupportToolContextToMcpMetaConverter() {
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		var clientCapabilities = new ClientCapabilities(null, null, null, null);
		when(this.mcpClient.getClientCapabilities()).thenReturn(clientCapabilities);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1));
		when(this.mcpClient.listTools()).thenReturn(Mono.just(listToolsResult));

		ToolContextToMcpMetaConverter customConverter = ToolContextToMcpMetaConverter.defaultConverter();

		AsyncMcpToolCallbackProvider provider = AsyncMcpToolCallbackProvider.builder()
			.mcpClients(this.mcpClient)
			.toolContextToMcpMetaConverter(customConverter)
			.build();

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(1);
	}

	@Test
	void builderShouldSupportMcpClientsAsList() {
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		var clientCapabilities = new ClientCapabilities(null, null, null, null);
		when(this.mcpClient.getClientCapabilities()).thenReturn(clientCapabilities);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1));
		when(this.mcpClient.listTools()).thenReturn(Mono.just(listToolsResult));

		AsyncMcpToolCallbackProvider provider = AsyncMcpToolCallbackProvider.builder()
			.mcpClients(List.of(this.mcpClient))
			.build();

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(1);
	}

	@Test
	void builderShouldSupportMcpClientsAsVarargs() {
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		var clientCapabilities = new ClientCapabilities(null, null, null, null);
		when(this.mcpClient.getClientCapabilities()).thenReturn(clientCapabilities);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1));
		when(this.mcpClient.listTools()).thenReturn(Mono.just(listToolsResult));

		AsyncMcpToolCallbackProvider provider = AsyncMcpToolCallbackProvider.builder()
			.mcpClients(this.mcpClient)
			.build();

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(1);
	}

	@Test
	void builderShouldSupportCustomToolNamePrefixGenerator() {
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		var clientCapabilities = new ClientCapabilities(null, null, null, null);
		when(this.mcpClient.getClientCapabilities()).thenReturn(clientCapabilities);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1));
		when(this.mcpClient.listTools()).thenReturn(Mono.just(listToolsResult));

		McpToolNamePrefixGenerator customGenerator = (mcpConnectionInfo, tool) -> "custom_" + tool.name();

		AsyncMcpToolCallbackProvider provider = AsyncMcpToolCallbackProvider.builder()
			.mcpClients(this.mcpClient)
			.toolNamePrefixGenerator(customGenerator)
			.build();

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(1);
		assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("custom_tool1");
	}

	@Test
	void cacheShouldNotRefreshBeforeTtlExpires() {
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		var clientCapabilities = new ClientCapabilities(null, null, null, null);
		when(this.mcpClient.getClientCapabilities()).thenReturn(clientCapabilities);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1));
		when(this.mcpClient.listTools()).thenReturn(Mono.just(listToolsResult));

		// Use a long TTL to ensure it doesn't expire during the test
		AsyncMcpToolCallbackProvider provider = AsyncMcpToolCallbackProvider.builder()
			.mcpClients(this.mcpClient)
			.cacheTtl(Duration.ofHours(1))
			.build();

		// First call - should fetch tools
		provider.getToolCallbacks();
		// Second call - should use cache (TTL not expired)
		provider.getToolCallbacks();
		// Third call - should use cache (TTL not expired)
		provider.getToolCallbacks();

		// listTools should only be called once since cache should not have expired
		verify(this.mcpClient, times(1)).listTools();
	}

	@Test
	void cacheWithNullTtlShouldNotExpire() {
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		var clientCapabilities = new ClientCapabilities(null, null, null, null);
		when(this.mcpClient.getClientCapabilities()).thenReturn(clientCapabilities);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1));
		when(this.mcpClient.listTools()).thenReturn(Mono.just(listToolsResult));

		// No cacheTtl set (null by default) - should cache indefinitely
		AsyncMcpToolCallbackProvider provider = AsyncMcpToolCallbackProvider.builder()
			.mcpClients(this.mcpClient)
			.build();

		// Multiple calls should all use the cache
		provider.getToolCallbacks();
		provider.getToolCallbacks();
		provider.getToolCallbacks();

		// listTools should only be called once
		verify(this.mcpClient, times(1)).listTools();
	}

	@Test
	void cacheWithZeroTtlShouldNotExpire() {
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		var clientCapabilities = new ClientCapabilities(null, null, null, null);
		when(this.mcpClient.getClientCapabilities()).thenReturn(clientCapabilities);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1));
		when(this.mcpClient.listTools()).thenReturn(Mono.just(listToolsResult));

		// Zero duration should be treated as infinite cache
		AsyncMcpToolCallbackProvider provider = AsyncMcpToolCallbackProvider.builder()
			.mcpClients(this.mcpClient)
			.cacheTtl(Duration.ZERO)
			.build();

		// Multiple calls should all use the cache
		provider.getToolCallbacks();
		provider.getToolCallbacks();
		provider.getToolCallbacks();

		// listTools should only be called once
		verify(this.mcpClient, times(1)).listTools();
	}

	@Test
	void invalidateCacheShouldForceRefreshEvenWithTtl() {
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		var clientCapabilities = new ClientCapabilities(null, null, null, null);
		when(this.mcpClient.getClientCapabilities()).thenReturn(clientCapabilities);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1));
		when(this.mcpClient.listTools()).thenReturn(Mono.just(listToolsResult));

		// Long TTL
		AsyncMcpToolCallbackProvider provider = AsyncMcpToolCallbackProvider.builder()
			.mcpClients(this.mcpClient)
			.cacheTtl(Duration.ofHours(1))
			.build();

		// First call - should fetch tools
		provider.getToolCallbacks();
		verify(this.mcpClient, times(1)).listTools();

		// Invalidate cache explicitly
		provider.invalidateCache();

		// Second call - should refresh because cache was invalidated
		provider.getToolCallbacks();
		verify(this.mcpClient, times(2)).listTools();
	}

}
