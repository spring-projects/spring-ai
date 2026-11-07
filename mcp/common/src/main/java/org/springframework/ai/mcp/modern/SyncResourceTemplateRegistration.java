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
import io.modelcontextprotocol.modern.server.feature.McpSyncCompletionHandler;
import io.modelcontextprotocol.modern.server.feature.McpSyncResource;
import io.modelcontextprotocol.modern.server.feature.McpSyncResourceTemplate;

import org.springframework.util.Assert;

/**
 * A resource template together with the {@link McpSyncResource} that reads every URI
 * matching it. See {@link SyncResourceRegistration} for why the pairing lives here: the
 * SDK's {@link McpSyncResourceTemplate} carries the definition and the completion
 * handler, but not the read handler.
 *
 * @param template the definition returned by {@code resources/templates/list}, with the
 * handler that completes its variables, if any
 * @param handler the handler for {@code resources/read} of any URI matching the template
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record SyncResourceTemplateRegistration(McpSyncResourceTemplate template, McpSyncResource handler) {

	public SyncResourceTemplateRegistration {
		Assert.notNull(template, "template must not be null");
		Assert.notNull(handler, "handler must not be null");
	}

	/**
	 * Pairs a resource template with a handler that requires no client capabilities.
	 * @since 2.1.0
	 */
	public static SyncResourceTemplateRegistration of(ResourceTemplate resourceTemplate,
			McpSyncResource.Handler handler) {
		return new SyncResourceTemplateRegistration(McpSyncResourceTemplate.of(resourceTemplate),
				McpSyncResource.of(handler));
	}

	/**
	 * Pairs a resource template with a handler that only works with clients declaring
	 * {@code requiredCapabilities}. A read from any other client is rejected with
	 * {@code -32021} without running the handler.
	 * @since 2.1.0
	 */
	public static SyncResourceTemplateRegistration of(ResourceTemplate resourceTemplate,
			ClientCapabilities requiredCapabilities, McpSyncResource.Handler handler) {
		return new SyncResourceTemplateRegistration(McpSyncResourceTemplate.of(resourceTemplate),
				McpSyncResource.of(requiredCapabilities, handler));
	}

	/**
	 * This registration with the template's variables completed by
	 * {@code completionHandler}, when its repository supports completion.
	 * @since 2.1.0
	 */
	public SyncResourceTemplateRegistration withCompletionHandler(McpSyncCompletionHandler completionHandler) {
		return new SyncResourceTemplateRegistration(this.template.withCompletionHandler(completionHandler),
				this.handler);
	}

}
