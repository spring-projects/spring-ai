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

package org.springframework.ai.mcp.annotation.method.logging;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import io.modelcontextprotocol.spec.McpSchema.LoggingLevel;
import io.modelcontextprotocol.spec.McpSchema.LoggingMessageNotification;
import io.modelcontextprotocol.util.Assert;

import org.springframework.ai.mcp.annotation.McpLogging;

/**
 * Abstract base class for creating callbacks around logging consumer methods.
 *
 * This class provides common functionality for both synchronous and asynchronous logging
 * consumer method callbacks. It contains shared logic for method validation, argument
 * building, and other common operations.
 *
 * @author Christian Tzolov
 */
public abstract class AbstractMcpLoggingMethodCallback {

	protected final Method method;

	protected final Object bean;

	/**
	 * Constructor for AbstractMcpLoggingConsumerMethodCallback.
	 * @param method The method to create a callback for
	 * @param bean The bean instance that contains the method
	 */
	protected AbstractMcpLoggingMethodCallback(Method method, Object bean) {
		Assert.notNull(method, "Method can't be null!");
		Assert.notNull(bean, "Bean can't be null!");

		this.method = method;
		this.bean = bean;
		this.validateMethod(this.method);
	}

	/**
	 * Validates that the method signature is compatible with the logging consumer
	 * callback.
	 * <p>
	 * This method checks that the return type is valid and that the parameters match the
	 * expected pattern.
	 * @param method The method to validate
	 * @throws IllegalArgumentException if the method signature is not compatible
	 */
	protected void validateMethod(Method method) {
		if (method == null) {
			throw new IllegalArgumentException("Method must not be null");
		}

		this.validateReturnType(method);
		this.validateParameters(method);
	}

	/**
	 * Validates that the method return type is compatible with the logging consumer
	 * callback. This method should be implemented by subclasses to handle specific return
	 * type validation.
	 * @param method The method to validate
	 * @throws IllegalArgumentException if the return type is not compatible
	 */
	protected abstract void validateReturnType(Method method);

	/**
	 * Validates method parameters. This method provides common validation logic and
	 * delegates exchange type checking to subclasses.
	 * @param method The method to validate
	 * @throws IllegalArgumentException if the parameters are not compatible
	 */
	protected void validateParameters(Method method) {
		Parameter[] parameters = method.getParameters();

		// Check parameter count - must have either 1 or 3 parameters
		if (parameters.length != 1 && parameters.length != 3) {
			throw new IllegalArgumentException(
					"Method must have either 1 parameter (LoggingMessageNotification) or 3 parameters (LoggingLevel, String, String): "
							+ method.getName() + " in " + method.getDeclaringClass().getName() + " has "
							+ parameters.length + " parameters");
		}

		// Check parameter types
		if (parameters.length == 1) {
			// Single parameter must be LoggingMessageNotification
			if (!LoggingMessageNotification.class.isAssignableFrom(parameters[0].getType())) {
				throw new IllegalArgumentException("Single parameter must be of type LoggingMessageNotification: "
						+ method.getName() + " in " + method.getDeclaringClass().getName() + " has parameter of type "
						+ parameters[0].getType().getName());
			}
		}
		else {
			// Three parameters must be LoggingLevel, String, String
			if (!LoggingLevel.class.isAssignableFrom(parameters[0].getType())) {
				throw new IllegalArgumentException("First parameter must be of type LoggingLevel: " + method.getName()
						+ " in " + method.getDeclaringClass().getName() + " has parameter of type "
						+ parameters[0].getType().getName());
			}
			if (!String.class.isAssignableFrom(parameters[1].getType())) {
				throw new IllegalArgumentException("Second parameter must be of type String: " + method.getName()
						+ " in " + method.getDeclaringClass().getName() + " has parameter of type "
						+ parameters[1].getType().getName());
			}
			if (!String.class.isAssignableFrom(parameters[2].getType())) {
				throw new IllegalArgumentException("Third parameter must be of type String: " + method.getName()
						+ " in " + method.getDeclaringClass().getName() + " has parameter of type "
						+ parameters[2].getType().getName());
			}
		}
	}

	/**
	 * Builds the arguments array for invoking the method.
	 * <p>
	 * This method constructs an array of arguments based on the method's parameter types
	 * and the available values (exchange, notification).
	 * @param method The method to build arguments for
	 * @param exchange The server exchange
	 * @param notification The logging message notification
	 * @return An array of arguments for the method invocation
	 */
	protected Object[] buildArgs(Method method, Object exchange, LoggingMessageNotification notification) {
		Parameter[] parameters = method.getParameters();
		Object[] args = new Object[parameters.length];

		if (parameters.length == 1) {
			// Single parameter (LoggingMessageNotification)
			args[0] = notification;
		}
		else {
			// Three parameters (LoggingLevel, String, String)
			args[0] = notification.level();
			args[1] = notification.logger();
			args[2] = notification.data();
		}

		return args;
	}

	/**
	 * Exception thrown when there is an error invoking a logging consumer method.
	 */
	public static class McpLoggingConsumerMethodException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		/**
		 * Constructs a new exception with the specified detail message and cause.
		 * @param message The detail message
		 * @param cause The cause
		 */
		public McpLoggingConsumerMethodException(String message, Throwable cause) {
			super(message, cause);
		}

		/**
		 * Constructs a new exception with the specified detail message.
		 * @param message The detail message
		 */
		public McpLoggingConsumerMethodException(String message) {
			super(message);
		}

	}

	/**
	 * Abstract builder for creating McpLoggingConsumerMethodCallback instances.
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
		 * Set the logging consumer annotation.
		 * @param loggingConsumer The logging consumer annotation
		 * @return This builder
		 */
		@SuppressWarnings("unchecked")
		public T loggingConsumer(McpLogging loggingConsumer) {
			// No additional configuration needed from the annotation at this time
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
		}

		/**
		 * Build the callback.
		 * @return A new callback instance
		 */
		public abstract R build();

	}

}
