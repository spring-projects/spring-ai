/*
* Copyright 2025 - 2025 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springframework.ai.tool;

import java.util.List;

import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.util.Assert;

/**
 * A simple implementation of {@link ToolCallbackProvider} that maintains a static array
 * of {@link FunctionCallback} objects. This provider is immutable after construction and
 * provides a straightforward way to supply a fixed set of tool callbacks to AI models.
 *
 * <p>
 * This implementation is thread-safe as it maintains an immutable array of callbacks that
 * is set during construction and cannot be modified afterwards.
 *
 * <p>
 * Example usage: <pre>{@code
 * FunctionCallback callback1 = new MyFunctionCallback();
 * FunctionCallback callback2 = new AnotherFunctionCallback();
 *
 * // Create provider with varargs constructor
 * ToolCallbackProvider provider1 = new StaticToolCallbackProvider(callback1, callback2);
 *
 * // Or create provider with List constructor
 * List<FunctionCallback> callbacks = Arrays.asList(callback1, callback2);
 * ToolCallbackProvider provider2 = new StaticToolCallbackProvider(callbacks);
 * }</pre>
 *
 * @author Christian Tzolov
 * @since 1.0.0
 * @see ToolCallbackProvider
 * @see FunctionCallback
 */
public class StaticToolCallbackProvider implements ToolCallbackProvider {

	private final FunctionCallback[] toolCallbacks;

	/**
	 * Constructs a new StaticToolCallbackProvider with the specified array of function
	 * callbacks.
	 * @param toolCallbacks the array of function callbacks to be provided by this
	 * provider. Must not be null, though an empty array is permitted.
	 * @throws IllegalArgumentException if the toolCallbacks array is null
	 */
	public StaticToolCallbackProvider(FunctionCallback... toolCallbacks) {
		Assert.notNull(toolCallbacks, "ToolCallbacks must not be null");
		this.toolCallbacks = toolCallbacks;
	}

	/**
	 * Constructs a new StaticToolCallbackProvider with the specified list of function
	 * callbacks. The list is converted to an array internally.
	 * @param toolCallbacks the list of function callbacks to be provided by this
	 * provider. Must not be null and must not contain null elements.
	 * @throws IllegalArgumentException if the toolCallbacks list is null or contains null
	 * elements
	 */
	public StaticToolCallbackProvider(List<? extends FunctionCallback> toolCallbacks) {
		Assert.noNullElements(toolCallbacks, "toolCallbacks cannot contain null elements");
		this.toolCallbacks = toolCallbacks.toArray(new FunctionCallback[0]);
	}

	/**
	 * Returns the array of function callbacks held by this provider.
	 * @return an array containing all function callbacks provided during construction.
	 * The returned array is a direct reference to the internal array, as the callbacks
	 * are expected to be immutable.
	 */
	@Override
	public FunctionCallback[] getToolCallbacks() {
		return this.toolCallbacks;
	}

}
