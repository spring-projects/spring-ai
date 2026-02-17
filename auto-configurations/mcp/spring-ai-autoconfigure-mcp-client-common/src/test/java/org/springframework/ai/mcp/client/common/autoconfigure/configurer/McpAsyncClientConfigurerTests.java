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

import java.util.Collections;
import java.util.List;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import org.springframework.ai.mcp.customizer.McpAsyncClientCustomizer;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit tests for {@link McpAsyncClientConfigurer}.
 *
 * @author lance
 */
class McpAsyncClientConfigurerTests {

	private McpAsyncClientCustomizer customizer1;

	private McpAsyncClientCustomizer customizer2;

	private McpClient.AsyncSpec spec;

	private McpAsyncClientConfigurer configurer;

	@BeforeEach
	void setUp() {
		this.customizer1 = mock(McpAsyncClientCustomizer.class);
		this.customizer2 = mock(McpAsyncClientCustomizer.class);
		this.spec = McpClient.async(mock(McpClientTransport.class));
	}

	@Test
	void testConfigureWithCustomizersInOrder() {
		this.configurer = new McpAsyncClientConfigurer(List.of(this.customizer1, this.customizer2));

		McpClient.AsyncSpec result = this.configurer.configure("asyncClientA", this.spec);

		InOrder inOrder = inOrder(this.customizer1, this.customizer2);
		inOrder.verify(this.customizer1).customize("asyncClientA", this.spec);
		inOrder.verify(this.customizer2).customize("asyncClientA", this.spec);

		verifyNoMoreInteractions(this.customizer1, this.customizer2);
		assertSame(this.spec, result);
	}

	@Test
	void testConfigureWithoutCustomizers() {
		this.configurer = new McpAsyncClientConfigurer(Collections.emptyList());

		McpClient.AsyncSpec result = this.configurer.configure("asyncClientB", this.spec);

		assertSame(this.spec, result);
		verifyNoInteractions(this.customizer1, this.customizer2);
	}

}
