/*
 * Copyright 2025-2025 the original author or authors.
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

import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.method.elicitation.SyncElicitationSpecification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for {@link SyncMcpElicitationProvider}.
 *
 * @author Christian Tzolov
 */
public class SyncMcpElicitationProviderTests {

	@Test
	public void testGetElicitationHandler() {
		var provider = new SyncMcpElicitationProvider(List.of(new TestElicitationHandler()));
		SyncElicitationSpecification specification = provider.getElicitationSpecifications().get(0);
		Function<ElicitRequest, ElicitResult> handler = specification.elicitationHandler();

		assertNotNull(handler);

		ElicitRequest request = new ElicitRequest("Please provide your name",
				Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"))));
		ElicitResult result = handler.apply(request);

		assertNotNull(result);
		assertEquals(ElicitResult.Action.ACCEPT, result.action());
		assertNotNull(result.content());
		assertEquals("Test User", result.content().get("name"));
	}

	public static class TestElicitationHandler {

		@McpElicitation(clients = "my-client-id")
		public ElicitResult handleElicitation(ElicitRequest request) {
			return new ElicitResult(ElicitResult.Action.ACCEPT,
					Map.of("name", "Test User", "message", request.message()));
		}

	}

	public static class MultipleElicitationHandler {

		@McpElicitation(clients = "my-client-id")
		public ElicitResult handleElicitation1(ElicitRequest request) {
			return new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("handler", "1"));
		}

		@McpElicitation(clients = "my-client-id")
		public ElicitResult handleElicitation2(ElicitRequest request) {
			return new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("handler", "2"));
		}

	}

}
