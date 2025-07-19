/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.tool;

import java.util.List;
import java.util.Map;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.RequiresConsent;
import org.springframework.ai.tool.annotation.RequiresConsent.ConsentLevel;
import org.springframework.ai.tool.consent.ConsentAwareToolCallback;
import org.springframework.ai.tool.consent.ConsentManager;
import org.springframework.ai.tool.consent.exception.ConsentDeniedException;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultToolCallingManager} with consent management.
 *
 * @author Hyunjoon Park
 * @since 1.0.0
 */
class DefaultToolCallingManagerConsentTests {

	private DefaultToolCallingManager toolCallingManager;

	private ConsentManager consentManager;

	private ToolCallback mockToolCallback;

	private ConsentAwareToolCallback consentAwareToolCallback;

	@BeforeEach
	void setUp() {
		ObservationRegistry observationRegistry = ObservationRegistry.create();
		ToolCallbackResolver toolCallbackResolver = Mockito.mock(ToolCallbackResolver.class);
		DefaultToolExecutionExceptionProcessor exceptionProcessor = DefaultToolExecutionExceptionProcessor.builder()
			.build();

		this.toolCallingManager = new DefaultToolCallingManager(observationRegistry, toolCallbackResolver,
				exceptionProcessor);

		// Set up mock tool callback
		this.mockToolCallback = Mockito.mock(ToolCallback.class);
		ToolDefinition toolDefinition = DefaultToolDefinition.builder()
			.name("deleteBook")
			.description("Delete a book")
			.inputSchema("{\"type\":\"object\",\"properties\":{\"bookId\":{\"type\":\"string\"}}}")
			.build();
		ToolMetadata toolMetadata = DefaultToolMetadata.builder().build();
		when(this.mockToolCallback.getToolDefinition()).thenReturn(toolDefinition);
		when(this.mockToolCallback.getToolMetadata()).thenReturn(toolMetadata);

		// Set up consent manager
		this.consentManager = Mockito.mock(ConsentManager.class);

		// Create mock RequiresConsent annotation
		RequiresConsent requiresConsent = Mockito.mock(RequiresConsent.class);
		when(requiresConsent.message()).thenReturn("Delete book {bookId}?");
		when(requiresConsent.level()).thenReturn(ConsentLevel.EVERY_TIME);
		when(requiresConsent.categories()).thenReturn(new String[0]);

		// Create consent-aware wrapper
		this.consentAwareToolCallback = new ConsentAwareToolCallback(this.mockToolCallback, this.consentManager,
				requiresConsent);
	}

	@Test
	void testManualExecutionWithConsentGranted() {
		// Given
		// ConsentAwareToolCallback will first check hasValidConsent, then call
		// requestConsent if needed
		// For this test, we'll make hasValidConsent return false to trigger
		// requestConsent
		when(this.consentManager.hasValidConsent(anyString(), any(ConsentLevel.class), any(String[].class)))
			.thenReturn(false);
		when(this.consentManager.requestConsent(anyString(), anyString(), any(ConsentLevel.class), any(String[].class),
				any(Map.class)))
			.thenReturn(true);
		when(this.mockToolCallback.call(anyString(), any())).thenReturn("Book deleted");

		List<ToolCallback> toolCallbacks = List.of(this.consentAwareToolCallback);
		ToolCallingChatOptions chatOptions = ToolCallingChatOptions.builder().toolCallbacks(toolCallbacks).build();

		UserMessage userMessage = new UserMessage("Delete book with ID 123");
		Prompt prompt = new Prompt(List.of(userMessage), chatOptions);

		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("1", "tool-call", "deleteBook",
				"{\"bookId\":\"123\"}");
		AssistantMessage assistantMessage = new AssistantMessage("I'll delete the book.", Map.of(), List.of(toolCall));

		Generation generation = new Generation(assistantMessage);
		ChatResponse chatResponse = new ChatResponse(List.of(generation));

		// When
		ToolExecutionResult result = this.toolCallingManager.executeToolCalls(prompt, chatResponse);

		// Then
		assertThat(result).isNotNull();
		assertThat(result.conversationHistory()).hasSize(3); // user, assistant, tool
																// response
		// Verify consent was requested
		verify(this.consentManager, times(1)).hasValidConsent(anyString(), any(ConsentLevel.class),
				any(String[].class));
		verify(this.consentManager, times(1)).requestConsent(anyString(), anyString(), any(ConsentLevel.class),
				any(String[].class), any(Map.class));
		verify(this.mockToolCallback, times(1)).call(anyString(), any());
	}

