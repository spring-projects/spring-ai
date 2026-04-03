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

package org.springframework.ai.mcp.annotation.adapter;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.util.Assert;

import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpProgressToken;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.common.MetaUtils;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

/**
 * Utility class for adapting between McpPrompt annotations and McpSchema.Prompt objects.
 *
 * @author Christian Tzolov
 * @author Vadzim Shurmialiou
 * @author Craig Walls
 */
public final class PromptAdapter {

	private PromptAdapter() {
	}

	/**
	 * Convert a McpPrompt annotation to a McpSchema.Prompt object.
	 * @param mcpPrompt The McpPrompt annotation
	 * @return The corresponding McpSchema.Prompt object
	 */
	public static McpSchema.Prompt asPrompt(McpPrompt mcpPrompt) {
		Map<String, Object> meta = MetaUtils.getMeta(mcpPrompt.metaProvider());
		return new McpSchema.Prompt(mcpPrompt.name(), mcpPrompt.title(), mcpPrompt.description(), List.of(), meta);
	}

	/**
	 * Convert a McpPrompt annotation to a McpSchema.Prompt object, including argument
	 * information from the method parameters.
	 * @param mcpPrompt The McpPrompt annotation
	 * @param method The method annotated with McpPrompt
	 * @return The corresponding McpSchema.Prompt object with argument information
	 */
	public static McpSchema.Prompt asPrompt(McpPrompt mcpPrompt, Method method) {
		List<McpSchema.PromptArgument> arguments = extractPromptArguments(method);
		Map<String, Object> meta = MetaUtils.getMeta(mcpPrompt.metaProvider());
		return new McpSchema.Prompt(getName(mcpPrompt, method), mcpPrompt.title(), mcpPrompt.description(), arguments,
				meta);
	}

	private static String getName(McpPrompt promptAnnotation, Method method) {
		Assert.notNull(method, "method cannot be null");
		if (promptAnnotation == null || (promptAnnotation.name() == null) || promptAnnotation.name().isEmpty()) {
			return method.getName();
		}
		return promptAnnotation.name();
	}

	/**
	 * Extract prompt arguments from a method's parameters.
	 * @param method The method to extract arguments from
	 * @return A list of PromptArgument objects
	 */
	private static List<McpSchema.PromptArgument> extractPromptArguments(Method method) {
		List<McpSchema.PromptArgument> arguments = new ArrayList<>();
		Parameter[] parameters = method.getParameters();

		for (Parameter parameter : parameters) {
			// Skip special parameter types
			if (McpAsyncServerExchange.class.isAssignableFrom(parameter.getType())
					|| McpSyncServerExchange.class.isAssignableFrom(parameter.getType())
					|| McpTransportContext.class.isAssignableFrom(parameter.getType())
					|| McpSyncRequestContext.class.isAssignableFrom(parameter.getType())
					|| McpAsyncRequestContext.class.isAssignableFrom(parameter.getType())
					|| McpSchema.GetPromptRequest.class.isAssignableFrom(parameter.getType())
					|| java.util.Map.class.isAssignableFrom(parameter.getType())
					|| McpMeta.class.isAssignableFrom(parameter.getType())
					|| parameter.isAnnotationPresent(McpProgressToken.class)) {
				continue;
			}

			// Check if parameter has McpArg annotation
			McpArg mcpArg = parameter.getAnnotation(McpArg.class);
			if (mcpArg != null) {
				String name = !mcpArg.name().isEmpty() ? mcpArg.name() : parameter.getName();
				arguments.add(new McpSchema.PromptArgument(name, mcpArg.description(), mcpArg.required()));
			}
			else {
				// Use parameter name and default values if no annotation
				arguments.add(new McpSchema.PromptArgument(parameter.getName(),
						"Parameter of type " + parameter.getType().getSimpleName(), false));
			}
		}

		return arguments;
	}

}
