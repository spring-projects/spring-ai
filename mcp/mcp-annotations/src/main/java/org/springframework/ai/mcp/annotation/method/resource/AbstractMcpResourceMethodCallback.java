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

package org.springframework.ai.mcp.annotation.method.resource;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.util.Assert;
import io.modelcontextprotocol.util.DefaultMcpUriTemplateManagerFactory;
import io.modelcontextprotocol.util.McpUriTemplateManager;
import io.modelcontextprotocol.util.McpUriTemplateManagerFactory;

import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpProgressToken;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.context.DefaultMcpAsyncRequestContext;
import org.springframework.ai.mcp.annotation.context.DefaultMcpSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

/**
 * Abstract base class for creating callbacks around resource methods.
 *
 * This class provides common functionality for both synchronous and asynchronous resource
 * method callbacks. It contains shared logic for method validation, argument building,
 * and other common operations.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Vadzim Shurmialiou
 * @author Craig Walls
 */
public abstract class AbstractMcpResourceMethodCallback {

	/**
	 * Content type of the resource.
	 */
	public enum ContentType {

		/**
		 * Text content type.
		 */
		TEXT,

		/**
		 * Binary blob content type.
		 */
		BLOB

	}

	protected final Method method;

	protected final Object bean;

	protected final String uri;

	protected final String name;

	protected final String description;

	protected final String mimeType;

	protected final List<String> uriVariables;

	protected final McpReadResourceResultConverter resultConverter;

	protected final McpUriTemplateManager uriTemplateManager;

	protected final ContentType contentType;

	protected final Map<String, Object> meta;

	/**
	 * Constructor for AbstractMcpResourceMethodCallback.
	 * @param method The method to create a callback for
	 * @param bean The bean instance that contains the method
	 * @param uri The URI for the resource
	 * @param name The name of the resource (optional)
	 * @param description The description of the resource (optional)
	 * @param mimeType The MIME type of the resource (optional)
	 * @param resultConverter The result converter
	 * @param uriTemplateMangerFactory The URI template manager factory
	 * @param contentType The content type
	 * @param meta The resource metadata to propagate to content-level _meta
	 */
	protected AbstractMcpResourceMethodCallback(Method method, Object bean, String uri, String name, String description,
			String mimeType, McpReadResourceResultConverter resultConverter,
			McpUriTemplateManagerFactory uriTemplateMangerFactory, ContentType contentType, Map<String, Object> meta) {

		Assert.hasText(uri, "URI can't be null or empty!");
		Assert.notNull(method, "Method can't be null!");
		Assert.notNull(bean, "Bean can't be null!");
		Assert.notNull(resultConverter, "Result converter can't be null!");
		Assert.notNull(uriTemplateMangerFactory, "URI template manager factory can't be null!");

		this.method = method;
		this.bean = bean;
		this.uri = uri;
		this.name = name;
		this.description = description;
		this.mimeType = mimeType;
		this.resultConverter = resultConverter;
		this.uriTemplateManager = uriTemplateMangerFactory.create(this.uri);

		this.uriVariables = this.uriTemplateManager.getVariableNames();

		this.contentType = contentType;
		this.meta = meta;
	}

	/**
	 * Validates that the method signature is compatible with the resource callback.
	 * <p>
	 * This method checks that the return type is valid and that the parameters match the
	 * expected pattern based on whether URI variables are present.
	 * @param method The method to validate
	 * @throws IllegalArgumentException if the method signature is not compatible
	 */
	protected void validateMethod(Method method) {
		if (method == null) {
			throw new IllegalArgumentException("Method must not be null");
		}

		this.validateReturnType(method);

		if (this.uriVariables.isEmpty()) {
			this.validateParametersWithoutUriVariables(method);
		}
		else {
			this.validateParametersWithUriVariables(method);
		}
	}

	/**
	 * Validates that the method return type is compatible with the resource callback.
	 * This method should be implemented by subclasses to handle specific return type
	 * validation.
	 * @param method The method to validate
	 * @throws IllegalArgumentException if the return type is not compatible
	 */
	protected abstract void validateReturnType(Method method);

