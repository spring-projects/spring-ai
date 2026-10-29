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

import io.modelcontextprotocol.modern.McpSchema.Resource;
import io.modelcontextprotocol.modern.server.feature.McpSyncResource;

import org.springframework.util.Assert;

/**
 * A listed resource together with the {@link McpSyncResource} that reads it. The SDK's
 * {@code McpSyncResource} carries no definition, because {@code resources/read} also
 * serves URIs that only match a template; a catalogue of static resources needs the
 * pairing.
 *
 * @param resource the definition returned by {@code resources/list}
 * @param handler the handler for {@code resources/read} of {@code resource.uri()}
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record SyncResourceEntry(Resource resource, McpSyncResource handler) {

	public SyncResourceEntry {
		Assert.notNull(resource, "resource must not be null");
		Assert.notNull(handler, "handler must not be null");
	}

	/**
	 * Pairs a resource with a handler that requires no client capabilities.
	 */
	public static SyncResourceEntry of(Resource resource, McpSyncResource.Handler handler) {
		return new SyncResourceEntry(resource, McpSyncResource.of(handler));
	}

}
