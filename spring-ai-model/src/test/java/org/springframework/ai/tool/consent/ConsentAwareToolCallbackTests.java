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

package org.springframework.ai.tool.consent;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.RequiresConsent;
import org.springframework.ai.tool.annotation.RequiresConsent.ConsentLevel;
import org.springframework.ai.tool.consent.exception.ConsentDeniedException;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ConsentAwareToolCallback}.
 *
 * @author Hyunjoon Park
 */
class ConsentAwareToolCallbackTests {

	@Mock
	private ToolCallback delegate;

	@Mock
	private ConsentManager consentManager;

	@Mock
	private RequiresConsent requiresConsent;

	private ConsentAwareToolCallback consentAwareCallback;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		when(requiresConsent.message()).thenReturn("Do you approve this action?");
		when(requiresConsent.level()).thenReturn(ConsentLevel.EVERY_TIME);
		when(requiresConsent.categories()).thenReturn(new String[0]);

		consentAwareCallback = new ConsentAwareToolCallback(delegate, consentManager, requiresConsent);
	}

	@Test
	void callWithConsentGranted() {
		// Given
		Map<String, Object> parameters = Map.of("param1", "value1");
		Object expectedResult = "Tool executed successfully";

		when(delegate.getName()).thenReturn("testTool");
		when(consentManager.hasValidConsent("testTool", ConsentLevel.EVERY_TIME, new String[0])).thenReturn(false);
		when(consentManager.requestConsent(eq("testTool"), any(), any(), any(), eq(parameters))).thenReturn(true);
		when(delegate.call(parameters)).thenReturn(expectedResult);

		// When
		Object result = consentAwareCallback.call(parameters);

		// Then
		assertThat(result).isEqualTo(expectedResult);
		verify(delegate).call(parameters);
		verify(consentManager).requestConsent(eq("testTool"), any(), any(), any(), eq(parameters));
	}

	@Test
	void callWithConsentDenied() {
		// Given
		Map<String, Object> parameters = Map.of("param1", "value1");

		when(delegate.getName()).thenReturn("testTool");
		when(consentManager.hasValidConsent("testTool", ConsentLevel.EVERY_TIME, new String[0])).thenReturn(false);
		when(consentManager.requestConsent(eq("testTool"), any(), any(), any(), eq(parameters))).thenReturn(false);

		// When/Then
		assertThatThrownBy(() -> consentAwareCallback.call(parameters)).isInstanceOf(ConsentDeniedException.class)
			.hasMessageContaining("User denied consent for tool 'testTool' execution");

		verify(delegate, never()).call(any());
	}

	@Test
	void callWithExistingValidConsent() {
		// Given
		Map<String, Object> parameters = Map.of("param1", "value1");
		Object expectedResult = "Tool executed successfully";

		when(delegate.getName()).thenReturn("testTool");
		when(consentManager.hasValidConsent("testTool", ConsentLevel.EVERY_TIME, new String[0])).thenReturn(true);
		when(delegate.call(parameters)).thenReturn(expectedResult);

		// When
		Object result = consentAwareCallback.call(parameters);

		// Then
		assertThat(result).isEqualTo(expectedResult);
		verify(delegate).call(parameters);
		verify(consentManager, never()).requestConsent(any(), any(), any(), any(), any());
	}

	@Test
	void messageSubstitutionWithParameters() {
		// Given
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("bookId", "12345");
		parameters.put("title", "Spring in Action");

		when(requiresConsent.message()).thenReturn("Delete book {bookId} titled '{title}'?");
		consentAwareCallback = new ConsentAwareToolCallback(delegate, consentManager, requiresConsent);

		when(delegate.getName()).thenReturn("deleteBook");
		when(consentManager.hasValidConsent("deleteBook", ConsentLevel.EVERY_TIME, new String[0])).thenReturn(false);
		when(consentManager.requestConsent(eq("deleteBook"), eq("Delete book 12345 titled 'Spring in Action'?"), any(),
				any(), eq(parameters)))
			.thenReturn(true);
		when(delegate.call(parameters)).thenReturn("Deleted");

		// When
		Object result = consentAwareCallback.call(parameters);

		// Then
		assertThat(result).isEqualTo("Deleted");
		verify(consentManager).requestConsent(eq("deleteBook"), eq("Delete book 12345 titled 'Spring in Action'?"),
				any(), any(), eq(parameters));
	}

	@Test
	void delegateMethodsAreProxied() {
		// Given
		when(delegate.getName()).thenReturn("testTool");
		when(delegate.getDescription()).thenReturn("Test tool description");
		ToolDefinition toolDef = mock(ToolDefinition.class);
		when(delegate.getToolDefinition()).thenReturn(toolDef);

		// When/Then
		assertThat(consentAwareCallback.getName()).isEqualTo("testTool");
		assertThat(consentAwareCallback.getDescription()).isEqualTo("Test tool description");
		assertThat(consentAwareCallback.getToolDefinition()).isEqualTo(toolDef);
	}

	@Test
	void gettersReturnCorrectValues() {
		// When/Then
		assertThat(consentAwareCallback.getDelegate()).isEqualTo(delegate);
		assertThat(consentAwareCallback.getRequiresConsent()).isEqualTo(requiresConsent);
	}

}
