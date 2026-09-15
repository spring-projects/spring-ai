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

package org.springframework.ai.mcp.annotation.method.changed.tool;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.ai.mcp.annotation.McpToolListChanged;

/**
 * Class for creating Consumer callbacks around tool list changed consumer methods.
 *
 * This class provides a way to convert methods annotated with {@link McpToolListChanged}
 * into callback functions that can be used to handle tool list change notifications. It
 * supports methods with a single List&lt;McpSchema.Tool&gt; parameter.
 *
 * @author Christian Tzolov
 */
public final class SyncMcpToolListChangedMethodCallback extends AbstractMcpToolListChangedMethodCallback
		implements Consumer<List<McpSchema.Tool>> {

	private SyncMcpToolListChangedMethodCallback(Builder builder) {
		super(builder.method, builder.bean);
	}

	/**
	 * Accept the tool list change notification and process it.
	 * <p>
	 * This method builds the arguments for the method call and invokes the method.
	 * @param updatedTools The updated list of tools, must not be null
	 * @throws McpToolListChangedConsumerMethodException if there is an error invoking the
	 * tool list changed consumer method
	 * @throws IllegalArgumentException if the updatedTools is null
	 */
	@Override
	public void accept(List<McpSchema.Tool> updatedTools) {
		if (updatedTools == null) {
			throw new IllegalArgumentException("Updated tools list must not be null");
		}

		try {
			// Build arguments for the method call
			Object[] args = this.buildArgs(this.method, null, updatedTools);

			// Invoke the method
			this.method.setAccessible(true);
			this.method.invoke(this.bean, args);
		}
		catch (Exception e) {
			throw new McpToolListChangedConsumerMethodException(
					"Error invoking tool list changed consumer method: " + this.method.getName(), e);
		}
	}

	/**
	 * Validates that the method return type is compatible with the tool list changed
	 * consumer callback.
	 * @param method The method to validate
	 * @throws IllegalArgumentException if the return type is not compatible
	 */
	@Override
	protected void validateReturnType(Method method) {
		Class<?> returnType = method.getReturnType();

		if (returnType != void.class) {
			throw new IllegalArgumentException("Method must have void return type: " + method.getName() + " in "
					+ method.getDeclaringClass().getName() + " returns " + returnType.getName());
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
	 * Builder for creating SyncMcpToolListChangedMethodCallback instances.
	 * <p>
	 * This builder provides a fluent API for constructing
	 * SyncMcpToolListChangedMethodCallback instances with the required parameters.
	 */
	public static class Builder extends AbstractBuilder<Builder, SyncMcpToolListChangedMethodCallback> {

		/**
		 * Build the callback.
		 * @return A new SyncMcpToolListChangedMethodCallback instance
		 */
		@Override
		public SyncMcpToolListChangedMethodCallback build() {
			validate();
			return new SyncMcpToolListChangedMethodCallback(this);
		}

	}

}