	@Test
	void testManualExecutionWithConsentDenied() {
		// Given
		// ConsentAwareToolCallback will first check hasValidConsent, then call
		// requestConsent if needed
		when(this.consentManager.hasValidConsent(anyString(), any(ConsentLevel.class), any(String[].class)))
			.thenReturn(false);
		when(this.consentManager.requestConsent(anyString(), anyString(), any(ConsentLevel.class), any(String[].class),
				any(Map.class)))
			.thenReturn(false);

		List<ToolCallback> toolCallbacks = List.of(this.consentAwareToolCallback);
		ToolCallingChatOptions chatOptions = ToolCallingChatOptions.builder().toolCallbacks(toolCallbacks).build();

		UserMessage userMessage = new UserMessage("Delete book with ID 123");
		Prompt prompt = new Prompt(List.of(userMessage), chatOptions);

		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("1", "tool-call", "deleteBook",
				"{\"bookId\":\"123\"}");
		AssistantMessage assistantMessage = new AssistantMessage("I'll delete the book.", Map.of(), List.of(toolCall));

		Generation generation = new Generation(assistantMessage);
		ChatResponse chatResponse = new ChatResponse(List.of(generation));

		// When & Then
		assertThatThrownBy(() -> this.toolCallingManager.executeToolCalls(prompt, chatResponse))
			.isInstanceOf(ConsentDeniedException.class)
			.hasMessageContaining("User denied consent for tool");

		// Verify consent was requested but denied
		verify(this.consentManager, times(1)).hasValidConsent(anyString(), any(ConsentLevel.class),
				any(String[].class));
		verify(this.consentManager, times(1)).requestConsent(anyString(), anyString(), any(ConsentLevel.class),
				any(String[].class), any(Map.class));
		verify(this.mockToolCallback, times(0)).call(anyString(), any());
	}

	@Test
	void testManualExecutionWithNonConsentAwareToolCallback() {
		// Given
		when(this.mockToolCallback.call(anyString(), any())).thenReturn("Book deleted");

		List<ToolCallback> toolCallbacks = List.of(this.mockToolCallback); // Regular
																			// callback,
																			// not
																			// consent-aware
		ToolCallingChatOptions chatOptions = ToolCallingChatOptions.builder().toolCallbacks(toolCallbacks).build();

		UserMessage userMessage = new UserMessage("Delete book with ID 123");
		Prompt prompt = new Prompt(List.of(userMessage), chatOptions);

		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("1", "tool-call", "deleteBook",
				"{\"bookId\":\"123\"}");
		AssistantMessage assistantMessage = new AssistantMessage("I'll delete the book.", Map.of(), List.of(toolCall));

		Generation generation = new Generation(assistantMessage);
		ChatResponse chatResponse = new ChatResponse(List.of(generation));

		// When
		ToolExecutionResult result = this.toolCallingManager.executeToolCalls(prompt, chatResponse);

		// Then
		assertThat(result).isNotNull();
		assertThat(result.conversationHistory()).hasSize(3);
		verify(this.mockToolCallback, times(1)).call(anyString(), any());
		// ConsentManager should not be called for non-consent-aware callbacks
	}

	@Test
	void testManualExecutionWithMixedToolCallbacks() {
		// Given
		ToolCallback regularCallback = Mockito.mock(ToolCallback.class);
		ToolDefinition regularToolDef = DefaultToolDefinition.builder()
			.name("getBook")
			.description("Get a book")
			.inputSchema("{\"type\":\"object\",\"properties\":{\"bookId\":{\"type\":\"string\"}}}")
			.build();
		when(regularCallback.getToolDefinition()).thenReturn(regularToolDef);
		when(regularCallback.getToolMetadata()).thenReturn(DefaultToolMetadata.builder().build());
		when(regularCallback.call(anyString(), any())).thenReturn("Book found");

		// For the consent-aware callback
		when(this.consentManager.hasValidConsent(anyString(), any(ConsentLevel.class), any(String[].class)))
			.thenReturn(false);
		when(this.consentManager.requestConsent(anyString(), anyString(), any(ConsentLevel.class), any(String[].class),
				any(Map.class)))
			.thenReturn(true);
		when(this.mockToolCallback.call(anyString(), any())).thenReturn("Book deleted");

		List<ToolCallback> toolCallbacks = List.of(regularCallback, this.consentAwareToolCallback);
		ToolCallingChatOptions chatOptions = ToolCallingChatOptions.builder().toolCallbacks(toolCallbacks).build();

		UserMessage userMessage = new UserMessage("Get and delete book with ID 123");
		Prompt prompt = new Prompt(List.of(userMessage), chatOptions);

		AssistantMessage.ToolCall getCall = new AssistantMessage.ToolCall("1", "tool-call", "getBook",
				"{\"bookId\":\"123\"}");
		AssistantMessage.ToolCall deleteCall = new AssistantMessage.ToolCall("2", "tool-call", "deleteBook",
				"{\"bookId\":\"123\"}");
		AssistantMessage assistantMessage = new AssistantMessage("I'll get and delete the book.", Map.of(),
				List.of(getCall, deleteCall));

		Generation generation = new Generation(assistantMessage);
		ChatResponse chatResponse = new ChatResponse(List.of(generation));

		// When
		ToolExecutionResult result = this.toolCallingManager.executeToolCalls(prompt, chatResponse);

		// Then
		assertThat(result).isNotNull();
		assertThat(result.conversationHistory()).hasSize(3);
		verify(regularCallback, times(1)).call(anyString(), any());
		// Verify consent was requested for the consent-aware callback only
		verify(this.consentManager, times(1)).hasValidConsent(anyString(), any(ConsentLevel.class),
				any(String[].class));
		verify(this.consentManager, times(1)).requestConsent(anyString(), anyString(), any(ConsentLevel.class),
				any(String[].class), any(Map.class));
		verify(this.mockToolCallback, times(1)).call(anyString(), any());
	}

}