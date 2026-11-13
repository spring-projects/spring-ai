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

package org.springframework.ai.mcp.annotation.method.changed.prompt;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import io.modelcontextprotocol.modern.McpSchema;
import reactor.core.publisher.Mono;

/**
 * A handler of the prompt list changes of the connections that speak the 2026-07-28
 * ("modern") MCP revision, the counterpart of
 * {@link AsyncPromptListChangedSpecification}.
 *
 * @param clients the names of the connections the handler hears about
 * @param promptListChangeHandler the handler, which takes the prompts in the modern
 * schema type
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record AsyncModernPromptListChangedSpecification(String[] clients,
		Function<List<McpSchema.Prompt>, Mono<Void>> promptListChangeHandler) {

	public AsyncModernPromptListChangedSpecification {
		Objects.requireNonNull(clients, "clients must not be null");
		if (clients.length == 0 || Arrays.stream(clients).map(String::trim).anyMatch(String::isEmpty)) {
			throw new IllegalArgumentException("clients must not be empty");
		}
		Objects.requireNonNull(promptListChangeHandler, "promptListChangeHandler must not be null");
	}

}
