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

import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SyncElicitationSpecification} and
 * {@link AsyncElicitationSpecification} validation requirements.
 *
 * @author Christian Tzolov
 */
public class ElicitationSpecificationTests {

	@Test
	void testSyncElicitationSpecificationValidClientId() {
		// Valid clientId should work
		SyncElicitationSpecification spec = new SyncElicitationSpecification(new String[] { "valid-client-id" },
				request -> new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("test", "value")));

		assertThat(spec.clients()).containsExactly("valid-client-id");
		assertThat(spec.elicitationHandler()).isNotNull();
	}

	@Test
	void testSyncElicitationSpecificationNullClientId() {
		assertThatThrownBy(() -> new SyncElicitationSpecification(null,
				request -> new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("test", "value"))))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("clients must not be null");
	}

	@Test
	void testSyncElicitationSpecificationEmptyClientId() {
		assertThatThrownBy(() -> new SyncElicitationSpecification(new String[] { "" },
				request -> new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("test", "value"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("clients must not be empty");
	}

	@Test
	void testSyncElicitationSpecificationBlankClientId() {
		assertThatThrownBy(() -> new SyncElicitationSpecification(new String[] { "	 " },
				request -> new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("test", "value"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("clients must not be empty");
	}

	@Test
	void testSyncElicitationSpecificationNullHandler() {
		assertThatThrownBy(() -> new SyncElicitationSpecification(new String[] { "valid-client-id" }, null))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("elicitationHandler must not be null");
	}

	@Test
	void testAsyncElicitationSpecificationValidClientId() {
		// Valid clientId should work
		AsyncElicitationSpecification spec = new AsyncElicitationSpecification(new String[] { "valid-client-id" },
				request -> Mono.just(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("test", "value"))));

		assertThat(spec.clients()).containsExactly("valid-client-id");
		assertThat(spec.elicitationHandler()).isNotNull();
	}

	@Test
	void testAsyncElicitationSpecificationNullClientId() {
		assertThatThrownBy(() -> new AsyncElicitationSpecification(null,
				request -> Mono.just(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("test", "value")))))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("clients must not be null");
	}

	@Test
	void testAsyncElicitationSpecificationEmptyClientId() {
		assertThatThrownBy(() -> new AsyncElicitationSpecification(new String[] { "" },
				request -> Mono.just(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("test", "value")))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("clients must not be empty");
	}

	@Test
	void testAsyncElicitationSpecificationBlankClientId() {
		assertThatThrownBy(() -> new AsyncElicitationSpecification(new String[] { "	  " },
				request -> Mono.just(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("test", "value")))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("clients must not be empty");
	}

	@Test
	void testAsyncElicitationSpecificationNullHandler() {
		assertThatThrownBy(() -> new AsyncElicitationSpecification(new String[] { "valid-client-id" }, null))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("elicitationHandler must not be null");
	}

	@Test
	void testSyncElicitationSpecificationFunctionality() {
		SyncElicitationSpecification spec = new SyncElicitationSpecification(new String[] { "test-client" },
				request -> new ElicitResult(ElicitResult.Action.ACCEPT,
						Map.of("message", request.message(), "clientId", "test-client")));

		ElicitRequest request = ElicitationTestHelper.createSampleRequest("Test message");
		ElicitResult result = spec.elicitationHandler().apply(request);

		assertThat(result).isNotNull();
		assertThat(result.action()).isEqualTo(ElicitResult.Action.ACCEPT);
		assertThat(result.content()).containsEntry("message", "Test message");
		assertThat(result.content()).containsEntry("clientId", "test-client");
	}

	@Test
	void testAsyncElicitationSpecificationFunctionality() {
		AsyncElicitationSpecification spec = new AsyncElicitationSpecification(new String[] { "test-client" },
				request -> Mono.just(new ElicitResult(ElicitResult.Action.ACCEPT,
						Map.of("message", request.message(), "clientId", "test-client"))));

		ElicitRequest request = ElicitationTestHelper.createSampleRequest("Test async message");
		Mono<ElicitResult> resultMono = spec.elicitationHandler().apply(request);

		ElicitResult result = resultMono.block();
		assertThat(result).isNotNull();
		assertThat(result.action()).isEqualTo(ElicitResult.Action.ACCEPT);
		assertThat(result.content()).containsEntry("message", "Test async message");
		assertThat(result.content()).containsEntry("clientId", "test-client");
	}

}
