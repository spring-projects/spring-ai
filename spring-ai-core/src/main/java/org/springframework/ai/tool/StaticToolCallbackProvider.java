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
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class StaticToolCallbackProvider implements ToolCallbackProvider {

	private final FunctionCallback[] toolCallbacks;

	public StaticToolCallbackProvider(FunctionCallback... toolCallbacks) {
		Assert.notNull(toolCallbacks, "ToolCallbacks must not be null");
		this.toolCallbacks = toolCallbacks;
	}

	public StaticToolCallbackProvider(List<? extends FunctionCallback> toolCallbacks) {
		Assert.notNull(toolCallbacks, "ToolCallbacks must not be null");
		this.toolCallbacks = toolCallbacks.toArray(new FunctionCallback[0]);
	}

	@Override
	public FunctionCallback[] getToolCallbacks() {
		return this.toolCallbacks;
	}

}
