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
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import io.modelcontextprotocol.modern.McpSchema.ProgressParams;
import io.modelcontextprotocol.spec.McpSchema.ProgressNotification;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.annotation.common.ModernClientHandlerSupport;

/**
 * Synchronous implementation of a progress method callback.
 *
 * This class creates a Consumer that invokes a method annotated with @McpProgress
 * synchronously when a progress notification is received.
 * <p>
 * The method may take the {@code ProgressNotification} of the legacy protocol or the
 * {@code ProgressParams} of the 2026-07-28 ("modern") revision. Either way the callback
 * accepts the legacy notification; the progress of a modern client is converted to it
 * with {@link ModernClientHandlerSupport#toLegacyProgress}, which loses nothing.
 * <p>
 * The method may take a {@link ToolContext} as an additional parameter. It receives the
 * tool context of the tool call that reports the progress when the caller knows it, as
 * the tool callbacks of a modern connection do, and {@code null} otherwise.
 *
 * @author Christian Tzolov
 */
public final class SyncMcpProgressMethodCallback extends AbstractMcpProgressMethodCallback
		implements Consumer<ProgressNotification> {

	/**
	 * The positions of the progress parameters among the parameters of the method: one
	 * for a notification, three for the progress, the token and the total.
	 */
	private final int[] progressParameterIndexes;

	/** The position of the {@link ToolContext} parameter, or -1 if there is none. */
	private final int toolContextParameterIndex;

	/** Whether the method takes the {@code ProgressParams} of the modern schema. */
	private final boolean modernNotification;

	private final int parameterCount;

	private SyncMcpProgressMethodCallback(Builder builder) {
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

	@Override
	protected void validateReturnType(Method method) {
		// Synchronous methods must return void
		if (!void.class.equals(method.getReturnType())) {
			throw new IllegalArgumentException("Synchronous progress methods must return void: " + method.getName()
					+ " in " + method.getDeclaringClass().getName() + " returns " + method.getReturnType().getName());
		}
	}

	/**
	 * Accepts the {@code ProgressParams} of the modern schema in addition to the
	 * {@code ProgressNotification} of the legacy protocol.
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

	/**
	 * Accept the progress notification and process it.
	 * <p>
	 * This method builds the arguments for the method call and invokes the method.
	 * @param notification The progress notification, must not be null
	 * @throws McpProgressMethodException if there is an error invoking the progress
	 * method
	 * @throws IllegalArgumentException if the notification is null
	 */
	@Override
	public void accept(ProgressNotification notification) {
		if (notification == null) {
			throw new IllegalArgumentException("Notification must not be null");
		}

		Object argument = null;
		if (this.takesNotification()) {
			argument = this.modernNotification ? ModernClientHandlerSupport.toModernProgress(notification)
					: notification;
		}
		this.invoke(argument, notification.progress(), notification.progressToken(), notification.total(), null);
	}

	/**
	 * Accept the progress of a modern client, for a tool call with the given tool
	 * context. The method receives the tool context if it declares a {@link ToolContext}
	 * parameter.
	 * @param progress The modern progress, must not be null
	 * @param toolContext The tool context of the tool call that reports the progress, or
	 * {@code null} if there is none
	 * @throws McpProgressMethodException if there is an error invoking the progress
	 * method
	 * @throws IllegalArgumentException if the progress is null
	 * @since 2.1.0
	 */
	public void acceptModern(ProgressParams progress, @Nullable ToolContext toolContext) {
		if (progress == null) {
			throw new IllegalArgumentException("Progress must not be null");
		}

		Object argument = null;
		if (this.takesNotification()) {
			argument = this.modernNotification ? progress : ModernClientHandlerSupport.toLegacyProgress(progress);
		}
		this.invoke(argument, progress.progress(), progress.progressToken(), progress.total(), toolContext);
	}

	/** Whether the method takes a notification, as opposed to its three values. */
	private boolean takesNotification() {
		return this.progressParameterIndexes.length == 1;
	}

	/**
	 * Invokes the method with the notification, already of the type the method takes, or
	 * with the progress, the token and the total, and with the tool context where the
	 * method declares one.
	 */
	private void invoke(@Nullable Object notification, Double progress, Object progressToken, @Nullable Double total,
			@Nullable ToolContext toolContext) {
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
			this.method.invoke(this.bean, args);
		}
		catch (Exception e) {
			throw new McpProgressMethodException("Error invoking progress method: " + this.method.getName(), e);
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
	 * Builder for creating SyncMcpProgressMethodCallback instances.
	 * <p>
	 * This builder provides a fluent API for constructing SyncMcpProgressMethodCallback
	 * instances with the required parameters.
	 */
	public static class Builder extends AbstractBuilder<Builder, SyncMcpProgressMethodCallback> {

		/**
		 * Build the callback.
		 * @return A new SyncMcpProgressMethodCallback instance
		 */
		@Override
		public SyncMcpProgressMethodCallback build() {
			validate();
			return new SyncMcpProgressMethodCallback(this);
		}

	}

}