	/**
	 * Validates method parameters when no URI variables are present. This method provides
	 * common validation logic and delegates exchange type checking to subclasses.
	 * @param method The method to validate
	 * @throws IllegalArgumentException if the parameters are not compatible
	 */
	protected void validateParametersWithoutUriVariables(Method method) {
		Parameter[] parameters = method.getParameters();

		// Count parameters excluding @McpProgressToken and McpMeta annotated ones
		int nonSpecialParamCount = 0;

		for (Parameter param : parameters) {
			if (!param.isAnnotationPresent(McpProgressToken.class) && !McpMeta.class.isAssignableFrom(param.getType())
					&& !McpSyncRequestContext.class.isAssignableFrom(param.getType())
					&& !McpAsyncRequestContext.class.isAssignableFrom(param.getType())
					&& !isExchangeOrContextType(param.getType())) {
				nonSpecialParamCount++;
			}
		}

		// Check parameter count - must have at most 2 non-special parameters
		if (nonSpecialParamCount > 2) {
			throw new IllegalArgumentException(
					"Method can have at most 2 input parameters (excluding @McpProgressToken and McpMeta) when no URI variables are present: "
							+ method.getName() + " in " + method.getDeclaringClass().getName() + " has "
							+ nonSpecialParamCount + " non-special parameters");
		}

		// Check parameter types
		boolean hasValidParams = false;
		boolean hasExchangeParam = false;
		boolean hasRequestOrUriParam = false;
		boolean hasMetaParam = false;
		boolean hasRequestContextParam = false;

		for (Parameter param : parameters) {
			// Skip @McpProgressToken annotated parameters
			if (param.isAnnotationPresent(McpProgressToken.class)) {
				continue;
			}

			Class<?> paramType = param.getType();

			if (McpSyncRequestContext.class.isAssignableFrom(paramType)) {
				if (hasRequestContextParam) {
					throw new IllegalArgumentException("Method cannot have more than one request context parameter: "
							+ method.getName() + " in " + method.getDeclaringClass().getName());
				}
				if (McpPredicates.isReactiveReturnType.test(method)) {
					throw new IllegalArgumentException(
							"Sync complete methods should use McpSyncRequestContext instead of McpAsyncRequestContext parameter: "
									+ method.getName() + " in " + method.getDeclaringClass().getName());
				}
				hasRequestContextParam = true;
			}
			else if (McpAsyncRequestContext.class.isAssignableFrom(paramType)) {
				if (hasRequestContextParam) {
					throw new IllegalArgumentException("Method cannot have more than one request context parameter: "
							+ method.getName() + " in " + method.getDeclaringClass().getName());
				}
				if (McpPredicates.isNotReactiveReturnType.test(method)) {
					throw new IllegalArgumentException(
							"Async complete methods should use McpAsyncRequestContext instead of McpSyncRequestContext parameter: "
									+ method.getName() + " in " + method.getDeclaringClass().getName());
				}
				hasRequestContextParam = true;
			}
			else if (McpMeta.class.isAssignableFrom(paramType)) {
				if (hasMetaParam) {
					throw new IllegalArgumentException("Method cannot have more than one McpMeta parameter: "
							+ method.getName() + " in " + method.getDeclaringClass().getName());
				}
				hasMetaParam = true;
			}
			else if (isExchangeOrContextType(paramType)) {
				if (hasExchangeParam) {
					throw new IllegalArgumentException("Method cannot have more than one exchange parameter: "
							+ method.getName() + " in " + method.getDeclaringClass().getName());
				}
				hasExchangeParam = true;
			}
			else if (ReadResourceRequest.class.isAssignableFrom(paramType)
					|| String.class.isAssignableFrom(paramType)) {
				if (hasRequestOrUriParam) {
					throw new IllegalArgumentException(
							"Method cannot have more than one ReadResourceRequest or String parameter: "
									+ method.getName() + " in " + method.getDeclaringClass().getName());
				}
				hasRequestOrUriParam = true;
				hasValidParams = true;
			}
			else {
				throw new IllegalArgumentException(
						"Method parameters must be exchange, ReadResourceRequest, String, McpMeta, or @McpProgressToken when no URI variables are present: "
								+ method.getName() + " in " + method.getDeclaringClass().getName()
								+ " has parameter of type " + paramType.getName());
			}
		}

		if (!hasValidParams && nonSpecialParamCount > 0) {
			throw new IllegalArgumentException(
					"Method must have either ReadResourceRequest or String parameter when no URI variables are present: "
							+ method.getName() + " in " + method.getDeclaringClass().getName());
		}
	}

