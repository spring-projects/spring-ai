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

package org.springframework.ai.mcp.client.common.autoconfigure;

import java.lang.reflect.Field;
import java.util.List;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.McpAsyncClientBiPredicate;
import org.springframework.ai.mcp.McpSyncClientBiPredicate;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration.McpToolCallbackAutoConfigurationCondition;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link McpToolCallbackAutoConfigurationCondition}.
 */
public class McpToolCallbackAutoConfigurationConditionTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestConfiguration.class);

	@Test
	void matchesWhenBothPropertiesAreEnabled() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.enabled=true", "spring.ai.mcp.client.toolcallback.enabled=true")
			.run(context -> assertThat(context).hasBean("testBean"));
	}

	@Test
	void doesNotMatchWhenMcpClientIsDisabled() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.enabled=false", "spring.ai.mcp.client.toolcallback.enabled=true")
			.run(context -> assertThat(context).doesNotHaveBean("testBean"));
	}

	@Test
	void doesNotMatchWhenToolCallbackIsDisabled() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.enabled=true", "spring.ai.mcp.client.toolcallback.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean("testBean"));
	}

	@Test
	void doesNotMatchWhenBothPropertiesAreDisabled() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.enabled=false", "spring.ai.mcp.client.toolcallback.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean("testBean"));
	}

	@Test
	void doesMatchWhenToolCallbackPropertyIsMissing() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.client.enabled=true")
			.run(context -> assertThat(context).hasBean("testBean"));
	}

	@Test
	void doesMatchWhenBothPropertiesAreMissing() {
		this.contextRunner.run(context -> assertThat(context).hasBean("testBean"));
	}

	@Test
	void verifySyncToolCallbackFilterConfiguration() {
		this.contextRunner
			.withUserConfiguration(McpToolCallbackAutoConfiguration.class, McpSyncClientFilterConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.type=SYNC")
			.run(context -> {
				assertThat(context).hasBean("syncClientFilter");
				SyncMcpToolCallbackProvider toolCallbackProvider = context.getBean(SyncMcpToolCallbackProvider.class);
				Field field = SyncMcpToolCallbackProvider.class.getDeclaredField("toolFilter");
				field.setAccessible(true);
				McpSyncClientBiPredicate toolFilter = (McpSyncClientBiPredicate) field.get(toolCallbackProvider);
				McpSyncClient syncClient1 = mock(McpSyncClient.class);
				var clientInfo1 = new McpSchema.Implementation("client1", "1.0.0");
				when(syncClient1.getClientInfo()).thenReturn(clientInfo1);
				McpSchema.Tool tool1 = mock(McpSchema.Tool.class);
				when(tool1.name()).thenReturn("tool1");
				McpSchema.Tool tool2 = mock(McpSchema.Tool.class);
				when(tool2.name()).thenReturn("tool2");
				McpSchema.ListToolsResult listToolsResult1 = mock(McpSchema.ListToolsResult.class);
				when(listToolsResult1.tools()).thenReturn(List.of(tool1, tool2));
				when(syncClient1.listTools()).thenReturn(listToolsResult1);
				assertThat(toolFilter.test(syncClient1, tool1)).isFalse();
				assertThat(toolFilter.test(syncClient1, tool2)).isTrue();
			});
	}

	@Test
	void verifyASyncToolCallbackFilterConfiguration() {
		this.contextRunner
			.withUserConfiguration(McpToolCallbackAutoConfiguration.class, McpAsyncClientFilterConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.type=ASYNC")
			.run(context -> {
				assertThat(context).hasBean("asyncClientFilter");
				AsyncMcpToolCallbackProvider toolCallbackProvider = context.getBean(AsyncMcpToolCallbackProvider.class);
				Field field = AsyncMcpToolCallbackProvider.class.getDeclaredField("toolFilter");
				field.setAccessible(true);
				McpAsyncClientBiPredicate toolFilter = (McpAsyncClientBiPredicate) field.get(toolCallbackProvider);
				McpAsyncClient asyncClient1 = mock(McpAsyncClient.class);
				var clientInfo1 = new McpSchema.Implementation("client1", "1.0.0");
				when(asyncClient1.getClientInfo()).thenReturn(clientInfo1);
				McpSchema.Tool tool1 = mock(McpSchema.Tool.class);
				when(tool1.name()).thenReturn("tool1");
				McpSchema.Tool tool2 = mock(McpSchema.Tool.class);
				when(tool2.name()).thenReturn("tool2");
				McpSchema.ListToolsResult listToolsResult1 = mock(McpSchema.ListToolsResult.class);
				when(listToolsResult1.tools()).thenReturn(List.of(tool1, tool2));
				when(asyncClient1.listTools()).thenReturn(Mono.just(listToolsResult1));
				assertThat(toolFilter.test(asyncClient1, tool1)).isFalse();
				assertThat(toolFilter.test(asyncClient1, tool2)).isTrue();
			});
	}

	@Configuration
	@Conditional(McpToolCallbackAutoConfigurationCondition.class)
	static class TestConfiguration {

		@Bean
		String testBean() {
			return "testBean";
		}

	}

	@Configuration
	static class McpSyncClientFilterConfiguration {

		@Bean
		McpSyncClientBiPredicate syncClientFilter() {
			return new McpSyncClientBiPredicate() {
				@Override
				public boolean test(McpSyncClient mcpSyncClient, McpSchema.Tool tool) {
					if (mcpSyncClient.getClientInfo().name().equals("client1") && tool.name().contains("tool1")) {
						return false;
					}
					return true;
				}
			};
		}

	}

	@Configuration
	static class McpAsyncClientFilterConfiguration {

		@Bean
		McpAsyncClientBiPredicate asyncClientFilter() {
			return new McpAsyncClientBiPredicate() {
				@Override
				public boolean test(McpAsyncClient mcpAsyncClient, McpSchema.Tool tool) {
					if (mcpAsyncClient.getClientInfo().name().equals("client1") && tool.name().contains("tool1")) {
						return false;
					}
					return true;
				}
			};
		}

	}

}
