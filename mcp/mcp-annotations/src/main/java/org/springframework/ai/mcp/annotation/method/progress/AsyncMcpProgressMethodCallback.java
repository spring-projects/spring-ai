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

package org.springframework.ai.mcp.annotation.method.progress;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import io.modelcontextprotocol.modern.McpSchema.ProgressParams;
import io.modelcontextprotocol.spec.McpSchema.ProgressNotification;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.annotation.common.ModernClientHandlerSupport;

/**
 * Asynchronous implementation of a progress method callback.
 *
 * This class creates a Function that invokes a method annotated with @McpProgress
 * asynchronously when a progress notification is received, returning a Mono<Void>.
 *
 * @author Christian Tzolov
 */
public final class AsyncMcpProgressMethodCallback extends AbstractMcpProgressMethodCallback
		implements Function<ProgressNotification, Mono<Void>> {

	/**
	 * The positions of the progress parameters: the notification, or the three values.
	 */
	private final int[] progressParameterIndexes;

	/** The position of the {@link ToolContext} parameter, or -1 if there is none. */
	private final int toolContextParameterIndex;

	/** Whether the method takes the progress type of the modern schema. */
	private final boolean modernNotification;

	private final int parameterCount;

	private AsyncMcpProgressMethodCallback(Builder builder) {
		super(builder.method, builder.bean);
		// The shape of the method, which the validation above has accepted.
		Class<?>[] parameterTypes = this.method.getParameterTypes();
		int toolContextIndex = -1;
		boolean modern = false;
		int[] progressIndexes = new int[parameterTypes.length];
		int progressParameters = 0;
		for (int i = 0; i < parameterTypes.length; i++) {
			if (ModernClientHandlerSupport.isToolContextType(parameterTypes[i])) {
				toolContextIndex = i;
			}
			else {
				progressIndexes[progressParameters++] = i;
				modern = modern || ProgressParams.class.isAssignableFrom(parameterTypes[i]);
			}
		}
		this.parameterCount = parameterTypes.length;
		this.progressParameterIndexes = Arrays.copyOf(progressIndexes, progressParameters);
		this.toolContextParameterIndex = toolContextIndex;
		this.modernNotification = modern;
	}

	/**
	 * Accepts the progress type of the modern schema in addition to the legacy
	 * notification.
	 */
	@Override
	protected boolean isNotificationType(Class<?> paramType) {
		return super.isNotificationType(paramType) || ProgressParams.class.isAssignableFrom(paramType);
	}

	/**
	 * Accepts a {@link ToolContext} parameter next to the progress parameters.
	 */
	@Override
	protected void validateParameters(Method method) {
		if (!ModernClientHandlerSupport.takesToolContext(method)) {
			super.validateParameters(method);
			return;
		}
		List<Class<?>> progressTypes = Arrays.stream(method.getParameterTypes())
			.filter(parameterType -> !ModernClientHandlerSupport.isToolContextType(parameterType))
			.toList();
		boolean oneToolContext = method.getParameterCount() - progressTypes.size() == 1;
		boolean notification = progressTypes.size() == 1 && this.isNotificationType(progressTypes.get(0));
		boolean values = progressTypes.size() == 3
				&& (Double.class.isAssignableFrom(progressTypes.get(0))
						|| double.class.isAssignableFrom(progressTypes.get(0)))
				&& String.class.isAssignableFrom(progressTypes.get(1))
				&& String.class.isAssignableFrom(progressTypes.get(2));
		if (!oneToolContext || !(notification || values)) {
			throw new IllegalArgumentException(
					"Method must have either 1 parameter (ProgressNotification) or 3 parameters (Double, String, String), "
							+ "and optionally a ToolContext parameter: " + method.getName() + " in "
							+ method.getDeclaringClass().getName());
		}
	}

	@Override
	protected void validateReturnType(Method method) {
		Class<?> returnType = method.getReturnType();

		// Check if return type is void or Mono<Void>
		if (returnType == void.class) {
			// void is acceptable - we'll wrap it in Mono
			return;
		}

		if (Mono.class.isAssignableFrom(returnType)) {
			// Check if it's Mono<Void>
			Type genericReturnType = method.getGenericReturnType();
			if (genericReturnType instanceof ParameterizedType paramType) {
				Type[] typeArguments = paramType.getActualTypeArguments();
				if (typeArguments.length == 1 && typeArguments[0] == Void.class) {
					// Mono<Void> is acceptable
					return;
				}
				else {
					throw new IllegalArgumentException("Mono return type must be Mono<Void>: " + method.getName()
							+ " in " + method.getDeclaringClass().getName() + " returns " + returnType.getName());
				}
			}
		}

		throw new IllegalArgumentException(
				"Asynchronous progress methods must return void or Mono<Void>: " + method.getName() + " in "
						+ method.getDeclaringClass().getName() + " returns " + returnType.getName());
	}

	/**
	 * Accepts a progress notification and processes it.
	 * @param notification the progress notification
	 * @return a Mono that completes when the method has processed the notification
	 * @throws McpProgressMethodException if there is an error invoking the progress
	 * method
	 */
	@Override
	public Mono<Void> apply(ProgressNotification notification) {
		if (notification == null) {
			return Mono.error(new IllegalArgumentException("Notification must not be null"));
		}
		return Mono.defer(() -> {
			Object argument = null;
			if (this.takesNotification()) {
				argument = this.modernNotification ? ModernClientHandlerSupport.toModernProgress(notification)
						: notification;
			}
			return this.invoke(argument, notification.progress(), notification.progressToken(), notification.total(),
					null);
		});
	}

	/**
	 * Accepts the progress of a modern client, for a tool call with the given tool
	 * context. The method receives the tool context if it declares a {@link ToolContext}
	 * parameter.
	 * @param progress the modern progress
	 * @param toolContext the tool context of the tool call that reports the progress, or
	 * {@code null} if there is none
	 * @return a Mono that completes when the method has processed the progress
	 * @throws McpProgressMethodException if there is an error invoking the progress
	 * method
	 * @since 2.1.0
	 */
	public Mono<Void> acceptModern(ProgressParams progress, @Nullable ToolContext toolContext) {
		if (progress == null) {
			return Mono.error(new IllegalArgumentException("Progress must not be null"));
		}
		return Mono.defer(() -> {
			Object argument = null;
			if (this.takesNotification()) {
				argument = this.modernNotification ? progress : ModernClientHandlerSupport.toLegacyProgress(progress);
			}
			return this.invoke(argument, progress.progress(), progress.progressToken(), progress.total(), toolContext);
		});
	}

	private boolean takesNotification() {
		return this.progressParameterIndexes.length == 1;
	}

	private Mono<Void> invoke(@Nullable Object notification, Double progress, Object progressToken,
			@Nullable Double total, @Nullable ToolContext toolContext) {
		try {
			// Build arguments for the method call
			Object[] args = new Object[this.parameterCount];
			if (this.takesNotification()) {
				args[this.progressParameterIndexes[0]] = notification;
			}
			else {
				args[this.progressParameterIndexes[0]] = progress;
				// The token is a string or a number; a modern client always sends a
				// number.
				args[this.progressParameterIndexes[1]] = String.valueOf(progressToken);
				args[this.progressParameterIndexes[2]] = total != null ? String.valueOf(total) : null;
			}
			if (this.toolContextParameterIndex >= 0) {
				args[this.toolContextParameterIndex] = toolContext;
			}

			// Invoke the method
			this.method.setAccessible(true);
			Object result = this.method.invoke(this.bean, args);

			// A void method has done its work by now.
			return result instanceof Mono<?> monoResult ? monoResult.then() : Mono.empty();
		}
		catch (Exception e) {
			return Mono
				.error(new McpProgressMethodException("Error invoking progress method: " + this.method.getName(), e));
		}
	}

	/**
	 * Create a new builder.
	 * @return A new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for creating AsyncMcpProgressMethodCallback instances.
	 * <p>
	 * This builder provides a fluent API for constructing AsyncMcpProgressMethodCallback
	 * instances with the required parameters.
	 */
	public static class Builder extends AbstractBuilder<Builder, AsyncMcpProgressMethodCallback> {

		/**
		 * Build the callback.
		 * @return A new AsyncMcpProgressMethodCallback instance
		 */
		@Override
		public AsyncMcpProgressMethodCallback build() {
			validate();
			return new AsyncMcpProgressMethodCallback(this);
		}

	}

}
