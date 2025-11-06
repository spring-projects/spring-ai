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
		customizer1 = mock(McpSyncClientCustomizer.class);
		customizer2 = mock(McpSyncClientCustomizer.class);
		spec = McpClient.sync(mock(McpClientTransport.class));
	}

	@Test
	void testConfigureWithCustomizers() {
		configurer = new McpSyncClientConfigurer(List.of(customizer1, customizer2));
		McpClient.SyncSpec result = configurer.configure("clientA", spec);

		verify(customizer1, only()).customize("clientA", spec);
		verify(customizer2, only()).customize("clientA", spec);

		verifyNoMoreInteractions(customizer1, customizer2);
		Assertions.assertSame(spec, result);
	}

	@Test
	void testConfigureWithoutCustomizers() {
		configurer = new McpSyncClientConfigurer(List.of());
		McpClient.SyncSpec result = configurer.configure("clientB", spec);
		Assertions.assertSame(spec, result);
	}

	@Test
	void testConfigureWithNullCustomizers() {
		configurer = new McpSyncClientConfigurer(null);
		McpClient.SyncSpec result = configurer.configure("clientC", spec);
		Assertions.assertSame(spec, result);
	}

}
