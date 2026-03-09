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

package org.springframework.ai.mcp.annotation.context;

import java.util.Map;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageRequest;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.ListRootsResult;
import io.modelcontextprotocol.spec.McpSchema.LoggingLevel;
import io.modelcontextprotocol.spec.McpSchema.LoggingMessageNotification;
import io.modelcontextprotocol.spec.McpSchema.ProgressNotification;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.SamplingMessage;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.core.type.TypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultMcpAsyncRequestContext}.
 *
 * @author Christian Tzolov
 */
public class DefaultMcpAsyncRequestContextTests {

	private CallToolRequest request;

	private McpAsyncServerExchange exchange;

	private McpAsyncRequestContext context;

	@BeforeEach
	public void setUp() {
		this.request = new CallToolRequest("test-tool", Map.of());
		this.exchange = mock(McpAsyncServerExchange.class);
		this.context = DefaultMcpAsyncRequestContext.builder().request(this.request).exchange(this.exchange).build();
	}

	// Builder Tests

	@Test
	public void testBuilderWithValidParameters() {
		CallToolRequest testRequest = new CallToolRequest("test-tool", Map.of());
		McpAsyncRequestContext ctx = DefaultMcpAsyncRequestContext.builder()
			.request(testRequest)
			.exchange(this.exchange)
			.build();

		assertThat(ctx).isNotNull();
		assertThat(ctx.request()).isEqualTo(testRequest);
		assertThat(ctx.exchange()).isEqualTo(this.exchange);
	}

