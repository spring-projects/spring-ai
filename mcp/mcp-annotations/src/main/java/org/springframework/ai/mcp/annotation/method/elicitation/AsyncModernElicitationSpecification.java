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

package org.springframework.ai.mcp.annotation.method.elicitation;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiFunction;

import io.modelcontextprotocol.modern.McpSchema.ElicitRequest;
import io.modelcontextprotocol.modern.McpSchema.ElicitResult;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.ai.chat.model.ToolContext;

/**
 * A reactive elicitation handler for the connections that speak the 2026-07-28 ("modern")
 * MCP revision, the counterpart of {@link AsyncElicitationSpecification}.
 *
 * @param clients the names of the connections the handler answers for
 * @param form whether the handler answers form elicitation requests
 * @param url whether the handler answers URL elicitation requests
 * @param elicitationHandler the handler, which takes and returns the modern schema types.
 * Its second argument is the tool context of the tool call that the request belongs to,
 * or {@code null} if there is none
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record AsyncModernElicitationSpecification(String[] clients, boolean form, boolean url,
		BiFunction<ElicitRequest, @Nullable ToolContext, Mono<ElicitResult>> elicitationHandler) {

	public AsyncModernElicitationSpecification {
		Objects.requireNonNull(clients, "clients must not be null");
		if (clients.length == 0 || Arrays.stream(clients).map(String::trim).anyMatch(String::isEmpty)) {
			throw new IllegalArgumentException("clients must not be empty");
		}
		if (!form && !url) {
			throw new IllegalArgumentException("the handler must answer form or URL requests");
		}
		Objects.requireNonNull(elicitationHandler, "elicitationHandler must not be null");
	}

}
