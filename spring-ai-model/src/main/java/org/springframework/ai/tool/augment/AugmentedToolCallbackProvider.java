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

package org.springframework.ai.tool.augment;

import java.util.Arrays;
import java.util.function.Consumer;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

/**
 * @author Christian Tzolov
 */

public class AugmentedToolCallbackProvider<T extends Record> implements ToolCallbackProvider {

	private final ToolCallbackProvider delegate;

	private final boolean removeExtraArgumentsAfterProcessing;

	private Consumer<AugmentedArgumentEvent<T>> argumentConsumer;

	private final Class<T> argumentType;

	public AugmentedToolCallbackProvider(Object toolObject, Class<T> argumentType,
			Consumer<AugmentedArgumentEvent<T>> argumentConsumer, boolean removeExtraArgumentsAfterProcessing) {
		this(MethodToolCallbackProvider.builder().toolObjects(toolObject).build(), argumentType, argumentConsumer,
				removeExtraArgumentsAfterProcessing);
	}

	public AugmentedToolCallbackProvider(ToolCallbackProvider delegate, Class<T> argumentType,
			Consumer<AugmentedArgumentEvent<T>> argumentConsumer, boolean removeExtraArgumentsAfterProcessing) {
		this.delegate = delegate;
		this.argumentType = argumentType;
		this.argumentConsumer = argumentConsumer;
		this.removeExtraArgumentsAfterProcessing = removeExtraArgumentsAfterProcessing;
	}

	@Override
	public ToolCallback[] getToolCallbacks() {

		return Arrays.stream(this.delegate.getToolCallbacks())
			.map(toolCallback -> new AugmentedToolCallback<T>(toolCallback, this.argumentType, this.argumentConsumer,
					this.removeExtraArgumentsAfterProcessing))
			.toArray(ToolCallback[]::new);

	}

	/**
	 * Creates a new builder instance
	 * @param <T> the argument type
	 * @return a new builder
	 */
	public static <T extends Record> Builder<T> builder() {
		return new Builder<>();
	}

	/**
	 * Builder for {@link AugmentedToolCallbackProvider}.
	 */
	public static class Builder<T extends Record> {

		private ToolCallbackProvider delegate;

		private boolean removeExtraArgumentsAfterProcessing = true;

		private Consumer<AugmentedArgumentEvent<T>> argumentConsumer;

		private Class<T> argumentType;

		private Object toolObject;

		/**
		 * Sets the delegate ToolCallbackProvider
		 * @param delegate the delegate provider
		 * @return this builder
		 */
		public Builder<T> delegate(ToolCallbackProvider delegate) {
			this.delegate = delegate;
			return this;
		}

		/**
		 * Sets the tool object (alternative to delegate)
		 * @param toolObject the tool object
		 * @return this builder
		 */
		public Builder<T> toolObject(Object toolObject) {
			this.toolObject = toolObject;
			return this;
		}

		/**
		 * Sets the argument type
		 * @param argumentType the class of the argument type
		 * @return this builder
		 */
		public Builder<T> argumentType(Class<T> argumentType) {
			this.argumentType = argumentType;
			return this;
		}

		/**
		 * Sets the argument consumer
		 * @param argumentConsumer the consumer for arguments
		 * @return this builder
		 */
		public Builder<T> argumentConsumer(Consumer<AugmentedArgumentEvent<T>> argumentConsumer) {
			this.argumentConsumer = argumentConsumer;
			return this;
		}

		/**
		 * Sets whether to remove extra arguments after processing
		 * @param removeExtraArgumentsAfterProcessing true to remove extra arguments
		 * @return this builder
		 */
		public Builder<T> removeExtraArgumentsAfterProcessing(boolean removeExtraArgumentsAfterProcessing) {
			this.removeExtraArgumentsAfterProcessing = removeExtraArgumentsAfterProcessing;
			return this;
		}

		/**
		 * Builds the {@link AugmentedToolCallbackProvider} instance.
		 * @return the built instance
		 * @throws IllegalStateException if required fields are not set
		 */
		public AugmentedToolCallbackProvider<T> build() {
			if (this.argumentType == null) {
				throw new IllegalStateException("argumentType is required");
			}
			if (this.argumentConsumer == null) {
				throw new IllegalStateException("argumentConsumer is required");
			}

			if (this.delegate != null && this.toolObject != null) {
				throw new IllegalStateException("Cannot set both delegate and toolObject");
			}

			if (this.delegate == null && this.toolObject == null) {
				throw new IllegalStateException("Either delegate or toolObject must be set");
			}

			if (this.toolObject != null) {
				return new AugmentedToolCallbackProvider<>(this.toolObject, this.argumentType, this.argumentConsumer,
						this.removeExtraArgumentsAfterProcessing);
			}
			else {
				return new AugmentedToolCallbackProvider<>(this.delegate, this.argumentType, this.argumentConsumer,
						this.removeExtraArgumentsAfterProcessing);
			}
		}

	}

}