	@Test
	public void testBuilderWithNullRequest() {
		StepVerifier
			.create(Mono.fromCallable(
					() -> DefaultMcpAsyncRequestContext.builder().request(null).exchange(this.exchange).build()))
			.expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException
					&& throwable.getMessage().contains("Request must not be null"))
			.verify();
	}

	@Test
	public void testBuilderWithNullExchange() {
		CallToolRequest testRequest = new CallToolRequest("test-tool", Map.of());
		StepVerifier
			.create(Mono.fromCallable(
					() -> DefaultMcpAsyncRequestContext.builder().request(testRequest).exchange(null).build()))
			.expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException
					&& throwable.getMessage().contains("Exchange must not be null"))
			.verify();
	}

	// Roots Tests

	@Test
	public void testRootsWhenSupported() {
		ClientCapabilities capabilities = mock(ClientCapabilities.class);
		McpSchema.ClientCapabilities.RootCapabilities roots = mock(McpSchema.ClientCapabilities.RootCapabilities.class);
		when(capabilities.roots()).thenReturn(roots);
		when(this.exchange.getClientCapabilities()).thenReturn(capabilities);

		ListRootsResult expectedResult = mock(ListRootsResult.class);
		when(this.exchange.listRoots()).thenReturn(Mono.just(expectedResult));

		StepVerifier.create(this.context.roots()).expectNext(expectedResult).verifyComplete();

		verify(this.exchange).listRoots();
	}

	@Test
	public void testRootsWhenNotSupported() {
		when(this.exchange.getClientCapabilities()).thenReturn(null);

		StepVerifier.create(this.context.roots())
			.verifyErrorSatisfies(e -> assertThat(e).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Roots not supported by the client"));
	}

	@Test
	public void testRootsWhenCapabilitiesNullRoots() {
		ClientCapabilities capabilities = mock(ClientCapabilities.class);
		when(capabilities.roots()).thenReturn(null);
		when(this.exchange.getClientCapabilities()).thenReturn(capabilities);

		StepVerifier.create(this.context.roots())
			.verifyErrorSatisfies(e -> assertThat(e).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Roots not supported by the client"));

	}

	// Elicitation Tests

	@Test
	public void testElicitationWithMessageAndMeta() {
		ClientCapabilities capabilities = mock(ClientCapabilities.class);
		ClientCapabilities.Elicitation elicitation = mock(ClientCapabilities.Elicitation.class);
		when(capabilities.elicitation()).thenReturn(elicitation);
		when(this.exchange.getClientCapabilities()).thenReturn(capabilities);

		Map<String, Object> contentMap = Map.of("name", "John", "age", 30);
		ElicitResult expectedResult = mock(ElicitResult.class);
		when(expectedResult.action()).thenReturn(ElicitResult.Action.ACCEPT);
		when(expectedResult.content()).thenReturn(contentMap);
		when(expectedResult.meta()).thenReturn(null);
		when(this.exchange.createElicitation(any(ElicitRequest.class))).thenReturn(Mono.just(expectedResult));

		Mono<StructuredElicitResult<Map<String, Object>>> result = this.context.elicit(e -> e.message("Test message"),
				new TypeReference<Map<String, Object>>() {
				});

		StepVerifier.create(result).assertNext(structuredResult -> {
			assertThat(structuredResult.action()).isEqualTo(ElicitResult.Action.ACCEPT);
			assertThat(structuredResult.structuredContent()).isNotNull();
			assertThat(structuredResult.structuredContent()).containsEntry("name", "John");
			assertThat(structuredResult.structuredContent()).containsEntry("age", 30);
		}).verifyComplete();

		ArgumentCaptor<ElicitRequest> captor = ArgumentCaptor.forClass(ElicitRequest.class);
		verify(this.exchange).createElicitation(captor.capture());

		ElicitRequest capturedRequest = captor.getValue();
		assertThat(capturedRequest.message()).isEqualTo("Test message");
		assertThat(capturedRequest.requestedSchema()).isNotNull();
	}

	@Test
	public void testElicitationWithMetadata() {
		ClientCapabilities capabilities = mock(ClientCapabilities.class);
		ClientCapabilities.Elicitation elicitation = mock(ClientCapabilities.Elicitation.class);
		when(capabilities.elicitation()).thenReturn(elicitation);
		when(this.exchange.getClientCapabilities()).thenReturn(capabilities);

		record Person(String name, int age) {
		}

		Map<String, Object> contentMap = Map.of("name", "Jane", "age", 25);
		ElicitResult expectedResult = mock(ElicitResult.class);
		when(expectedResult.action()).thenReturn(ElicitResult.Action.ACCEPT);
		when(expectedResult.content()).thenReturn(contentMap);
		when(expectedResult.meta()).thenReturn(null);
		when(this.exchange.createElicitation(any(ElicitRequest.class))).thenReturn(Mono.just(expectedResult));

		Map<String, Object> meta = Map.of("key", "value");
		Mono<StructuredElicitResult<Person>> result = this.context.elicit(e -> e.message("Test message").meta(meta),
				new TypeReference<Person>() {
				});

		StepVerifier.create(result).assertNext(structuredResult -> {
			assertThat(structuredResult.action()).isEqualTo(ElicitResult.Action.ACCEPT);
			assertThat(structuredResult.structuredContent()).isNotNull();
			assertThat(structuredResult.structuredContent().name()).isEqualTo("Jane");
			assertThat(structuredResult.structuredContent().age()).isEqualTo(25);
		}).verifyComplete();

		ArgumentCaptor<ElicitRequest> captor = ArgumentCaptor.forClass(ElicitRequest.class);
		verify(this.exchange).createElicitation(captor.capture());

		ElicitRequest capturedRequest = captor.getValue();
		assertThat(capturedRequest.meta()).containsEntry("key", "value");
	}

	@Test
	public void testElicitationWithNullTypeReference() {
		assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> this.context.elicit((TypeReference<?>) null)))
			.hasMessageContaining("Elicitation response type must not be null");
	}

	@Test
	public void testElicitationWithNullClassType() {
		assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> this.context.elicit((Class<?>) null)))
			.hasMessageContaining("Elicitation response type must not be null");
	}

	@Test
	public void testElicitationWithEmptyMessage() {
		assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
			this.context.elicit(e -> e.message("").meta(null), new TypeReference<String>() {
			});
		})).hasMessageContaining("Elicitation message must not be empty");
	}

	@Test
	public void testElicitationWithNullMessage() {
		assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
			this.context.elicit(e -> e.message(null).meta(null), new TypeReference<String>() {
			});
		})).hasMessageContaining("Elicitation message must not be empty");
	}

	@Test
	public void testElicitationReturnsEmptyWhenNotSupported() {
		when(this.exchange.getClientCapabilities()).thenReturn(null);

		StepVerifier
			.create(this.context.elicit(e -> e.message("Test message"), new TypeReference<Map<String, Object>>() {
			}))
			.verifyErrorSatisfies(e -> assertThat(e).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Elicitation not supported by the client"));
	}

	@Test
	public void testElicitationReturnsResultWhenActionIsNotAccept() {
		ClientCapabilities capabilities = mock(ClientCapabilities.class);
		ClientCapabilities.Elicitation elicitation = mock(ClientCapabilities.Elicitation.class);
		when(capabilities.elicitation()).thenReturn(elicitation);
		when(this.exchange.getClientCapabilities()).thenReturn(capabilities);

		Map<String, Object> contentMap = Map.of();
		ElicitResult expectedResult = mock(ElicitResult.class);
		when(expectedResult.action()).thenReturn(ElicitResult.Action.DECLINE);
		when(expectedResult.content()).thenReturn(contentMap);
		when(expectedResult.meta()).thenReturn(null);
		when(this.exchange.createElicitation(any(ElicitRequest.class))).thenReturn(Mono.just(expectedResult));

		Mono<StructuredElicitResult<Map<String, Object>>> result = this.context.elicit(e -> e.message("Test message"),
				new TypeReference<Map<String, Object>>() {
				});

		StepVerifier.create(result).assertNext(structuredResult -> {
			assertThat(structuredResult.action()).isEqualTo(ElicitResult.Action.DECLINE);
			assertThat(structuredResult.structuredContent()).isNotNull();
		}).verifyComplete();
	}

	@Test
	public void testElicitationConvertsComplexTypes() {
		ClientCapabilities capabilities = mock(ClientCapabilities.class);
		ClientCapabilities.Elicitation elicitation = mock(ClientCapabilities.Elicitation.class);
		when(capabilities.elicitation()).thenReturn(elicitation);
		when(this.exchange.getClientCapabilities()).thenReturn(capabilities);

		record Address(String street, String city) {
		}
		record PersonWithAddress(String name, int age, Address address) {
		}

		Map<String, Object> addressMap = Map.of("street", "123 Main St", "city", "Springfield");
		Map<String, Object> contentMap = Map.of("name", "John", "age", 30, "address", addressMap);
		ElicitResult expectedResult = mock(ElicitResult.class);
		when(expectedResult.action()).thenReturn(ElicitResult.Action.ACCEPT);
		when(expectedResult.content()).thenReturn(contentMap);
		when(expectedResult.meta()).thenReturn(null);
		when(this.exchange.createElicitation(any(ElicitRequest.class))).thenReturn(Mono.just(expectedResult));

		Mono<StructuredElicitResult<PersonWithAddress>> result = this.context.elicit(e -> e.message("Test message"),
				new TypeReference<PersonWithAddress>() {
				});

		StepVerifier.create(result).assertNext(structuredResult -> {
			assertThat(structuredResult.action()).isEqualTo(ElicitResult.Action.ACCEPT);
			assertThat(structuredResult.structuredContent()).isNotNull();
			assertThat(structuredResult.structuredContent().name()).isEqualTo("John");
			assertThat(structuredResult.structuredContent().age()).isEqualTo(30);
			assertThat(structuredResult.structuredContent().address()).isNotNull();
			assertThat(structuredResult.structuredContent().address().street()).isEqualTo("123 Main St");
			assertThat(structuredResult.structuredContent().address().city()).isEqualTo("Springfield");
		}).verifyComplete();
	}

	@Test
	public void testElicitationHandlesListTypes() {
		ClientCapabilities capabilities = mock(ClientCapabilities.class);
		ClientCapabilities.Elicitation elicitation = mock(ClientCapabilities.Elicitation.class);
		when(capabilities.elicitation()).thenReturn(elicitation);
		when(this.exchange.getClientCapabilities()).thenReturn(capabilities);

		Map<String, Object> contentMap = Map.of("items",
				java.util.List.of(Map.of("name", "Item1"), Map.of("name", "Item2")));
		ElicitResult expectedResult = mock(ElicitResult.class);
		when(expectedResult.action()).thenReturn(ElicitResult.Action.ACCEPT);
		when(expectedResult.content()).thenReturn(contentMap);
		when(expectedResult.meta()).thenReturn(null);
		when(this.exchange.createElicitation(any(ElicitRequest.class))).thenReturn(Mono.just(expectedResult));

		Mono<StructuredElicitResult<Map<String, Object>>> result = this.context.elicit(e -> e.message("Test message"),
				new TypeReference<Map<String, Object>>() {
				});

		StepVerifier.create(result)
			.assertNext(structuredResult -> assertThat(structuredResult.structuredContent()).containsKey("items"))
			.verifyComplete();
	}

	@Test
	public void testElicitationWithTypeReference() {
		ClientCapabilities capabilities = mock(ClientCapabilities.class);
		ClientCapabilities.Elicitation elicitation = mock(ClientCapabilities.Elicitation.class);
		when(capabilities.elicitation()).thenReturn(elicitation);
		when(this.exchange.getClientCapabilities()).thenReturn(capabilities);

		Map<String, Object> contentMap = Map.of("result", "success", "data", "test value");
		ElicitResult expectedResult = mock(ElicitResult.class);
		when(expectedResult.action()).thenReturn(ElicitResult.Action.ACCEPT);
		when(expectedResult.content()).thenReturn(contentMap);
		when(this.exchange.createElicitation(any(ElicitRequest.class))).thenReturn(Mono.just(expectedResult));

		Mono<StructuredElicitResult<Map<String, Object>>> result = this.context
			.elicit(new TypeReference<Map<String, Object>>() {
			});

		StepVerifier.create(result).assertNext(map -> {
			assertThat(map.structuredContent()).containsEntry("result", "success");
			assertThat(map.structuredContent()).containsEntry("data", "test value");
		}).verifyComplete();
	}

	@Test
	public void testElicitationWithRequest() {
		ClientCapabilities capabilities = mock(ClientCapabilities.class);
		ClientCapabilities.Elicitation elicitation = mock(ClientCapabilities.Elicitation.class);
		when(capabilities.elicitation()).thenReturn(elicitation);
		when(this.exchange.getClientCapabilities()).thenReturn(capabilities);

		ElicitResult expectedResult = mock(ElicitResult.class);
		ElicitRequest elicitRequest = ElicitRequest.builder()
			.message("Test message")
			.requestedSchema(Map.of("type", "string"))
			.build();

		when(this.exchange.createElicitation(elicitRequest)).thenReturn(Mono.just(expectedResult));

		Mono<ElicitResult> result = this.context.elicit(elicitRequest);

		StepVerifier.create(result).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void testElicitationWhenNotSupported() {
		when(this.exchange.getClientCapabilities()).thenReturn(null);

		ElicitRequest elicitRequest = ElicitRequest.builder()
			.message("Test message")
			.requestedSchema(Map.of("type", "string"))
			.build();

		StepVerifier.create(this.context.elicit(elicitRequest))
			.verifyErrorSatisfies(e -> assertThat(e).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Elicitation not supported by the client"));
	}

	// Sampling Tests

	@Test
	public void testSamplingWithMessages() {
		ClientCapabilities capabilities = mock(ClientCapabilities.class);
		ClientCapabilities.Sampling sampling = mock(ClientCapabilities.Sampling.class);
		when(capabilities.sampling()).thenReturn(sampling);
		when(this.exchange.getClientCapabilities()).thenReturn(capabilities);

		CreateMessageResult expectedResult = mock(CreateMessageResult.class);
		when(this.exchange.createMessage(any(CreateMessageRequest.class))).thenReturn(Mono.just(expectedResult));

		Mono<CreateMessageResult> result = this.context.sample("Message 1", "Message 2");

		StepVerifier.create(result).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void testSamplingWithConsumer() {
		ClientCapabilities capabilities = mock(ClientCapabilities.class);
		ClientCapabilities.Sampling sampling = mock(ClientCapabilities.Sampling.class);
		when(capabilities.sampling()).thenReturn(sampling);
		when(this.exchange.getClientCapabilities()).thenReturn(capabilities);

		CreateMessageResult expectedResult = mock(CreateMessageResult.class);
		when(this.exchange.createMessage(any(CreateMessageRequest.class))).thenReturn(Mono.just(expectedResult));

		Mono<CreateMessageResult> result = this.context.sample(spec -> {
			spec.message(new TextContent("Test message"));
			spec.systemPrompt("System prompt");
			spec.temperature(0.7);
			spec.maxTokens(100);
		});

		StepVerifier.create(result).expectNext(expectedResult).verifyComplete();

		ArgumentCaptor<CreateMessageRequest> captor = ArgumentCaptor.forClass(CreateMessageRequest.class);
		verify(this.exchange).createMessage(captor.capture());

		CreateMessageRequest capturedRequest = captor.getValue();
		assertThat(capturedRequest.systemPrompt()).isEqualTo("System prompt");
		assertThat(capturedRequest.temperature()).isEqualTo(0.7);
		assertThat(capturedRequest.maxTokens()).isEqualTo(100);
	}

	@Test
	public void testSamplingWithRequest() {
		ClientCapabilities capabilities = mock(ClientCapabilities.class);
		ClientCapabilities.Sampling sampling = mock(ClientCapabilities.Sampling.class);
		when(capabilities.sampling()).thenReturn(sampling);
		when(this.exchange.getClientCapabilities()).thenReturn(capabilities);

		CreateMessageResult expectedResult = mock(CreateMessageResult.class);
		CreateMessageRequest createRequest = CreateMessageRequest.builder()
			.messages(java.util.List.of(new SamplingMessage(Role.USER, new TextContent("Test"))))
			.maxTokens(500)
			.build();

		when(this.exchange.createMessage(createRequest)).thenReturn(Mono.just(expectedResult));

		Mono<CreateMessageResult> result = this.context.sample(createRequest);

		StepVerifier.create(result).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void testSamplingWhenNotSupported() {
		when(this.exchange.getClientCapabilities()).thenReturn(null);

		CreateMessageRequest createRequest = CreateMessageRequest.builder()
			.messages(java.util.List.of(new SamplingMessage(Role.USER, new TextContent("Test"))))
			.maxTokens(500)
			.build();

		StepVerifier.create(this.context.sample(createRequest))
			.verifyErrorSatisfies(e -> assertThat(e).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Sampling not supported by the client"));
	}

	// Progress Tests

	@Test
	public void testProgressWithPercentage() {
		CallToolRequest requestWithToken = CallToolRequest.builder()
			.name("test-tool")
			.arguments(Map.of())
			.progressToken("token-123")
			.build();
		McpAsyncRequestContext contextWithToken = DefaultMcpAsyncRequestContext.builder()
			.request(requestWithToken)
			.exchange(this.exchange)
			.build();

		when(this.exchange.progressNotification(any(ProgressNotification.class))).thenReturn(Mono.empty());

		StepVerifier.create(contextWithToken.progress(50)).verifyComplete();

		ArgumentCaptor<ProgressNotification> captor = ArgumentCaptor.forClass(ProgressNotification.class);
		verify(this.exchange).progressNotification(captor.capture());

		ProgressNotification notification = captor.getValue();
		assertThat(notification.progressToken()).isEqualTo("token-123");
		assertThat(notification.progress()).isEqualTo(0.5);
		assertThat(notification.total()).isEqualTo(1.0);
	}

	@Test
	public void testProgressWithInvalidPercentage() {
		assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> this.context.progress(-1)))
			.hasMessageContaining("Percentage must be between 0 and 100");

		assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> this.context.progress(101)))
			.hasMessageContaining("Percentage must be between 0 and 100");
	}

	@Test
	public void testProgressWithConsumer() {
		CallToolRequest requestWithToken = CallToolRequest.builder()
			.name("test-tool")
			.arguments(Map.of())
			.progressToken("token-123")
			.build();
		McpAsyncRequestContext contextWithToken = DefaultMcpAsyncRequestContext.builder()
			.request(requestWithToken)
			.exchange(this.exchange)
			.build();

		when(this.exchange.progressNotification(any(ProgressNotification.class))).thenReturn(Mono.empty());

		StepVerifier.create(contextWithToken.progress(spec -> {
			spec.progress(0.75);
			spec.total(1.0);
			spec.message("Processing...");
		})).verifyComplete();

		ArgumentCaptor<ProgressNotification> captor = ArgumentCaptor.forClass(ProgressNotification.class);
		verify(this.exchange).progressNotification(captor.capture());

		ProgressNotification notification = captor.getValue();
		assertThat(notification.progressToken()).isEqualTo("token-123");
		assertThat(notification.progress()).isEqualTo(0.75);
		assertThat(notification.total()).isEqualTo(1.0);
		assertThat(notification.message()).isEqualTo("Processing...");
	}

	@Test
	public void testProgressWithNotification() {
		ProgressNotification notification = new ProgressNotification("token-123", 0.5, 1.0, "Test", null);
		when(this.exchange.progressNotification(notification)).thenReturn(Mono.empty());

		StepVerifier.create(this.context.progress(notification)).verifyComplete();

		verify(this.exchange).progressNotification(notification);
	}

	@Test
	public void testProgressWithoutToken() {
		// request already has no progress token (null by default)
		// Should not throw, just log warning and return empty
		StepVerifier.create(this.context.progress(50)).verifyComplete();
	}

	// Ping Tests

	@Test
	public void testPing() {
		when(this.exchange.ping()).thenReturn(Mono.just(new Object()));

		StepVerifier.create(this.context.ping()).expectNextCount(1).verifyComplete();

		verify(this.exchange).ping();
	}

	// Logging Tests

	@Test
	public void testLogWithConsumer() {
		when(this.exchange.loggingNotification(any(LoggingMessageNotification.class))).thenReturn(Mono.empty());

		StepVerifier.create(this.context.log(spec -> {
			spec.message("Test log message");
			spec.level(LoggingLevel.INFO);
			spec.logger("test-logger");
		})).verifyComplete();

		ArgumentCaptor<LoggingMessageNotification> captor = ArgumentCaptor.forClass(LoggingMessageNotification.class);
		verify(this.exchange).loggingNotification(captor.capture());

		LoggingMessageNotification notification = captor.getValue();
		assertThat(notification.data()).isEqualTo("Test log message");
		assertThat(notification.level()).isEqualTo(LoggingLevel.INFO);
		assertThat(notification.logger()).isEqualTo("test-logger");
	}

	@Test
	public void testDebug() {
		when(this.exchange.loggingNotification(any(LoggingMessageNotification.class))).thenReturn(Mono.empty());

		StepVerifier.create(this.context.debug("Debug message")).verifyComplete();

		ArgumentCaptor<LoggingMessageNotification> captor = ArgumentCaptor.forClass(LoggingMessageNotification.class);
		verify(this.exchange).loggingNotification(captor.capture());

		LoggingMessageNotification notification = captor.getValue();
		assertThat(notification.data()).isEqualTo("Debug message");
		assertThat(notification.level()).isEqualTo(LoggingLevel.DEBUG);
	}

	@Test
	public void testInfo() {
		when(this.exchange.loggingNotification(any(LoggingMessageNotification.class))).thenReturn(Mono.empty());

		StepVerifier.create(this.context.info("Info message")).verifyComplete();

		ArgumentCaptor<LoggingMessageNotification> captor = ArgumentCaptor.forClass(LoggingMessageNotification.class);
		verify(this.exchange).loggingNotification(captor.capture());

		LoggingMessageNotification notification = captor.getValue();
		assertThat(notification.data()).isEqualTo("Info message");
		assertThat(notification.level()).isEqualTo(LoggingLevel.INFO);
	}

	@Test
	public void testWarn() {
		when(this.exchange.loggingNotification(any(LoggingMessageNotification.class))).thenReturn(Mono.empty());

		StepVerifier.create(this.context.warn("Warning message")).verifyComplete();

		ArgumentCaptor<LoggingMessageNotification> captor = ArgumentCaptor.forClass(LoggingMessageNotification.class);
		verify(this.exchange).loggingNotification(captor.capture());

		LoggingMessageNotification notification = captor.getValue();
		assertThat(notification.data()).isEqualTo("Warning message");
		assertThat(notification.level()).isEqualTo(LoggingLevel.WARNING);
	}

	@Test
	public void testError() {
		when(this.exchange.loggingNotification(any(LoggingMessageNotification.class))).thenReturn(Mono.empty());

		StepVerifier.create(this.context.error("Error message")).verifyComplete();

		ArgumentCaptor<LoggingMessageNotification> captor = ArgumentCaptor.forClass(LoggingMessageNotification.class);
		verify(this.exchange).loggingNotification(captor.capture());

		LoggingMessageNotification notification = captor.getValue();
		assertThat(notification.data()).isEqualTo("Error message");
		assertThat(notification.level()).isEqualTo(LoggingLevel.ERROR);
	}

	@Test
	public void testLogWithEmptyMessage() {
		assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> this.context.debug("")))
			.hasMessageContaining("Log message must not be empty");
	}

	// Getter Tests

	@Test
	public void testGetRequest() {
		assertThat(this.context.request()).isEqualTo(this.request);
	}

	@Test
	public void testGetExchange() {
		assertThat(this.context.exchange()).isEqualTo(this.exchange);
	}

	@Test
	public void testGetSessionId() {
		when(this.exchange.sessionId()).thenReturn("session-123");

		assertThat(this.context.sessionId()).isEqualTo("session-123");
	}

	@Test
	public void testGetClientInfo() {
		Implementation clientInfo = mock(Implementation.class);
		when(this.exchange.getClientInfo()).thenReturn(clientInfo);

		assertThat(this.context.clientInfo()).isEqualTo(clientInfo);
	}

	@Test
	public void testGetClientCapabilities() {
		ClientCapabilities capabilities = mock(ClientCapabilities.class);
		when(this.exchange.getClientCapabilities()).thenReturn(capabilities);

		assertThat(this.context.clientCapabilities()).isEqualTo(capabilities);
	}

	@Test
	public void testGetRequestMeta() {
		Map<String, Object> meta = Map.of("key", "value");
		CallToolRequest requestWithMeta = CallToolRequest.builder()
			.name("test-tool")
			.arguments(Map.of())
			.meta(meta)
			.build();
		McpAsyncRequestContext contextWithMeta = DefaultMcpAsyncRequestContext.builder()
			.request(requestWithMeta)
			.exchange(this.exchange)
			.build();

		assertThat(contextWithMeta.requestMeta()).isEqualTo(meta);
	}

}