	protected void validateParamType(Class<?> paramType) {
	}

	/**
	 * Validates method parameters when URI variables are present. This method provides
	 * common validation logic and delegates exchange type checking to subclasses.
	 * @param method The method to validate
	 * @throws IllegalArgumentException if the parameters are not compatible
	 */
	protected void validateParametersWithUriVariables(Method method) {
		Parameter[] parameters = method.getParameters();

		// Count special parameters (exchange, request, progress token, and meta)
		int exchangeParamCount = 0;
		int requestParamCount = 0;
		int progressTokenParamCount = 0;
		int metaParamCount = 0;
		boolean hasRequestContextParam = false;

		for (Parameter param : parameters) {
			if (param.isAnnotationPresent(McpProgressToken.class)) {
				progressTokenParamCount++;
			}
			else {
				Class<?> paramType = param.getType();

				this.validateParamType(paramType);

				if (McpMeta.class.isAssignableFrom(paramType)) {
					metaParamCount++;
				}
				else if (isExchangeOrContextType(paramType)) {
					exchangeParamCount++;
				}
				else if (ReadResourceRequest.class.isAssignableFrom(paramType)) {
					requestParamCount++;
				}
				else if (McpSyncRequestContext.class.isAssignableFrom(paramType)) {
					if (hasRequestContextParam) {
						throw new IllegalArgumentException(
								"Method cannot have more than one request context parameter: " + method.getName()
										+ " in " + method.getDeclaringClass().getName());
					}
					if (McpPredicates.isReactiveReturnType.test(method)) {
						throw new IllegalArgumentException(
								"Sync complete methods should use McpSyncRequestContext instead of McpAsyncRequestContext parameter: "
										+ method.getName() + " in " + method.getDeclaringClass().getName());
					}
					hasRequestContextParam = true;
				}
				else if (McpAsyncRequestContext.class.isAssignableFrom(paramType)) {
					if (hasRequestContextParam) {
						throw new IllegalArgumentException(
								"Method cannot have more than one request context parameter: " + method.getName()
										+ " in " + method.getDeclaringClass().getName());
					}
					if (McpPredicates.isNotReactiveReturnType.test(method)) {
						throw new IllegalArgumentException(
								"Async complete methods should use McpAsyncRequestContext instead of McpSyncRequestContext parameter: "
										+ method.getName() + " in " + method.getDeclaringClass().getName());
					}
					hasRequestContextParam = true;
				}
			}
		}

		// Check if we have more than one exchange parameter
		if (exchangeParamCount > 1) {
			throw new IllegalArgumentException("Method cannot have more than one exchange parameter: "
					+ method.getName() + " in " + method.getDeclaringClass().getName());
		}

		// Check if we have more than one request parameter
		if (requestParamCount > 1) {
			throw new IllegalArgumentException("Method cannot have more than one ReadResourceRequest parameter: "
					+ method.getName() + " in " + method.getDeclaringClass().getName());
		}

		// Check if we have more than one meta parameter
		if (metaParamCount > 1) {
			throw new IllegalArgumentException("Method cannot have more than one McpMeta parameter: " + method.getName()
					+ " in " + method.getDeclaringClass().getName());
		}

		// Calculate how many parameters should be for URI variables
		int requestContextParamCount = hasRequestContextParam ? 1 : 0;
		int specialParamCount = exchangeParamCount + requestParamCount + progressTokenParamCount + metaParamCount
				+ requestContextParamCount;
		int uriVarParamCount = parameters.length - specialParamCount;

		// Check if we have the right number of parameters for URI variables
		if (uriVarParamCount != this.uriVariables.size()) {
			throw new IllegalArgumentException(
					"Method must have parameters for all URI variables. Expected " + this.uriVariables.size()
							+ " URI variable parameters, but found " + uriVarParamCount + ": " + method.getName()
							+ " in " + method.getDeclaringClass().getName() + ". URI variables: " + this.uriVariables);
		}

		// Check that all non-special parameters are String type (for URI variables)
		for (Parameter param : parameters) {
			// Skip @McpProgressToken annotated parameters
			if (param.isAnnotationPresent(McpProgressToken.class)) {
				continue;
			}

			Class<?> paramType = param.getType();
			if (!McpSyncRequestContext.class.isAssignableFrom(paramType)
					&& !McpAsyncRequestContext.class.isAssignableFrom(paramType) && !isExchangeOrContextType(paramType)
					&& !ReadResourceRequest.class.isAssignableFrom(paramType)
					&& !McpMeta.class.isAssignableFrom(paramType) && !String.class.isAssignableFrom(paramType)) {
				throw new IllegalArgumentException("URI variable parameters must be of type String: " + method.getName()
						+ " in " + method.getDeclaringClass().getName() + ", parameter of type " + paramType.getName()
						+ " is not valid");
			}
		}
	}

