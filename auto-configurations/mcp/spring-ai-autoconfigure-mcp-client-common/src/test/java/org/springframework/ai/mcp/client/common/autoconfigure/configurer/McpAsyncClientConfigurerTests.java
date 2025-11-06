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
		customizer1 = mock(McpAsyncClientCustomizer.class);
		customizer2 = mock(McpAsyncClientCustomizer.class);
		spec = McpClient.async(mock(McpClientTransport.class));
	}

	@Test
	void testConfigureWithCustomizersInOrder() {
		configurer = new McpAsyncClientConfigurer(List.of(customizer1, customizer2));

		McpClient.AsyncSpec result = configurer.configure("asyncClientA", spec);

		InOrder inOrder = inOrder(customizer1, customizer2);
		inOrder.verify(customizer1).customize("asyncClientA", spec);
		inOrder.verify(customizer2).customize("asyncClientA", spec);

		verifyNoMoreInteractions(customizer1, customizer2);
		assertSame(spec, result);
	}

	@Test
	void testConfigureWithoutCustomizers() {
		configurer = new McpAsyncClientConfigurer(Collections.emptyList());

		McpClient.AsyncSpec result = configurer.configure("asyncClientB", spec);

		assertSame(spec, result);
		verifyNoInteractions(customizer1, customizer2);
	}

}
