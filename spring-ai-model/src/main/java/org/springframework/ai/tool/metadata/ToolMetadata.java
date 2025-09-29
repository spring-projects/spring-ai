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

package org.springframework.ai.tool.metadata;

import java.lang.reflect.Method;

import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.util.Assert;

/**
 * Metadata about a tool specification and execution.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface ToolMetadata {

	/**
	 * Whether the tool result should be returned directly or passed back to the model.
	 */
	default boolean returnDirect() {
		return false;
	}

	/**
	 * Create a default {@link ToolMetadata} builder.
	 */
	static DefaultToolMetadata.Builder builder() {
		return DefaultToolMetadata.builder();
	}

	/**
	 * Create a default {@link ToolMetadata} instance from a {@link Method}.
	 */
	static ToolMetadata from(Method method) {
		Assert.notNull(method, "method cannot be null");
		return DefaultToolMetadata.builder().returnDirect(ToolUtils.getToolReturnDirect(method)).build();
	}

}
