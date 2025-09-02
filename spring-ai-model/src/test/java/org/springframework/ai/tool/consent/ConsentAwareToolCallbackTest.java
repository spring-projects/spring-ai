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

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.RequiresConsent;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.ConsentAwareMethodToolCallbackProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for consent-aware tool execution.
 *
 * @author Assistant
 */
class ConsentAwareToolCallbackTest {

	@Test
	void testToolWithConsentGranted() {
		// Create a consent checker that always grants consent
		ConsentChecker alwaysGrantChecker = context -> true;

		ConsentAwareMethodToolCallbackProvider provider = ConsentAwareMethodToolCallbackProvider.builder()
			.toolObjects(new ConsentRequiredTools())
			.consentChecker(alwaysGrantChecker)
			.build();

		ToolCallback[] callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(2);

		// Find the sendEmail tool
		ToolCallback sendEmailTool = findToolByName(callbacks, "sendEmail");
		assertThat(sendEmailTool).isNotNull();

		// Execute should succeed when consent is granted
		String result = sendEmailTool.call("{\"to\":\"test@example.com\",\"subject\":\"Test\",\"body\":\"Hello\"}");
		assertThat(result).isEqualTo("Email sent to test@example.com");
	}

	@Test
	void testToolWithConsentDenied() {
		// Create a consent checker that always denies consent
		ConsentChecker alwaysDenyChecker = context -> false;

		ConsentAwareMethodToolCallbackProvider provider = ConsentAwareMethodToolCallbackProvider.builder()
			.toolObjects(new ConsentRequiredTools())
			.consentChecker(alwaysDenyChecker)
			.build();

		ToolCallback[] callbacks = provider.getToolCallbacks();
		ToolCallback sendEmailTool = findToolByName(callbacks, "sendEmail");

		// Execute should fail when consent is denied
		assertThatThrownBy(
				() -> sendEmailTool.call("{\"to\":\"test@example.com\",\"subject\":\"Test\",\"body\":\"Hello\"}"))
			.isInstanceOf(ConsentRequiredException.class)
			.hasMessageContaining("User consent required for tool: sendEmail");
	}

	@Test
	void testToolWithoutConsentAnnotation() {
		// Tools without @RequiresConsent should work normally
		ConsentChecker neverCalledChecker = context -> {
			throw new AssertionError("Consent checker should not be called for tools without @RequiresConsent");
		};

		ConsentAwareMethodToolCallbackProvider provider = ConsentAwareMethodToolCallbackProvider.builder()
			.toolObjects(new ConsentRequiredTools())
			.consentChecker(neverCalledChecker)
			.build();

		ToolCallback[] callbacks = provider.getToolCallbacks();
		ToolCallback getTimeTool = findToolByName(callbacks, "getCurrentTime");

		// Should execute without consent check
		String result = getTimeTool.call("{}");
		assertThat(result).contains("Current time:");
	}

	@Test
	void testDefaultConsentChecker() {
		// Test with the default consent checker
		DefaultConsentChecker.ConsentRequestHandler handler = context -> {
			// Simulate user interaction - grant consent for sendEmail only
			return "sendEmail".equals(context.getToolName());
		};

		DefaultConsentChecker consentChecker = new DefaultConsentChecker(handler);

		ConsentAwareMethodToolCallbackProvider provider = ConsentAwareMethodToolCallbackProvider.builder()
			.toolObjects(new ConsentRequiredTools())
			.consentChecker(consentChecker)
			.build();

		ToolCallback[] callbacks = provider.getToolCallbacks();
		ToolCallback sendEmailTool = findToolByName(callbacks, "sendEmail");

		// First call should request and grant consent
		String result = sendEmailTool.call("{\"to\":\"test@example.com\",\"subject\":\"Test\",\"body\":\"Hello\"}");
		assertThat(result).isEqualTo("Email sent to test@example.com");

		// Second call should use cached consent (for SESSION level)
		result = sendEmailTool.call("{\"to\":\"another@example.com\",\"subject\":\"Test2\",\"body\":\"Hello again\"}");
		assertThat(result).isEqualTo("Email sent to another@example.com");
	}

	private ToolCallback findToolByName(ToolCallback[] callbacks, String name) {
		for (ToolCallback callback : callbacks) {
			if (callback.getToolDefinition().name().equals(name)) {
				return callback;
			}
		}
		return null;
	}

	/**
	 * Test class with tools that require consent.
	 */
	static class ConsentRequiredTools {

		@Tool(description = "Send an email to a recipient")
		@RequiresConsent(reason = "This tool will send an email on your behalf",
				level = RequiresConsent.ConsentLevel.SESSION, categories = { "communication", "email" })
		public String sendEmail(String to, String subject, String body) {
			return "Email sent to " + to;
		}

		@Tool(description = "Get the current time")
		public String getCurrentTime() {
			return "Current time: " + System.currentTimeMillis();
		}

	}

}
