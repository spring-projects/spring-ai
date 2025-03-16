/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.model.function;

import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.lang.NonNull;

/**
 * Strategy interface for resolving {@link FunctionCallback} instances.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 * @deprecated Use {@link ToolCallbackResolver} instead.
 */
@Deprecated
public interface FunctionCallbackResolver {

	/**
	 * Resolve the {@link FunctionCallback} instance by its name.
	 * @param name the name of the function to resolve
	 * @return the {@link FunctionCallback} instance
	 */
	FunctionCallback resolve(@NonNull String name);

}
