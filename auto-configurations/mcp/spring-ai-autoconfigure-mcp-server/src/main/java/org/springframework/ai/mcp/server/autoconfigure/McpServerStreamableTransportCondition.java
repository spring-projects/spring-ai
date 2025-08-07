package org.springframework.ai.mcp.server.autoconfigure;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * This class defines a condition met when the MCP server is enabled, STDIO transport is
 * disabled, and STREAMABLE transport type is selected.
 *
 * @since 1.1.0
 * @author yinh
 */
public class McpServerStreamableTransportCondition extends AllNestedConditions {

	public McpServerStreamableTransportCondition() {
		super(ConfigurationPhase.PARSE_CONFIGURATION);
	}

	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	static class McpServerEnabledCondition {

	}

	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "stdio", havingValue = "false",
			matchIfMissing = true)
	static class StdioDisabledCondition {

	}

	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "transport-type",
			havingValue = "STREAMABLE")
	static class StreamableTransportCondition {

	}

}
