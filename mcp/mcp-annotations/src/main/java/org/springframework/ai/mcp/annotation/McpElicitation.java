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

package org.springframework.ai.mcp.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for methods that handle elicitation requests from MCP servers. This
 * annotation is applicable only for MCP clients.
 *
 * <p>
 * Methods annotated with this annotation can be used to process elicitation requests from
 * MCP servers.
 *
 * <p>
 * For synchronous handlers, the method must return {@code ElicitResult}. For asynchronous
 * handlers, the method must return {@code Mono<ElicitResult>}.
 *
 * <p>
 * Example usage: <pre>{@code
 * &#64;McpElicitation(clients = "my-client-id")
 * public ElicitResult handleElicitationRequest(ElicitRequest request) {
 *     return ElicitResult.builder()
 *         .message("Generated response")
 *         .requestedSchema(
 *             Map.of("type", "object", "properties", Map.of("message", Map.of("type", "string"))))
 *         .build();
 * }
 *
 * &#64;McpElicitation(clients = "my-client-id")
 * public Mono<ElicitResult> handleAsyncElicitationRequest(ElicitRequest request) {
 *     return Mono.just(ElicitResult.builder()
 *         .message("Generated response")
 *         .requestedSchema(
 *             Map.of("type", "object", "properties", Map.of("message", Map.of("type", "string"))))
 *         .build());
 * }
 * }</pre>
 *
 * @author Christian Tzolov
 * @see io.modelcontextprotocol.spec.McpSchema.ElicitRequest
 * @see io.modelcontextprotocol.spec.McpSchema.ElicitResult
 */
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpElicitation {

	/**
	 * Used as connection or client identifier to select the MCP clients, the elicitation
	 * method is associated with.
	 */
	String[] clients();

}
