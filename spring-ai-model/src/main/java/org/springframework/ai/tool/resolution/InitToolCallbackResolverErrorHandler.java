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

package org.springframework.ai.tool.resolution;

import java.util.List;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.core.Ordered;

/**
 * Handler for errors when {@link ToolCallbackResolver} failed to fetch ToolCallbacks on
 * application starting up.
 *
 * @author walter.tan
 * @since 1.0.2
 */
public interface InitToolCallbackResolverErrorHandler extends Ordered {

	/**
	 * Check whether the handler supports the given provider and error.
	 * @param provider the failed ToolCallbackProvider
	 * @param t the error caught
	 * @return
	 */
	boolean support(ToolCallbackProvider provider, Throwable t);

	/**
	 * Handle errors when initializing the {@link ToolCallbackResolver} failed and return
	 * fallback ToolCallbacks on demand.
	 * @param provider the failed ToolCallbackProvider
	 * @param t the error caught
	 * @return
	 */
	List<ToolCallback> handle(ToolCallbackProvider provider, Throwable t);

}
