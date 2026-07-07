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

package org.springframework.ai.mcp.server.common.autoconfigure.observation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;

/**
 * An {@link ObservationFilter} to include MCP server tool call content (input/output) in
 * the observation.
 *
 * @author Michal Grandys
 * @since 2.0.0
 */
public class McpServerToolContentObservationFilter implements ObservationFilter {

	@Override
	public Observation.Context map(Observation.Context context) {
		if (!(context instanceof McpServerToolObservationContext mcpServerToolObservationContext)) {
			return context;
		}

		String toolCallArguments = mcpServerToolObservationContext.getToolCallArguments();
		mcpServerToolObservationContext.addHighCardinalityKeyValue(
				McpServerToolObservationDocumentation.HighCardinalityKeyNames.TOOL_CALL_ARGUMENTS
					.withValue(toolCallArguments));

		String toolCallResult = mcpServerToolObservationContext.getToolCallResult();
		if (toolCallResult != null) {
			mcpServerToolObservationContext.addHighCardinalityKeyValue(
					McpServerToolObservationDocumentation.HighCardinalityKeyNames.TOOL_CALL_RESULT
						.withValue(toolCallResult));
		}

		return mcpServerToolObservationContext;
	}

}