	protected abstract Object assignExchangeType(Class<?> paramType, Object exchange);

	/**
	 * Builds the arguments array for invoking the method.
	 * <p>
	 * This method constructs an array of arguments based on the method's parameter types
	 * and the available values (exchange, request, URI variables, progress token).
	 * @param method The method to build arguments for
	 * @param exchange The server exchange
	 * @param request The resource request
	 * @param uriVariableValues Map of URI variable names to their values
	 * @return An array of arguments for the method invocation
	 */
	protected Object[] buildArgs(Method method, Object exchange, ReadResourceRequest request,
			Map<String, String> uriVariableValues) {
		Parameter[] parameters = method.getParameters();
		Object[] args = new Object[parameters.length];

		// First, handle @McpProgressToken and McpMeta parameters
		for (int i = 0; i < parameters.length; i++) {
			Class<?> paramType = parameters[i].getType();
			if (parameters[i].isAnnotationPresent(McpProgressToken.class)) {
				// Get progress token from request
				args[i] = request != null ? request.progressToken() : null;
			}
			else if (McpMeta.class.isAssignableFrom(paramType)) {
				// Inject McpMeta with request metadata
				args[i] = request != null ? new McpMeta(request.meta()) : new McpMeta(null);
			}
			else if (McpTransportContext.class.isAssignableFrom(paramType)
					|| McpSyncServerExchange.class.isAssignableFrom(paramType)
					|| McpAsyncServerExchange.class.isAssignableFrom(paramType)) {

				args[i] = this.assignExchangeType(paramType, exchange);
			}
			else if (McpSyncRequestContext.class.isAssignableFrom(paramType)) {
				args[i] = DefaultMcpSyncRequestContext.builder()
					.exchange((McpSyncServerExchange) exchange)
					.request(request)
					.build();
			}
			else if (McpAsyncRequestContext.class.isAssignableFrom(paramType)) {
				args[i] = DefaultMcpAsyncRequestContext.builder()
					.exchange((McpAsyncServerExchange) exchange)
					.request(request)
					.build();
			}
		}

		if (!this.uriVariables.isEmpty()) {
			this.buildArgsWithUriVariables(parameters, args, exchange, request, uriVariableValues);
		}
		else {
			this.buildArgsWithoutUriVariables(parameters, args, exchange, request);
		}

		return args;
	}

