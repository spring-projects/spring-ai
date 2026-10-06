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

package org.springframework.ai.mcp.toolbox.autoconfigure;

import com.google.cloud.mcp.McpToolboxClient;

/**
 * Callback interface that can be implemented by beans wishing to customize the
 * {@link McpToolboxClient.Builder} before it is used to construct the auto-configured
 * {@link McpToolboxClient}.
 *
 * @author Stenal P Jolly
 * @since 2.1.0
 */
@FunctionalInterface
public interface McpToolboxClientCustomizer {

	/**
	 * Customize the {@link McpToolboxClient.Builder}.
	 * @param builder the builder to customize
	 */
	void customize(McpToolboxClient.Builder builder);

}
