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

package org.springframework.ai.mcp.annotation.common;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.modern.McpSchema.ElicitRequest;
import io.modelcontextprotocol.modern.McpSchema.ElicitResult;
import io.modelcontextprotocol.modern.McpSchema.ElicitUrlRequest;
import io.modelcontextprotocol.modern.McpSchema.ProgressParams;
import io.modelcontextprotocol.modern.McpSchema.Prompt;
import io.modelcontextprotocol.modern.McpSchema.Resource;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;
import org.springframework.util.Assert;

/**
 * Lets a client-side handler method serve MCP connections of both protocols, whichever
 * schema types it is written against: those of the legacy protocol
 * ({@code io.modelcontextprotocol.spec.McpSchema}, referred to as {@code McpSchema} in
 * this class) or those of the 2026-07-28 ("modern") revision
 * ({@code io.modelcontextprotocol.modern.McpSchema}).
 * <p>
 * The two families carry the same data, with two exceptions:
 * <ul>
 * <li>A legacy URL elicitation request has an {@code elicitationId} and a modern one has
 * not. A modern URL request can therefore not be handed to a method that takes the legacy
 * types.</li>
 * <li>A legacy elicitation result has a {@code _meta} and a modern one has not, so the
 * {@code _meta} is left out when a result is converted to the modern type.</li>
 * </ul>
 * <p>
 * A handler method may also take the {@link ToolContext} of the tool call that it serves.
 * It is passed for the tool calls of a modern connection, where the handlers are set for
 * each call. It is {@code null} for a legacy connection, whose requests are not tied to a
 * tool call, and for a modern client that is used directly.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class ModernClientHandlerSupport {

	private static final Log logger = LogFactory.getLog(ModernClientHandlerSupport.class);

	private ModernClientHandlerSupport() {
	}

	/**
	 * Whether the type is an elicitation request of the modern schema.
	 * @param type the type to check
	 * @return {@code true} for the modern {@code ElicitRequest} and its two modes
	 */
	public static boolean isModernElicitRequestType(Class<?> type) {
		return ElicitRequest.class.isAssignableFrom(type);
	}

	/**
	 * Whether the type is an elicitation result of the modern schema.
	 * @param type the type to check
	 * @return {@code true} for the modern {@code ElicitResult}
	 */
	public static boolean isModernElicitResultType(Class<?> type) {
		return ElicitResult.class.isAssignableFrom(type);
	}

	/**
	 * Whether a synchronous {@code @McpElicitation} method can answer the form
	 * elicitation requests of a modern connection. It can when its parameter takes a form
	 * request of either schema.
	 * @param method the annotated method
	 * @return {@code true} if the method handles modern form requests
	 */
	public static boolean handlesModernFormElicitation(Method method) {
		return takesFormRequests(elicitRequestParameterType(method));
	}

	/**
	 * Whether a reactive {@code @McpElicitation} method can answer the form elicitation
	 * requests of a modern connection: its parameter takes a form request, of either
	 * schema.
	 * @param method the annotated method
	 * @return {@code true} if the method handles modern form requests
	 */
	public static boolean handlesModernFormElicitationAsync(Method method) {
		return takesFormRequests(asyncElicitRequestParameterType(method));
	}

	/**
	 * Whether a reactive {@code @McpElicitation} method can answer the URL elicitation
	 * requests of a modern connection. It can only when its parameter takes a URL request
	 * of the modern schema, because the legacy one requires an {@code elicitationId}.
	 * @param method the annotated method
	 * @return {@code true} if the method handles modern URL requests
	 */
	public static boolean handlesModernUrlElicitationAsync(Method method) {
		Class<?> requestType = asyncElicitRequestParameterType(method);
		return requestType != null && requestType.isAssignableFrom(ElicitUrlRequest.class);
	}

	private static boolean takesFormRequests(@Nullable Class<?> requestType) {
		return requestType != null && (requestType.isAssignableFrom(ElicitFormRequest.class)
				|| requestType.isAssignableFrom(McpSchema.ElicitFormRequest.class));
	}

	/**
	 * Whether a synchronous {@code @McpElicitation} method can answer the URL elicitation
	 * requests of a modern connection. It can only when its parameter takes a URL request
	 * of the modern schema, because the legacy one requires an {@code elicitationId}.
	 * @param method the annotated method
	 * @return {@code true} if the method handles modern URL requests
	 */
	public static boolean handlesModernUrlElicitation(Method method) {
		Class<?> requestType = elicitRequestParameterType(method);
		return requestType != null && requestType.isAssignableFrom(ElicitUrlRequest.class);
	}

	/**
	 * Whether a synchronous {@code @McpElicitation} method can answer elicitation
	 * requests of a legacy connection. Every synchronous elicitation method can: one that
	 * takes a form request answers the form requests, and one that takes a URL request,
	 * of either schema, answers the URL requests of a client that declares them.
	 * @param method the annotated method
	 * @return {@code true} if the method handles legacy requests
	 */
	public static boolean handlesLegacyElicitation(Method method) {
		return elicitRequestParameterType(method) != null;
	}

	/**
	 * Whether an {@code @McpElicitation} method stands for the form elicitation requests
	 * of the clients it names, of whichever protocol: its parameter takes a form request
	 * of either schema. A method that is not a synchronous elicitation handler, such as a
	 * reactive one, counts as standing for them.
	 * @param method the annotated method
	 * @return {@code true} if no other method may answer form requests for the same
	 * client
	 */
	public static boolean claimsFormElicitation(Method method) {
		Class<?> requestType = elicitRequestParameterType(method);
		return requestType == null || requestType.isAssignableFrom(ElicitFormRequest.class)
				|| requestType.isAssignableFrom(McpSchema.ElicitFormRequest.class);
	}

	/**
	 * Whether an {@code @McpElicitation} method stands for the URL elicitation requests
	 * of the clients it names: its parameter takes a URL request of either schema. A
	 * method that is not a synchronous elicitation handler counts as standing for them.
	 * @param method the annotated method
	 * @return {@code true} if no other method may answer URL requests for the same client
	 */
	public static boolean claimsUrlElicitation(Method method) {
		Class<?> requestType = elicitRequestParameterType(method);
		return requestType == null || requestType.isAssignableFrom(ElicitUrlRequest.class)
				|| requestType.isAssignableFrom(McpSchema.ElicitUrlRequest.class);
	}

	/**
	 * The type of the elicitation request that a synchronous {@code @McpElicitation}
	 * method takes. Such a method takes one elicitation request of either schema and,
	 * optionally, a {@link ToolContext}, and it returns an elicitation result of either
	 * schema or a {@link StructuredElicitResult}.
	 * @param method the annotated method
	 * @return the type of its request parameter, or {@code null} if the method is not a
	 * synchronous elicitation handler
	 */
	public static @Nullable Class<?> elicitRequestParameterType(Method method) {
		Class<?> returnType = method.getReturnType();
		if (!isModernElicitResultType(returnType) && !McpSchema.ElicitResult.class.isAssignableFrom(returnType)
				&& !StructuredElicitResult.class.isAssignableFrom(returnType)) {
			return null;
		}
		return requestParameterType(method);
	}

	/**
	 * The one elicitation request parameter of a method that takes nothing else but,
	 * optionally, a {@link ToolContext}.
	 */
	private static @Nullable Class<?> requestParameterType(Method method) {
		Class<?> requestType = null;
		int toolContexts = 0;
		for (Class<?> parameterType : method.getParameterTypes()) {
			if (isToolContextType(parameterType)) {
				toolContexts++;
			}
			else if (requestType == null && (isModernElicitRequestType(parameterType)
					|| McpSchema.ElicitRequest.class.isAssignableFrom(parameterType))) {
				requestType = parameterType;
			}
			else {
				return null;
			}
		}
		return toolContexts <= 1 ? requestType : null;
	}

	/**
	 * The type of the elicitation request that a reactive {@code @McpElicitation} method
	 * takes. Such a method takes one elicitation request of either schema and,
	 * optionally, a {@link ToolContext}, and it returns a {@code Mono} of an elicitation
	 * result of either schema or of a {@link StructuredElicitResult}.
	 * @param method the annotated method
	 * @return the type of its request parameter, or {@code null} if the method is not a
	 * reactive elicitation handler
	 */
	public static @Nullable Class<?> asyncElicitRequestParameterType(Method method) {
		if (!Mono.class.isAssignableFrom(method.getReturnType())) {
			return null;
		}
		return requestParameterType(method);
	}

	/**
	 * Whether the type is the {@link ToolContext} that a handler method may take.
	 * @param type the type to check
	 * @return {@code true} for {@code ToolContext}
	 */
	public static boolean isToolContextType(Class<?> type) {
		return ToolContext.class.isAssignableFrom(type);
	}

	/**
	 * Whether the method takes a {@link ToolContext}.
	 * @param method the method to check
	 * @return {@code true} if one of its parameters is a {@code ToolContext}
	 */
	public static boolean takesToolContext(Method method) {
		for (Class<?> parameterType : method.getParameterTypes()) {
			if (isToolContextType(parameterType)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Converts a legacy elicitation request to the modern type. The {@code elicitationId}
	 * of a URL request is left out.
	 * @param request the legacy request
	 * @return the modern request
	 */
	public static ElicitRequest toModernElicitRequest(McpSchema.ElicitRequest request) {
		Assert.notNull(request, "request must not be null");
		if (request instanceof McpSchema.ElicitFormRequest form) {
			return new ElicitFormRequest(form.message(), form.requestedSchema(), form.meta());
		}
		if (request instanceof McpSchema.ElicitUrlRequest url) {
			return new ElicitUrlRequest(url.message(), url.url(), url.meta());
		}
		throw new IllegalArgumentException("Unsupported elicitation request: " + request.getClass().getName());
	}

	/**
	 * Converts a modern form elicitation request to the legacy type.
	 * @param request the modern request
	 * @return the legacy request
	 * @throws IllegalArgumentException for a URL request, which has no
	 * {@code elicitationId}
	 */
	public static McpSchema.ElicitRequest toLegacyElicitRequest(ElicitRequest request) {
		Assert.notNull(request, "request must not be null");
		if (request instanceof ElicitFormRequest form) {
			return new McpSchema.ElicitFormRequest(form.message(), form.requestedSchema(), form.meta());
		}
		throw new IllegalArgumentException("A modern URL elicitation request has no elicitationId and cannot be "
				+ "converted to the legacy type; handle it with a method that takes the modern ElicitUrlRequest");
	}

	/**
	 * Converts a legacy elicitation result to the modern type. The {@code _meta} of the
	 * result is left out.
	 * @param result the legacy result
	 * @return the modern result
	 */
	public static ElicitResult toModernElicitResult(McpSchema.ElicitResult result) {
		Assert.notNull(result, "result must not be null");
		return new ElicitResult(ElicitResult.Action.valueOf(result.action().name()), result.content());
	}

	/**
	 * Converts a modern elicitation result to the legacy type.
	 * @param result the modern result
	 * @return the legacy result
	 */
	public static McpSchema.ElicitResult toLegacyElicitResult(ElicitResult result) {
		Assert.notNull(result, "result must not be null");
		return new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.valueOf(result.action().name()),
				result.content(), null);
	}

	/**
	 * Converts a legacy progress notification to the modern type.
	 * @param notification the legacy notification
	 * @return the modern progress
	 */
	public static ProgressParams toModernProgress(McpSchema.ProgressNotification notification) {
		Assert.notNull(notification, "notification must not be null");
		return new ProgressParams(notification.progressToken(), notification.progress(), notification.total(),
				notification.message(), notification.meta());
	}

	/**
	 * Converts a modern progress to the legacy notification type.
	 * @param progress the modern progress
	 * @return the legacy notification
	 */
	public static McpSchema.ProgressNotification toLegacyProgress(ProgressParams progress) {
		Assert.notNull(progress, "progress must not be null");
		return new McpSchema.ProgressNotification(progress.progressToken(), progress.progress(), progress.total(),
				progress.message(), progress.meta());
	}

	/**
	 * Converts the tools of the legacy schema to those of the modern schema.
	 * @param tools the legacy tools
	 * @return the modern tools
	 */
	public static List<Tool> toModernTools(List<McpSchema.Tool> tools) {
		return convert(tools, Tool.class);
	}

	/**
	 * Converts the tools of the modern schema to those of the legacy schema.
	 * @param tools the modern tools
	 * @return the legacy tools
	 */
	public static List<McpSchema.Tool> toLegacyTools(List<Tool> tools) {
		return convert(tools, McpSchema.Tool.class);
	}

	/**
	 * Converts the prompts of the legacy schema to those of the modern schema.
	 * @param prompts the legacy prompts
	 * @return the modern prompts
	 */
	public static List<Prompt> toModernPrompts(List<McpSchema.Prompt> prompts) {
		return convert(prompts, Prompt.class);
	}

	/**
	 * Converts the prompts of the modern schema to those of the legacy schema.
	 * @param prompts the modern prompts
	 * @return the legacy prompts
	 */
	public static List<McpSchema.Prompt> toLegacyPrompts(List<Prompt> prompts) {
		return convert(prompts, McpSchema.Prompt.class);
	}

	/**
	 * Converts the resources of the legacy schema to those of the modern schema.
	 * @param resources the legacy resources
	 * @return the modern resources
	 */
	public static List<Resource> toModernResources(List<McpSchema.Resource> resources) {
		return convert(resources, Resource.class);
	}

	/**
	 * Converts the resources of the modern schema to those of the legacy schema.
	 * @param resources the modern resources
	 * @return the legacy resources
	 */
	public static List<McpSchema.Resource> toLegacyResources(List<Resource> resources) {
		return convert(resources, McpSchema.Resource.class);
	}

	/**
	 * Converts the definitions of one schema to those of the other one. Both describe the
	 * same JSON, so a definition is converted through it; what only one of the two
	 * schemas knows is left out, and so is a definition that the other schema does not
	 * accept.
	 */
	private static <T> List<T> convert(List<?> definitions, Class<T> targetType) {
		Assert.notNull(definitions, "definitions must not be null");
		McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();
		List<T> converted = new ArrayList<>(definitions.size());
		for (Object definition : definitions) {
			try {
				converted.add(jsonMapper.convertValue(definition, targetType));
			}
			catch (RuntimeException ex) {
				// The two schemas do not accept the same values everywhere, for example
				// an empty name. One such definition must not lose the others.
				logger.warn("Leaving out a definition that cannot be converted to " + targetType.getName() + ": "
						+ definition, ex);
			}
		}
		return converted;
	}

}