	/**
	 * Builds arguments for methods with URI variables. This method provides common
	 * argument building logic for methods with URI variables.
	 * @param parameters The method parameters
	 * @param args The arguments array to populate
	 * @param exchange The server exchange
	 * @param request The resource request
	 * @param uriVariableValues Map of URI variable names to their values
	 */
	protected void buildArgsWithUriVariables(Parameter[] parameters, Object[] args, Object exchange,
			ReadResourceRequest request, Map<String, String> uriVariableValues) {

		// Track which URI variables have been assigned
		List<String> assignedVariables = new ArrayList<>();

		// First pass: assign special parameters (exchange, request, and skip progress
		// token and meta)
		for (int i = 0; i < parameters.length; i++) {
			// Skip if parameter is annotated with @McpProgressToken or is McpMeta
			// (already handled)
			if (parameters[i].isAnnotationPresent(McpProgressToken.class)
					|| McpMeta.class.isAssignableFrom(parameters[i].getType())
					|| isExchangeOrContextType(parameters[i].getType())) {
				continue;
			}

			Class<?> paramType = parameters[i].getType();
			if (ReadResourceRequest.class.isAssignableFrom(paramType)) {
				args[i] = request;
			}
		}

		// Second pass: assign URI variables to the remaining parameters
		int variableIndex = 0;
		for (int i = 0; i < parameters.length; i++) {
			// Skip if parameter is annotated with @McpProgressToken, is McpMeta (already
			// handled)
			// or if it's already assigned (exchange or request)
			if (parameters[i].isAnnotationPresent(McpProgressToken.class)
					|| McpMeta.class.isAssignableFrom(parameters[i].getType()) || args[i] != null) {
				continue;
			}

			// Assign the next URI variable
			if (variableIndex < this.uriVariables.size()) {
				String variableName = this.uriVariables.get(variableIndex);
				args[i] = uriVariableValues.get(variableName);
				assignedVariables.add(variableName);
				variableIndex++;
			}
		}

		// Verify all URI variables were assigned
		if (assignedVariables.size() != this.uriVariables.size()) {
			throw new IllegalArgumentException("Failed to assign all URI variables to method parameters. "
					+ "Assigned: " + assignedVariables + ", Expected: " + this.uriVariables);
		}
	}

	/**
	 * Builds arguments for methods without URI variables. This method provides common
	 * argument building logic for methods without URI variables.
	 * @param parameters The method parameters
	 * @param args The arguments array to populate
	 * @param exchange The server exchange
	 * @param request The resource request
	 */
	protected void buildArgsWithoutUriVariables(Parameter[] parameters, Object[] args, Object exchange,
			ReadResourceRequest request) {
		for (int i = 0; i < parameters.length; i++) {
			// Skip if parameter is annotated with @McpProgressToken or is McpMeta
			// (already handled)
			if (parameters[i].isAnnotationPresent(McpProgressToken.class)
					|| McpMeta.class.isAssignableFrom(parameters[i].getType())
					|| McpSyncRequestContext.class.isAssignableFrom(parameters[i].getType())
					|| McpAsyncRequestContext.class.isAssignableFrom(parameters[i].getType())
					|| isExchangeOrContextType(parameters[i].getType())) {
				continue;
			}

			Parameter param = parameters[i];
			Class<?> paramType = param.getType();

			if (ReadResourceRequest.class.isAssignableFrom(paramType)) {
				args[i] = request;
			}
			else if (String.class.isAssignableFrom(paramType)) {
				args[i] = request.uri();
			}
			else {
				args[i] = null; // For any other parameter types
			}
		}
	}

	/**
	 * Checks if a parameter type is compatible with the exchange type. This method should
	 * be implemented by subclasses to handle specific exchange type checking.
	 * @param paramType The parameter type to check
	 * @return true if the parameter type is compatible with the exchange type, false
	 * otherwise
	 */
	protected abstract boolean isExchangeOrContextType(Class<?> paramType);

	/**
	 * Returns the content type of the resource.
	 * @return the content type
	 */
	public ContentType contentType() {
		return this.contentType;
	}

	/**
	 * Abstract builder for creating McpResourceMethodCallback instances.
	 * <p>
	 * This builder provides a base for constructing callback instances with the required
	 * parameters.
	 *
	 * @param <T> The type of the builder
	 * @param <R> The type of the callback
	 */
	protected abstract static class AbstractBuilder<T extends AbstractBuilder<T, R>, R> {

		protected Method method;

		protected Object bean;

		protected McpReadResourceResultConverter resultConverter;

		protected McpUriTemplateManagerFactory uriTemplateManagerFactory;

		protected ContentType contentType;

