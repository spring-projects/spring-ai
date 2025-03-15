/*
 * Copyright 2023-2025 the original author or authors.
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

import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;

/**
 * A resolver for {@link ToolCallback} instances.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface ToolCallbackResolver {

	/**
	 * Resolve the {@link FunctionCallback} for the given tool name.
	 */
	@Nullable
	FunctionCallback resolve(String toolName);

}
