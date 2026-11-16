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

package org.springframework.ai.mcp.annotation.method.progress;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiConsumer;

import io.modelcontextprotocol.modern.McpSchema.ProgressParams;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.model.ToolContext;

/**
 * A progress handler for the connections that speak the 2026-07-28 ("modern") MCP
 * revision, the counterpart of {@link SyncProgressSpecification}.
 *
 * @param clients the names of the connections the handler receives the progress of
 * @param progressHandler the handler, which takes the modern progress. Its second
 * argument is the tool context of the tool call that reports the progress, or
 * {@code null} if there is none
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record SyncModernProgressSpecification(String[] clients,
		BiConsumer<ProgressParams, @Nullable ToolContext> progressHandler) {

	public SyncModernProgressSpecification {
		Objects.requireNonNull(clients, "clients must not be null");
		if (clients.length == 0 || Arrays.stream(clients).map(String::trim).anyMatch(String::isEmpty)) {
			throw new IllegalArgumentException("At least one client Id must be specified");
		}
		Objects.requireNonNull(progressHandler, "progressHandler must not be null");
	}

}
