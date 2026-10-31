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

package org.springframework.ai.mcp.modern;

import io.modelcontextprotocol.modern.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.modern.McpSchema.ResourceTemplate;
import io.modelcontextprotocol.modern.server.feature.McpSyncResource;

import org.springframework.util.Assert;

/**
 * A resource template together with the {@link McpSyncResource} that reads every URI
 * matching it. See {@link SyncResourceEntry} for why the pairing lives here.
 *
 * @param resourceTemplate the definition returned by {@code resources/templates/list}
 * @param handler the handler for {@code resources/read} of any URI matching the template
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record SyncResourceTemplateEntry(ResourceTemplate resourceTemplate, McpSyncResource handler) {

	public SyncResourceTemplateEntry {
		Assert.notNull(resourceTemplate, "resourceTemplate must not be null");
		Assert.notNull(handler, "handler must not be null");
	}

	/**
	 * Pairs a resource template with a handler that requires no client capabilities.
	 * @since 2.1.0
	 */
	public static SyncResourceTemplateEntry of(ResourceTemplate resourceTemplate, McpSyncResource.Handler handler) {
		return new SyncResourceTemplateEntry(resourceTemplate, McpSyncResource.of(handler));
	}

	/**
	 * Pairs a resource template with a handler that only works with clients declaring
	 * {@code requiredCapabilities}. A read from any other client is rejected with
	 * {@code -32021} without running the handler.
	 * @since 2.1.0
	 */
	public static SyncResourceTemplateEntry of(ResourceTemplate resourceTemplate,
			ClientCapabilities requiredCapabilities, McpSyncResource.Handler handler) {
		return new SyncResourceTemplateEntry(resourceTemplate, McpSyncResource.of(requiredCapabilities, handler));
	}

}
