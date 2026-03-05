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

package org.springframework.ai.mcp.annotation.method.progress;

import java.lang.reflect.Method;
import java.util.function.Consumer;

import io.modelcontextprotocol.spec.McpSchema.ProgressNotification;

/**
 * Synchronous implementation of a progress method callback.
 *
 * This class creates a Consumer that invokes a method annotated with @McpProgress
 * synchronously when a progress notification is received.
 *
 * @author Christian Tzolov
 */
public final class SyncMcpProgressMethodCallback extends AbstractMcpProgressMethodCallback
		implements Consumer<ProgressNotification> {

	private SyncMcpProgressMethodCallback(Builder builder) {
		super(builder.method, builder.bean);
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

		try {
			// Build arguments for the method call
			Object[] args = this.buildArgs(this.method, null, notification);

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
