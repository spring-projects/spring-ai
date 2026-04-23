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

package org.springframework.ai.mcp.client.webflux.transport;

import java.util.Optional;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.TypeRef;
import io.modelcontextprotocol.util.McpJsonMapperUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SSE event type parsing in {@link WebClientStreamableHttpTransport}.
 *
 * Regression tests for
 * <a href="https://github.com/spring-projects/spring-ai/issues/5780">Issue #5780</a>:
 * Tests that SSE frames with null or empty event type are correctly parsed as message
 * events. Per SSE spec, missing event: field defaults to "message", but the original code
 * only accepted frames with event == "message" explicitly, silently dropping frames with
 * event == null.
 *
 * @author Spring AI Team
 */
class WebClientStreamableHttpTransportSseEventTest {

	private TestableWebClientStreamableHttpTransport transport;

	@BeforeEach
	void setUp() {
		McpJsonMapper jsonMapper = McpJsonMapperUtils.JSON_MAPPER;
		this.transport = new TestableWebClientStreamableHttpTransport(jsonMapper);
	}

	/**
	 * Tests that SSE frame with null event type (no event: field) is parsed as a message
	 * event.
	 */
	@Test
	void testParseSseWithNullEventType() {
		ServerSentEvent<String> event = ServerSentEvent.<String>builder()
			.data("{\"jsonrpc\":\"2.0\",\"method\":\"testMethod\",\"id\":\"1\"}")
			.build();

		Tuple2<Optional<String>, Iterable<McpSchema.JSONRPCMessage>> result = this.transport.testParse(event);

		assertThat(result).isNotNull();
		assertThat(result.getT2()).hasSize(1);

		McpSchema.JSONRPCMessage message = result.getT2().iterator().next();
		assertThat(message.method()).isEqualTo("testMethod");
		assertThat(message.id()).isEqualTo("1");
	}

	/**
	 * Tests that SSE frame with empty string event type is parsed as a message event.
	 */
	@Test
	void testParseSseWithEmptyEventType() {
		ServerSentEvent<String> event = ServerSentEvent.<String>builder()
			.event("")
			.data("{\"jsonrpc\":\"2.0\",\"method\":\"testMethod2\",\"id\":\"2\"}")
			.build();

		Tuple2<Optional<String>, Iterable<McpSchema.JSONRPCMessage>> result = this.transport.testParse(event);

		assertThat(result).isNotNull();
		assertThat(result.getT2()).hasSize(1);

		McpSchema.JSONRPCMessage message = result.getT2().iterator().next();
		assertThat(message.method()).isEqualTo("testMethod2");
		assertThat(message.id()).isEqualTo("2");
	}

	/**
	 * Tests that SSE frame with explicit "message" event type is still parsed correctly.
	 */
	@Test
	void testParseSseWithMessageEventType() {
		ServerSentEvent<String> event = ServerSentEvent.<String>builder()
			.event("message")
			.data("{\"jsonrpc\":\"2.0\",\"method\":\"testMethod3\",\"id\":\"3\"}")
			.build();

		Tuple2<Optional<String>, Iterable<McpSchema.JSONRPCMessage>> result = this.transport.testParse(event);

		assertThat(result).isNotNull();
		assertThat(result.getT2()).hasSize(1);

		McpSchema.JSONRPCMessage message = result.getT2().iterator().next();
		assertThat(message.method()).isEqualTo("testMethod3");
		assertThat(message.id()).isEqualTo("3");
	}

	/**
	 * Tests that SSE frame with non-message event type is NOT parsed (returns empty
	 * list).
	 */
	@Test
	void testParseSseWithOtherEventTypeIsIgnored() {
		ServerSentEvent<String> event = ServerSentEvent.<String>builder()
			.event("ping")
			.data("{\"jsonrpc\":\"2.0\",\"method\":\"testMethod4\",\"id\":\"4\"}")
			.build();

		Tuple2<Optional<String>, Iterable<McpSchema.JSONRPCMessage>> result = this.transport.testParse(event);

		assertThat(result).isNotNull();
		assertThat(result.getT2()).isEmpty();
	}

	/**
	 * Tests that SSE frame with event ID is properly extracted.
	 */
	@Test
	void testParseSseWithEventId() {
		ServerSentEvent<String> event = ServerSentEvent.<String>builder()
			.id("event-123")
			.data("{\"jsonrpc\":\"2.0\",\"method\":\"testMethod5\",\"id\":\"5\"}")
			.build();

		Tuple2<Optional<String>, Iterable<McpSchema.JSONRPCMessage>> result = this.transport.testParse(event);

		assertThat(result).isNotNull();
		assertThat(result.getT1()).isPresent();
		assertThat(result.getT1().get()).isEqualTo("event-123");
		assertThat(result.getT2()).hasSize(1);
	}

	/**
	 * Tests that multiple SSE frames with various event types are all correctly handled.
	 */
	@Test
	void testParseMultipleSseEventsWithMixedEventTypes() {
		// null event type
		Tuple2<Optional<String>, Iterable<McpSchema.JSONRPCMessage>> result1 = this.transport
			.testParse(ServerSentEvent.<String>builder()
				.data("{\"jsonrpc\":\"2.0\",\"method\":\"method1\",\"id\":\"id1\"}")
				.build());
		assertThat(result1.getT2()).hasSize(1);

		// empty event type
		Tuple2<Optional<String>, Iterable<McpSchema.JSONRPCMessage>> result2 = this.transport
			.testParse(ServerSentEvent.<String>builder()
				.event("")
				.data("{\"jsonrpc\":\"2.0\",\"method\":\"method2\",\"id\":\"id2\"}")
				.build());
		assertThat(result2.getT2()).hasSize(1);

		// explicit "message" event type
		Tuple2<Optional<String>, Iterable<McpSchema.JSONRPCMessage>> result3 = this.transport
			.testParse(ServerSentEvent.<String>builder()
				.event("message")
				.data("{\"jsonrpc\":\"2.0\",\"method\":\"method3\",\"id\":\"id3\"}")
				.build());
		assertThat(result3.getT2()).hasSize(1);

		// non-message event type should be ignored
		Tuple2<Optional<String>, Iterable<McpSchema.JSONRPCMessage>> result4 = this.transport
			.testParse(ServerSentEvent.<String>builder()
				.event("other")
				.data("{\"jsonrpc\":\"2.0\",\"method\":\"method4\",\"id\":\"id4\"}")
				.build());
		assertThat(result4.getT2()).isEmpty();
	}

	/**
	 * Testable subclass that exposes the parse method for testing.
	 */
	private static final class TestableWebClientStreamableHttpTransport extends WebClientStreamableHttpTransport {

		private TestableWebClientStreamableHttpTransport(McpJsonMapper jsonMapper) {
			super(WebClient.builder(), jsonMapper);
		}

		/**
		 * Exposes the private parse method for testing.
		 */
		public Tuple2<Optional<String>, Iterable<McpSchema.JSONRPCMessage>> testParse(ServerSentEvent<String> event) {
			return this.parse(event);
		}

		@Override
		protected Flux<ServerSentEvent<String>> eventStream() {
			return Flux.empty();
		}

		@Override
		public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
			return this.jsonMapper.convertValue(data, typeRef);
		}

	}

}
