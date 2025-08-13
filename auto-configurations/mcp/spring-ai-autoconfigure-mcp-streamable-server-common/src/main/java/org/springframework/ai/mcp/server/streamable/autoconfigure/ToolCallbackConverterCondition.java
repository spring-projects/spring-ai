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

package org.springframework.ai.mcp.server.streamable.autoconfigure;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * @author Christian Tzolov
 */
public class ToolCallbackConverterCondition extends AllNestedConditions {

	public ToolCallbackConverterCondition() {
		super(ConfigurationPhase.PARSE_CONFIGURATION);
	}

	@ConditionalOnProperty(prefix = McpStreamableServerProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	static class McpServerEnabledCondition {

	}

	@ConditionalOnProperty(prefix = McpStreamableServerProperties.CONFIG_PREFIX, name = "tool-callback-converter",
			havingValue = "true", matchIfMissing = true)
	static class ToolCallbackConvertCondition {

	}

}
