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

package org.springframework.ai.mcp.annotation.provider.elicitation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.method.elicitation.AsyncElicitationSpecification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for {@link AsyncMcpElicitationProvider}.
 *
 * @author Christian Tzolov
 */
public class AsyncMcpElicitationProviderTests {

	@Test
	public void testGetElicitationHandler() {
		var provider = new AsyncMcpElicitationProvider(List.of(new TestElicitationHandler()));

		AsyncElicitationSpecification specification = provider.getElicitationSpecifications().get(0);
		Function<ElicitRequest, Mono<ElicitResult>> handler = specification.elicitationHandler();

		assertNotNull(handler);

		ElicitRequest request = new ElicitRequest("Please provide your name",
				Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"))));
		Mono<ElicitResult> result = handler.apply(request);

		StepVerifier.create(result).assertNext(elicitResult -> {
			assertEquals(ElicitResult.Action.ACCEPT, elicitResult.action());
			assertNotNull(elicitResult.content());
			assertEquals("Async Test User", elicitResult.content().get("name"));
		}).verifyComplete();
	}

	@Test
	public void testGetElicitationHandlerWithSyncMethod() {
		var provider = new AsyncMcpElicitationProvider(List.of(new SyncElicitationHandler()));
		assertThat(provider.getElicitationSpecifications()).isEmpty();
	}

	public static class TestElicitationHandler {

		@McpElicitation(clients = "my-client-id")
		public Mono<ElicitResult> handleElicitation(ElicitRequest request) {
			return Mono.just(new ElicitResult(ElicitResult.Action.ACCEPT,
					Map.of("name", "Async Test User", "message", request.message())));
		}

	}

	public static class SyncElicitationHandler {

		@McpElicitation(clients = "my-client-id")
		public ElicitResult handleElicitation(ElicitRequest request) {
			return new ElicitResult(ElicitResult.Action.ACCEPT,
					Map.of("name", "Sync Test User", "message", request.message()));
		}

	}

	public static class MultipleElicitationHandler {

		@McpElicitation(clients = "my-client-id")
		public Mono<ElicitResult> handleElicitation1(ElicitRequest request) {
			return Mono.just(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("handler", "1")));
		}

		@McpElicitation(clients = "my-client-id")
		public Mono<ElicitResult> handleElicitation2(ElicitRequest request) {
			return Mono.just(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("handler", "2")));
		}

	}

}
