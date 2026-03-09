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

package org.springframework.ai.mcp.annotation.context;

import java.util.Map;

/**
 * Common interface for classes that provide metadata for the "_meta" field. This metadata
 * is used in tool, prompt, and resource declarations.
 *
 * @author Vadzim Shurmialiou
 * @author Craig Walls
 */
public interface MetaProvider {

	/**
	 * Returns metadata key-value pairs that will be included in the "_meta" field. These
	 * metadata values provide additional context and information for tools, prompts, and
	 * resource declarations.
	 * @return A Map containing metadata key-value pairs, where keys are strings and
	 * values can be any object type.
	 */
	Map<String, Object> getMeta();

}
