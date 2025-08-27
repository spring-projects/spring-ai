package org.springframework.ai.mcp.client.common.autoconfigure;

import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpSseClientProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStdioClientProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpCompositeClientProperties {

	private final ObjectProvider<McpSseClientProperties> sseClientPropertiesObjectProvider;

	private final ObjectProvider<McpStdioClientProperties> stdioClientPropertiesObjectProvider;

	private final ObjectProvider<McpStreamableHttpClientProperties> streamableHttpClientPropertiesObjectProvider;

	public McpCompositeClientProperties(ObjectProvider<McpSseClientProperties> sseClientPropertiesObjectProvider,
			ObjectProvider<McpStdioClientProperties> stdioClientPropertiesObjectProvider,
			ObjectProvider<McpStreamableHttpClientProperties> streamableHttpClientPropertiesObjectProvider) {
		this.sseClientPropertiesObjectProvider = sseClientPropertiesObjectProvider;
		this.stdioClientPropertiesObjectProvider = stdioClientPropertiesObjectProvider;
		this.streamableHttpClientPropertiesObjectProvider = streamableHttpClientPropertiesObjectProvider;
	}

	public boolean getReturnDirect(String connectionName) {
		McpSseClientProperties sseClientProperties = sseClientPropertiesObjectProvider.getIfAvailable();
		if (sseClientProperties != null && sseClientProperties.getConnections().containsKey(connectionName)) {
			return sseClientProperties.getConnections().get(connectionName).returnDirect();
		}
		McpStdioClientProperties stdioClientProperties = stdioClientPropertiesObjectProvider.getIfAvailable();
		if (stdioClientProperties != null && stdioClientProperties.getConnections().containsKey(connectionName)) {
			return stdioClientProperties.getConnections().get(connectionName).returnDirect();
		}
		McpStreamableHttpClientProperties streamableHttpClientProperties = streamableHttpClientPropertiesObjectProvider
			.getIfAvailable();
		if (streamableHttpClientProperties != null
				&& streamableHttpClientProperties.getConnections().containsKey(connectionName)) {
			return streamableHttpClientProperties.getConnections().get(connectionName).returnDirect();
		}
		return false;
	}

}