		protected String name; // Optional name for the resource

		protected String description; // Optional description for the resource

		protected String mimeType; // Optional MIME type for the resource

		protected String uri; // Resource URI

		protected Map<String, Object> meta; // Resource metadata

		/**
		 * Set the method to create a callback for.
		 * @param method The method to create a callback for
		 * @return This builder
		 */
		@SuppressWarnings("unchecked")
		public T method(Method method) {
			this.method = method;
			return (T) this;
		}

		/**
		 * Set the bean instance that contains the method.
		 * @param bean The bean instance
		 * @return This builder
		 */
		@SuppressWarnings("unchecked")
		public T bean(Object bean) {
			this.bean = bean;
			return (T) this;
		}

		/**
		 * Set the URI for the resource.
		 * @param uri The URI for the resource
		 * @return This builder
		 */
		public T uri(String uri) {
			this.uri = uri;
			return (T) this;
		}

		/**
		 * Set the Mcp Schema resource.
		 * @param resource The resource
		 * @return This builder
		 */
		public T resource(McpSchema.Resource resource) {
			this.uri = resource.uri();
			this.name = resource.name();
			this.description = resource.description();
			this.mimeType = resource.mimeType();
			this.meta = resource.meta();
			return (T) this;
		}

		/**
		 * Set the Mcp Schema resource template.
		 * @param resourceTemplate The resource template
		 * @return This builder
		 */
		public T resource(McpSchema.ResourceTemplate resourceTemplate) {
			this.uri = resourceTemplate.uriTemplate();
			this.name = resourceTemplate.name();
			this.description = resourceTemplate.description();
			this.mimeType = resourceTemplate.mimeType();
			this.meta = resourceTemplate.meta();
			return (T) this;
		}

		/**
		 * Set the result converter.
		 * @param resultConverter The result converter
		 * @return This builder
		 */
		@SuppressWarnings("unchecked")
		public T resultConverter(McpReadResourceResultConverter resultConverter) {
			this.resultConverter = resultConverter;
			return (T) this;
		}

		/**
		 * Set the URI template manager factory.
		 * @param uriTemplateManagerFactory The URI template manager factory
		 * @return This builder
		 */
		@SuppressWarnings("unchecked")
		public T uriTemplateManagerFactory(McpUriTemplateManagerFactory uriTemplateManagerFactory) {
			this.uriTemplateManagerFactory = uriTemplateManagerFactory;
			return (T) this;
		}

		/**
		 * Set the content type.
		 * @param contentType The content type
		 * @return This builder
		 */
		public T contentType(ContentType contentType) {
			this.contentType = contentType;
			return (T) this;
		}

		/**
		 * Set the name of the resource.
		 * @param name The name of the resource
		 * @return This builder
		 */
		public T name(String name) {
			this.name = name;
			return (T) this;
		}

		/**
		 * Set the description of the resource.
		 * @param description The description of the resource
		 * @return This builder
		 */
		public T description(String description) {
			this.description = description;
			return (T) this;
		}

		/**
		 * Set the MIME type of the resource.
		 * @param mimeType The MIME type of the resource
		 * @return This builder
		 */
		public T mimeType(String mimeType) {
			this.mimeType = mimeType;
			return (T) this;
		}

		/**
		 * Validate the builder state.
		 * @throws IllegalArgumentException if the builder state is invalid
		 */
		protected void validate() {
			if (this.method == null) {
				throw new IllegalArgumentException("Method must not be null");
			}
			if (this.bean == null) {
				throw new IllegalArgumentException("Bean must not be null");
			}
			if (this.uri == null || this.uri.isEmpty()) {
				throw new IllegalArgumentException("URI must not be null or empty");
			}
			if (this.uriTemplateManagerFactory == null) {
				this.uriTemplateManagerFactory = new DefaultMcpUriTemplateManagerFactory();
			}
			if (this.mimeType == null) {
				this.mimeType = "text/plain";
			}

			if (this.name == null) {
				this.name = this.method.getName();
			}
		}

		/**
		 * Build the callback.
		 * @return A new callback instance
		 */
		public abstract R build();

	}

}
