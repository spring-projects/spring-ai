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

package org.springframework.ai.mcp.client.common.autoconfigure.configurer;

import java.util.List;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit tests for {@link McpSyncClientConfigurer}.
 *
 * @author lance
 */
class McpSyncClientConfigurerTests {

	private McpSyncClientCustomizer customizer1;

	private McpSyncClientCustomizer customizer2;

	private McpClient.SyncSpec spec;

	private McpSyncClientConfigurer configurer;

	@BeforeEach
	void setUp() {
		this.customizer1 = mock(McpSyncClientCustomizer.class);
		this.customizer2 = mock(McpSyncClientCustomizer.class);
		this.spec = McpClient.sync(mock(McpClientTransport.class));
	}

	@Test
	void testConfigureWithCustomizers() {
		this.configurer = new McpSyncClientConfigurer(List.of(this.customizer1, this.customizer2));
		McpClient.SyncSpec result = this.configurer.configure("clientA", this.spec);

		verify(this.customizer1, only()).customize("clientA", this.spec);
		verify(this.customizer2, only()).customize("clientA", this.spec);

		verifyNoMoreInteractions(this.customizer1, this.customizer2);
		Assertions.assertSame(this.spec, result);
	}

	@Test
	void testConfigureWithoutCustomizers() {
		this.configurer = new McpSyncClientConfigurer(List.of());
		McpClient.SyncSpec result = this.configurer.configure("clientB", this.spec);
		Assertions.assertSame(this.spec, result);
	}

	@Test
	void testConfigureWithNullCustomizers() {
		this.configurer = new McpSyncClientConfigurer(null);
		McpClient.SyncSpec result = this.configurer.configure("clientC", this.spec);
		Assertions.assertSame(this.spec, result);
	}

}
