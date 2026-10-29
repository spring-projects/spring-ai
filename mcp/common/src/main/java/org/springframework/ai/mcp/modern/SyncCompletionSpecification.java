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

import io.modelcontextprotocol.modern.McpSchema.CompleteReference;
import io.modelcontextprotocol.modern.McpSchema.CompleteRequest;
import io.modelcontextprotocol.modern.McpSchema.CompleteResult;
import io.modelcontextprotocol.modern.server.McpRequestContext;

import org.springframework.util.Assert;

/**
 * A completion provider exposed on a 2026-07-28 server: the prompt or resource reference
 * it completes and the handler that serves {@code completion/complete}.
 *
 * @param reference the prompt or resource reference the handler completes
 * @param handler the handler invoked for {@code completion/complete}
 * @author Christian Tzolov
 * @since 2.2.0
 */
public record SyncCompletionSpecification(CompleteReference reference, Handler handler) {

	public SyncCompletionSpecification {
		Assert.notNull(reference, "reference must not be null");
		Assert.notNull(handler, "handler must not be null");
	}

	/**
	 * Serves a {@code completion/complete} request.
	 */
	@FunctionalInterface
	public interface Handler {

		CompleteResult complete(McpRequestContext context, CompleteRequest request);

	}
}
